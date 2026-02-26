package com.gitflow.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gitflow.android.data.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.net.URLEncoder
import timber.log.Timber

class AuthManager(private val context: Context) {

    /** Safe read-only access to application context for callers outside this class. */
    fun getContext(): Context = context.applicationContext

    private val preferences: SharedPreferences = createEncryptedPreferences()
    private val gson = Gson()

    // In-memory map: state -> (code_verifier, createdAt) for PKCE.
    // ConcurrentHashMap: getAuthUrl and handleAuthCallback can run on different coroutines.
    private data class PendingAuth(val codeVerifier: String, val createdAt: Long = System.currentTimeMillis())
    private val pendingAuthStates = java.util.concurrent.ConcurrentHashMap<String, PendingAuth>()

    // Mutexes prevent concurrent refresh races: if two coroutines simultaneously detect
    // an expired token and both try to refresh, the second one would use an invalidated
    // refresh_token and fail. The mutex ensures only one refresh runs at a time;
    // the second coroutine re-checks the token after acquiring the lock and skips refresh.
    private val gitlabRefreshMutex = Mutex()
    private val bitbucketRefreshMutex = Mutex()

    init {
        OAuthConfig.initialize(context)
        migrateFromUnencryptedPrefs()
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "auth_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateFromUnencryptedPrefs() {
        val oldPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) return
        val editor = preferences.edit()
        for ((key, value) in oldPrefs.all) {
            when (value) {
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
        oldPrefs.edit().clear().apply()
    }

    companion object {
        private const val PKCE_STATE_TTL_MS = 10 * 60 * 1000L
        private const val TOKEN_EXPIRY_BUFFER_MS = 5 * 60 * 1000L

        private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val GITHUB_API_URL = "https://api.github.com/"
        private const val GITLAB_AUTH_URL = "https://gitlab.com/oauth/authorize"
        private const val GITLAB_API_URL = "https://gitlab.com/"
        private const val BITBUCKET_AUTH_URL = "https://bitbucket.org/site/oauth2/authorize"
        private const val BITBUCKET_BASE_URL = "https://bitbucket.org/"
        private const val BITBUCKET_API_URL = "https://api.bitbucket.org/"
        private const val AZURE_PROFILE_URL = "https://app.vssps.visualstudio.com/_apis/profile/profiles/me?api-version=7.0"

        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITLAB_TOKEN = "gitlab_token"
        private const val KEY_BITBUCKET_TOKEN = "bitbucket_token"
        private const val KEY_GITEA_TOKEN = "gitea_token"
        private const val KEY_AZURE_DEVOPS_TOKEN = "azure_devops_token"

        private const val KEY_GITHUB_USER = "github_user"
        private const val KEY_GITLAB_USER = "gitlab_user"
        private const val KEY_BITBUCKET_USER = "bitbucket_user"
        private const val KEY_GITEA_USER = "gitea_user"
        private const val KEY_AZURE_DEVOPS_USER = "azure_devops_user"

        // Instance URLs for PAT providers
        private const val KEY_GITEA_INSTANCE_URL = "gitea_instance_url"
        private const val KEY_AZURE_DEVOPS_ORG_URL = "azure_devops_org_url"
    }

    // ---------------------------------------------------------------------------
    // Retrofit API clients
    // ---------------------------------------------------------------------------

    private val githubTokenApi: GitHubApi by lazy {
        buildRetrofit("https://github.com/").create(GitHubApi::class.java)
    }

    private val githubApi: GitHubApi by lazy {
        buildRetrofit(GITHUB_API_URL).create(GitHubApi::class.java)
    }

    private val gitlabApi: GitLabApi by lazy {
        buildRetrofit(GITLAB_API_URL).create(GitLabApi::class.java)
    }

    private val bitbucketTokenApi: BitbucketApi by lazy {
        buildRetrofit(BITBUCKET_BASE_URL).create(BitbucketApi::class.java)
    }

    private val bitbucketApi: BitbucketApi by lazy {
        buildRetrofit(BITBUCKET_API_URL).create(BitbucketApi::class.java)
    }

    // Gitea/Azure use dynamic base URLs — single dummy-base Retrofit, real URL passed via @Url
    private val giteaApi: GiteaApi by lazy {
        buildRetrofit("https://example.com/").create(GiteaApi::class.java)
    }

    private val azureApi: AzureDevOpsApi by lazy {
        buildRetrofit("https://dev.azure.com/").create(AzureDevOpsApi::class.java)
    }

    private val azureProfileApi: AzureDevOpsProfileApi by lazy {
        buildRetrofit("https://app.vssps.visualstudio.com/").create(AzureDevOpsProfileApi::class.java)
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ---------------------------------------------------------------------------
    // OAuth URL generation
    // ---------------------------------------------------------------------------

    /**
     * Returns (authUrl, state) pair for OAuth providers (GitHub, GitLab, Bitbucket).
     * Gitea and Azure DevOps use PAT — call [validateAndSavePAT] instead.
     */
    fun getAuthUrl(provider: GitProvider): Pair<String, String> {
        if (!OAuthConfig.isProviderConfigured(provider)) {
            throw IllegalStateException("OAuth credentials for $provider are not configured in oauth.properties")
        }

        val codeVerifier = PKCEHelper.generateCodeVerifier()
        val codeChallenge = PKCEHelper.generateCodeChallenge(codeVerifier)
        val state = java.util.UUID.randomUUID().toString()

        val now = System.currentTimeMillis()
        pendingAuthStates.entries.removeIf { now - it.value.createdAt > PKCE_STATE_TTL_MS }
        pendingAuthStates[state] = PendingAuth(codeVerifier)

        val redirectEncoded = URLEncoder.encode(OAuthConfig.REDIRECT_URI, "UTF-8")
        val challengeEncoded = URLEncoder.encode(codeChallenge, "UTF-8")
        val stateEncoded = URLEncoder.encode(state, "UTF-8")

        val url = when (provider) {
            GitProvider.GITHUB -> {
                val scope = URLEncoder.encode("repo user", "UTF-8")
                "$GITHUB_AUTH_URL?client_id=${OAuthConfig.githubClientId}" +
                    "&redirect_uri=$redirectEncoded&scope=$scope&response_type=code" +
                    "&code_challenge=$challengeEncoded&code_challenge_method=S256" +
                    "&state=$stateEncoded"
            }
            GitProvider.GITLAB -> {
                val scope = URLEncoder.encode("read_user read_repository write_repository", "UTF-8")
                "$GITLAB_AUTH_URL?client_id=${OAuthConfig.gitlabClientId}" +
                    "&redirect_uri=$redirectEncoded&scope=$scope&response_type=code" +
                    "&code_challenge=$challengeEncoded&code_challenge_method=S256" +
                    "&state=$stateEncoded"
            }
            GitProvider.BITBUCKET -> {
                val scope = URLEncoder.encode("repository account", "UTF-8")
                "$BITBUCKET_AUTH_URL?client_id=${OAuthConfig.bitbucketClientId}" +
                    "&redirect_uri=$redirectEncoded&scope=$scope&response_type=code" +
                    "&code_challenge=$challengeEncoded&code_challenge_method=S256" +
                    "&state=$stateEncoded"
            }
            GitProvider.GITEA, GitProvider.AZURE_DEVOPS ->
                throw UnsupportedOperationException("$provider uses PAT authentication, not OAuth")
        }
        return Pair(url, state)
    }

    // ---------------------------------------------------------------------------
    // OAuth callback handling
    // ---------------------------------------------------------------------------

    suspend fun handleAuthCallback(provider: GitProvider, code: String, state: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val pending = pendingAuthStates.remove(state)
                ?: return@withContext AuthResult(success = false, error = "Invalid or expired OAuth state")

            if (System.currentTimeMillis() - pending.createdAt > PKCE_STATE_TTL_MS) {
                return@withContext AuthResult(success = false, error = "OAuth state expired, please try again")
            }

            when (provider) {
                GitProvider.GITHUB -> handleGitHubCallback(code, pending.codeVerifier)
                GitProvider.GITLAB -> handleGitLabCallback(code, pending.codeVerifier)
                GitProvider.BITBUCKET -> handleBitbucketCallback(code, pending.codeVerifier)
                GitProvider.GITEA, GitProvider.AZURE_DEVOPS ->
                    AuthResult(success = false, error = "$provider uses PAT authentication")
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "Ошибка авторизации")
        }
    }

    private suspend fun handleGitHubCallback(code: String, codeVerifier: String): AuthResult {
        try {
            val tokenResponse = githubTokenApi.getAccessToken(
                mapOf(
                    "client_id" to OAuthConfig.githubClientId,
                    "client_secret" to OAuthConfig.githubClientSecret,
                    "code" to code,
                    "redirect_uri" to OAuthConfig.REDIRECT_URI,
                    "code_verifier" to codeVerifier
                )
            )
            if (!tokenResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить токен: ${tokenResponse.code()}")
            }
            val oauthResponse = tokenResponse.body()
                ?: return AuthResult(success = false, error = "Empty token response")

            val token = OAuthToken(
                accessToken = oauthResponse.access_token,
                tokenType = oauthResponse.token_type,
                scope = oauthResponse.scope
            )

            val userResponse = githubApi.getCurrentUser("Bearer ${token.accessToken}")
            if (!userResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе: ${userResponse.code()}")
            }
            val githubUser = userResponse.body()
                ?: return AuthResult(success = false, error = "Empty user response")

            // Try to get primary email if not returned by /user
            val email = githubUser.email ?: fetchGitHubPrimaryEmail(token.accessToken)

            val user = GitUser(
                id = githubUser.id,
                login = githubUser.login,
                name = githubUser.name,
                email = email,
                avatarUrl = githubUser.avatar_url,
                provider = GitProvider.GITHUB
            )
            saveToken(GitProvider.GITHUB, token)
            saveUser(GitProvider.GITHUB, user)
            return AuthResult(success = true, user = user, token = token)
        } catch (e: Exception) {
            return AuthResult(success = false, error = e.message ?: "Ошибка авторизации GitHub")
        }
    }

    private suspend fun fetchGitHubPrimaryEmail(accessToken: String): String? {
        return try {
            val response = githubApi.getUserEmails("Bearer $accessToken")
            if (response.isSuccessful) {
                response.body()?.firstOrNull { it.primary }?.email
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun fetchBitbucketPrimaryEmail(accessToken: String): String? {
        return try {
            val response = bitbucketApi.getUserEmails("Bearer $accessToken")
            if (response.isSuccessful) {
                response.body()?.values?.firstOrNull { it.is_primary && it.is_confirmed }?.email
                    ?: response.body()?.values?.firstOrNull { it.is_primary }?.email
            } else null
        } catch (_: Exception) { null }
    }

    private suspend fun handleGitLabCallback(code: String, codeVerifier: String): AuthResult {
        try {
            val tokenResponse = gitlabApi.getAccessToken(
                clientId = OAuthConfig.gitlabClientId,
                clientSecret = OAuthConfig.gitlabClientSecret,
                code = code,
                redirectUri = OAuthConfig.REDIRECT_URI,
                codeVerifier = codeVerifier
            )
            if (!tokenResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить токен")
            }
            val oauthResponse = tokenResponse.body()
                ?: return AuthResult(success = false, error = "Empty token response")
            val token = OAuthToken(
                accessToken = oauthResponse.access_token,
                tokenType = oauthResponse.token_type,
                scope = oauthResponse.scope,
                refreshToken = oauthResponse.refresh_token,
                expiresAt = oauthResponse.expires_in?.let { System.currentTimeMillis() + (it * 1000L) }
            )
            val userResponse = gitlabApi.getCurrentUser("Bearer ${token.accessToken}")
            if (!userResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе")
            }
            val gitlabUser = userResponse.body()
                ?: return AuthResult(success = false, error = "Empty user response")
            val user = GitUser(
                id = gitlabUser.id,
                login = gitlabUser.username,
                name = gitlabUser.name,
                email = gitlabUser.email,
                avatarUrl = gitlabUser.avatar_url,
                provider = GitProvider.GITLAB
            )
            saveToken(GitProvider.GITLAB, token)
            saveUser(GitProvider.GITLAB, user)
            return AuthResult(success = true, user = user, token = token)
        } catch (e: Exception) {
            return AuthResult(success = false, error = e.message ?: "Ошибка авторизации GitLab")
        }
    }

    private suspend fun handleBitbucketCallback(code: String, codeVerifier: String): AuthResult {
        try {
            val basicAuth = "Basic " + Base64.encodeToString(
                "${OAuthConfig.bitbucketClientId}:${OAuthConfig.bitbucketClientSecret}".toByteArray(),
                Base64.NO_WRAP
            )
            val tokenResponse = bitbucketTokenApi.getAccessToken(
                basicAuth = basicAuth,
                code = code,
                redirectUri = OAuthConfig.REDIRECT_URI,
                codeVerifier = codeVerifier
            )
            if (!tokenResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить токен Bitbucket: ${tokenResponse.code()}")
            }
            val oauthResponse = tokenResponse.body()
                ?: return AuthResult(success = false, error = "Empty token response")
            val token = OAuthToken(
                accessToken = oauthResponse.access_token,
                tokenType = oauthResponse.token_type,
                refreshToken = oauthResponse.refresh_token,
                expiresAt = oauthResponse.expires_in?.let { System.currentTimeMillis() + (it * 1000L) }
            )
            val userResponse = bitbucketApi.getCurrentUser("Bearer ${token.accessToken}")
            if (!userResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе Bitbucket")
            }
            val bbUser = userResponse.body()
                ?: return AuthResult(success = false, error = "Empty user response")
            val email = fetchBitbucketPrimaryEmail(token.accessToken)
            val user = GitUser(
                id = bbUser.account_id.hashCode().toLong(),
                login = bbUser.username ?: bbUser.display_name,
                name = bbUser.display_name,
                email = email,
                avatarUrl = bbUser.links?.avatar?.href,
                provider = GitProvider.BITBUCKET
            )
            saveToken(GitProvider.BITBUCKET, token)
            saveUser(GitProvider.BITBUCKET, user)
            return AuthResult(success = true, user = user, token = token)
        } catch (e: Exception) {
            return AuthResult(success = false, error = e.message ?: "Ошибка авторизации Bitbucket")
        }
    }

    // ---------------------------------------------------------------------------
    // PAT authentication (Gitea, Azure DevOps)
    // ---------------------------------------------------------------------------

    /**
     * Validates a PAT by calling the provider's user API, then stores it.
     * Returns AuthResult with the user info on success.
     */
    suspend fun validateAndSavePAT(
        provider: GitProvider,
        instanceUrl: String,
        username: String,
        pat: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                GitProvider.GITEA -> validateGiteaPAT(instanceUrl.trimEnd('/'), username, pat)
                GitProvider.AZURE_DEVOPS -> validateAzurePAT(instanceUrl.trimEnd('/'), username, pat)
                else -> AuthResult(success = false, error = "$provider не поддерживает PAT-авторизацию через этот метод")
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "Ошибка проверки токена")
        }
    }

    private suspend fun validateGiteaPAT(instanceUrl: String, username: String, pat: String): AuthResult {
        val userUrl = "$instanceUrl/api/v1/user"
        val authHeader = "token $pat"
        val response = giteaApi.getCurrentUser(url = userUrl, authorization = authHeader)
        if (!response.isSuccessful) {
            return AuthResult(success = false, error = "Не удалось подключиться к Gitea: ${response.code()}")
        }
        val giteaUser = response.body()
            ?: return AuthResult(success = false, error = "Пустой ответ от сервера")
        val user = GitUser(
            id = giteaUser.id,
            login = giteaUser.login,
            name = giteaUser.full_name?.takeIf { it.isNotBlank() } ?: giteaUser.login,
            email = giteaUser.email,
            avatarUrl = giteaUser.avatar_url,
            provider = GitProvider.GITEA
        )
        val token = OAuthToken(accessToken = pat)
        saveToken(GitProvider.GITEA, token)
        saveUser(GitProvider.GITEA, user)
        preferences.edit().putString(KEY_GITEA_INSTANCE_URL, instanceUrl).apply()
        return AuthResult(success = true, user = user, token = token)
    }

    private suspend fun validateAzurePAT(orgUrl: String, username: String, pat: String): AuthResult {
        val basicAuth = "Basic " + Base64.encodeToString(":$pat".toByteArray(), Base64.NO_WRAP)
        val response = azureProfileApi.getProfile(url = AZURE_PROFILE_URL, authorization = basicAuth)
        if (!response.isSuccessful) {
            return AuthResult(success = false, error = "Не удалось подключиться к Azure DevOps: ${response.code()}")
        }
        val profile = response.body()
            ?: return AuthResult(success = false, error = "Пустой ответ от сервера")
        val user = GitUser(
            id = profile.id.hashCode().toLong(),
            login = profile.publicAlias ?: username,
            name = profile.displayName,
            email = profile.emailAddress,
            avatarUrl = null,
            provider = GitProvider.AZURE_DEVOPS
        )
        val token = OAuthToken(accessToken = pat)
        saveToken(GitProvider.AZURE_DEVOPS, token)
        saveUser(GitProvider.AZURE_DEVOPS, user)
        preferences.edit().putString(KEY_AZURE_DEVOPS_ORG_URL, orgUrl).apply()
        return AuthResult(success = true, user = user, token = token)
    }

    // ---------------------------------------------------------------------------
    // Token refresh
    // ---------------------------------------------------------------------------

    // Note: these functions do NOT wrap withContext(Dispatchers.IO) — they are always
    // called from within getRepositories which is already on Dispatchers.IO.
    // resolveCredentialsProvider (GitRepository) calls them from a suspend context too.
    //
    // The Mutex prevents concurrent refresh races: if two coroutines simultaneously detect
    // an expired token, the first one refreshes and saves a new token+refreshToken; the
    // second coroutine acquires the lock after the first finishes, re-reads the token,
    // finds it no longer expired, and returns true without a redundant network call.
    internal suspend fun refreshGitLabTokenIfNeeded(): Boolean = gitlabRefreshMutex.withLock {
        val token = getToken(GitProvider.GITLAB) ?: return@withLock false
        if (!isTokenExpired(token)) return@withLock true   // already refreshed by another coroutine
        val refreshToken = token.refreshToken ?: return@withLock false
        try {
            val response = gitlabApi.refreshToken(
                clientId = OAuthConfig.gitlabClientId,
                clientSecret = OAuthConfig.gitlabClientSecret,
                refreshToken = refreshToken,
                redirectUri = OAuthConfig.REDIRECT_URI
            )
            if (!response.isSuccessful) return@withLock false
            val body = response.body() ?: return@withLock false
            saveToken(GitProvider.GITLAB, OAuthToken(
                accessToken = body.access_token,
                tokenType = body.token_type,
                scope = body.scope,
                refreshToken = body.refresh_token ?: refreshToken,
                expiresAt = body.expires_in?.let { System.currentTimeMillis() + it * 1000L }
            ))
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh GitLab token")
            false
        }
    }

    internal suspend fun refreshBitbucketTokenIfNeeded(): Boolean = bitbucketRefreshMutex.withLock {
        val token = getToken(GitProvider.BITBUCKET) ?: return@withLock false
        if (!isTokenExpired(token)) return@withLock true   // already refreshed by another coroutine
        val refreshToken = token.refreshToken ?: return@withLock false
        try {
            val basicAuth = "Basic " + Base64.encodeToString(
                "${OAuthConfig.bitbucketClientId}:${OAuthConfig.bitbucketClientSecret}".toByteArray(),
                Base64.NO_WRAP
            )
            val response = bitbucketTokenApi.refreshToken(basicAuth = basicAuth, refreshToken = refreshToken)
            if (!response.isSuccessful) return@withLock false
            val body = response.body() ?: return@withLock false
            saveToken(GitProvider.BITBUCKET, OAuthToken(
                accessToken = body.access_token,
                tokenType = body.token_type,
                refreshToken = body.refresh_token ?: refreshToken,
                expiresAt = body.expires_in?.let { System.currentTimeMillis() + it * 1000L }
            ))
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh Bitbucket token")
            false
        }
    }

    // ---------------------------------------------------------------------------
    // Repository listing
    // ---------------------------------------------------------------------------

    suspend fun getRepositories(provider: GitProvider): List<GitRemoteRepository> = withContext(Dispatchers.IO) {
        when (provider) {
            GitProvider.GITLAB -> {
                if (!refreshGitLabTokenIfNeeded()) {
                    throw IllegalStateException("Сессия GitLab истекла. Пожалуйста, войдите снова в Настройки → Управление аккаунтами.")
                }
                val token = getToken(provider) ?: throw IllegalStateException("Токен не найден для $provider")
                getGitLabRepositories("Bearer ${token.accessToken}")
            }
            GitProvider.BITBUCKET -> {
                if (!refreshBitbucketTokenIfNeeded()) {
                    throw IllegalStateException("Сессия Bitbucket истекла. Пожалуйста, войдите снова в Настройки → Управление аккаунтами.")
                }
                val token = getToken(provider) ?: throw IllegalStateException("Токен не найден для $provider")
                getBitbucketRepositories("Bearer ${token.accessToken}")
            }
            GitProvider.GITEA -> {
                val token = getToken(provider) ?: throw IllegalStateException("Токен не найден для $provider")
                val instanceUrl = preferences.getString(KEY_GITEA_INSTANCE_URL, null)
                    ?: throw IllegalStateException("Instance URL не найден для Gitea")
                getGiteaRepositories("token ${token.accessToken}", instanceUrl)
            }
            GitProvider.AZURE_DEVOPS -> {
                val token = getToken(provider) ?: throw IllegalStateException("Токен не найден для $provider")
                val orgUrl = preferences.getString(KEY_AZURE_DEVOPS_ORG_URL, null)
                    ?: throw IllegalStateException("Organization URL не найден для Azure DevOps")
                val basicAuth = "Basic " + Base64.encodeToString(":${token.accessToken}".toByteArray(), Base64.NO_WRAP)
                getAzureRepositories(basicAuth, orgUrl)
            }
            GitProvider.GITHUB -> {
                val token = getToken(provider) ?: throw IllegalStateException("Токен не найден для $provider")
                getGitHubRepositories("Bearer ${token.accessToken}")
            }
        }
    }

    private suspend fun getGitHubRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        // Fetch up to 2 pages of user repos
        for (page in 1..2) {
            val response = githubApi.getUserRepositories(authHeader, page = page)
            if (response.isSuccessful) {
                val body = response.body() ?: break
                body.forEach { repositories.add(it.toGitRemoteRepository()) }
                if (body.size < 100) break
            } else {
                if (page == 1) throw IllegalStateException("Ошибка получения репозиториев GitHub: ${response.code()}")
                break
            }
        }
        // Org repos (paginated — up to 3 pages per org to handle large organizations)
        try {
            val orgsResponse = githubApi.getUserOrganizations(authHeader)
            if (orgsResponse.isSuccessful) {
                orgsResponse.body()?.forEach { org ->
                    try {
                        for (page in 1..3) {
                            val orgRepos = githubApi.getOrganizationRepositories(
                                authHeader, org.login, page = page
                            )
                            if (orgRepos.isSuccessful) {
                                val body = orgRepos.body() ?: break
                                body.forEach { repositories.add(it.toGitRemoteRepository()) }
                                if (body.size < 100) break
                            } else break
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch repos for org ${org.login}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch GitHub organizations")
        }
        return repositories.distinctBy { it.id }
    }

    private suspend fun getGitLabRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        // User projects
        val projectsResponse = gitlabApi.getUserProjects(authHeader)
        if (projectsResponse.isSuccessful) {
            projectsResponse.body()?.forEach { repositories.add(it.toGitRemoteRepository()) }
        } else {
            throw IllegalStateException("Ошибка получения проектов GitLab: ${projectsResponse.code()}")
        }
        // Group projects
        try {
            val groupsResponse = gitlabApi.getUserGroups(authHeader)
            if (groupsResponse.isSuccessful) {
                groupsResponse.body()?.forEach { group ->
                    try {
                        val groupRepos = gitlabApi.getGroupProjects(authHeader, group.id)
                        if (groupRepos.isSuccessful) {
                            groupRepos.body()?.forEach { repositories.add(it.toGitRemoteRepository()) }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch repos for group ${group.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch GitLab groups")
        }
        return repositories.distinctBy { it.id }
    }

    private suspend fun getBitbucketRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        val workspacesResponse = bitbucketApi.getWorkspaces(authHeader)
        if (!workspacesResponse.isSuccessful) {
            throw IllegalStateException("Ошибка получения воркспейсов Bitbucket: ${workspacesResponse.code()}")
        }
        val workspaces = workspacesResponse.body()?.values ?: emptyList()
        if (workspaces.isEmpty()) {
            // Fallback: try to get repos directly using the user's workspace
            val user = getCurrentUser(GitProvider.BITBUCKET)
            if (user != null) {
                val reposResponse = bitbucketApi.getRepositories(authHeader, user.login)
                if (reposResponse.isSuccessful) {
                    reposResponse.body()?.values?.forEach { repo ->
                        if (repo.scm == "git") repositories.add(repo.toGitRemoteRepository())
                    }
                }
            }
        } else {
            workspaces.forEach { ws ->
                try {
                    val reposResponse = bitbucketApi.getRepositories(authHeader, ws.workspace.slug)
                    if (reposResponse.isSuccessful) {
                        reposResponse.body()?.values?.forEach { repo ->
                            if (repo.scm == "git") repositories.add(repo.toGitRemoteRepository())
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to fetch repos for workspace ${ws.workspace.slug}")
                }
            }
        }
        return repositories.distinctBy { it.id }
    }

    private suspend fun getGiteaRepositories(authHeader: String, instanceUrl: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        val searchUrl = "$instanceUrl/api/v1/repos/search"
        for (page in 1..4) {
            val response = giteaApi.searchRepositories(url = searchUrl, authorization = authHeader, page = page)
            if (response.isSuccessful) {
                val data = response.body()?.data ?: break
                data.forEach { repositories.add(it.toGitRemoteRepository(instanceUrl)) }
                if (data.size < 50) break
            } else {
                if (page == 1) throw IllegalStateException("Ошибка получения репозиториев Gitea: ${response.code()}")
                break
            }
        }
        return repositories
    }

    private suspend fun getAzureRepositories(basicAuth: String, orgUrl: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        val projectsUrl = "$orgUrl/_apis/projects?api-version=7.0"
        val projectsResponse = azureApi.getProjects(url = projectsUrl, authorization = basicAuth)
        if (!projectsResponse.isSuccessful) {
            throw IllegalStateException("Ошибка получения проектов Azure DevOps: ${projectsResponse.code()}")
        }
        val projects = projectsResponse.body()?.value ?: emptyList()
        val user = getCurrentUser(GitProvider.AZURE_DEVOPS)
        projects.forEach { project ->
            try {
                val reposUrl = "$orgUrl/${project.name}/_apis/git/repositories?api-version=7.0"
                val reposResponse = azureApi.getRepositories(url = reposUrl, authorization = basicAuth)
                if (reposResponse.isSuccessful) {
                    reposResponse.body()?.value?.forEach { repo ->
                        repositories.add(repo.toGitRemoteRepository(user, orgUrl))
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch repos for Azure project ${project.name}")
            }
        }
        return repositories
    }

    // ---------------------------------------------------------------------------
    // Auth state queries
    // ---------------------------------------------------------------------------

    fun isAuthenticated(provider: GitProvider): Boolean {
        val token = getToken(provider) ?: return false
        // If the token has an expiry and it has passed, treat as not authenticated.
        // isTokenExpired returns false when expiresAt == null (GitHub/Gitea/Azure PATs).
        return !isTokenExpired(token)
    }

    fun getCurrentUser(provider: GitProvider): GitUser? {
        val key = userKey(provider)
        val userJson = preferences.getString(key, null)
        return userJson?.let { gson.fromJson(it, GitUser::class.java) }
    }

    fun getAccessToken(provider: GitProvider): String? = getToken(provider)?.accessToken

    fun getInstanceUrl(provider: GitProvider): String? = when (provider) {
        GitProvider.GITEA -> preferences.getString(KEY_GITEA_INSTANCE_URL, null)
        GitProvider.AZURE_DEVOPS -> preferences.getString(KEY_AZURE_DEVOPS_ORG_URL, null)
        else -> null
    }

    fun logout(provider: GitProvider) {
        val editor = preferences.edit()
        editor.remove(tokenKey(provider))
        editor.remove(userKey(provider))
        when (provider) {
            GitProvider.GITEA -> editor.remove(KEY_GITEA_INSTANCE_URL)
            GitProvider.AZURE_DEVOPS -> editor.remove(KEY_AZURE_DEVOPS_ORG_URL)
            else -> {}
        }
        editor.apply()
    }

    // ---------------------------------------------------------------------------
    // Clone URL
    // ---------------------------------------------------------------------------

    fun getCloneUrl(repository: GitRemoteRepository): String? {
        if (!isAuthenticated(repository.provider)) return null
        return repository.cloneUrl
    }

    // ---------------------------------------------------------------------------
    // Approximate repository size
    // ---------------------------------------------------------------------------

    suspend fun getRepositoryApproximateSize(rawUrl: String): Long? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeRepositoryUrlForParsing(rawUrl)
        if (normalizedUrl.isBlank()) return@withContext null
        val uri = try { URI(normalizedUrl) } catch (e: Exception) { return@withContext null }
        val host = uri.host ?: return@withContext null
        val path = uri.path?.trim('/') ?: return@withContext null
        if (path.isBlank()) return@withContext null

        try {
            when {
                host.contains("github.com", ignoreCase = true) -> fetchGitHubRepositorySize(path)
                host.contains("gitlab.com", ignoreCase = true) -> fetchGitLabRepositorySize(path)
                host.contains("bitbucket.org", ignoreCase = true) -> null // no public size API
                else -> null
            }
        } catch (e: Exception) { null }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun isTokenExpired(token: OAuthToken): Boolean {
        val expiresAt = token.expiresAt ?: return false
        return System.currentTimeMillis() >= expiresAt - TOKEN_EXPIRY_BUFFER_MS
    }

    private fun saveToken(provider: GitProvider, token: OAuthToken) {
        preferences.edit().putString(tokenKey(provider), gson.toJson(token)).apply()
    }

    private fun saveUser(provider: GitProvider, user: GitUser) {
        preferences.edit().putString(userKey(provider), gson.toJson(user)).apply()
    }

    private fun getToken(provider: GitProvider): OAuthToken? {
        val tokenJson = preferences.getString(tokenKey(provider), null)
        return tokenJson?.let { gson.fromJson(it, OAuthToken::class.java) }
    }

    private fun tokenKey(provider: GitProvider): String = when (provider) {
        GitProvider.GITHUB -> KEY_GITHUB_TOKEN
        GitProvider.GITLAB -> KEY_GITLAB_TOKEN
        GitProvider.BITBUCKET -> KEY_BITBUCKET_TOKEN
        GitProvider.GITEA -> KEY_GITEA_TOKEN
        GitProvider.AZURE_DEVOPS -> KEY_AZURE_DEVOPS_TOKEN
    }

    private fun userKey(provider: GitProvider): String = when (provider) {
        GitProvider.GITHUB -> KEY_GITHUB_USER
        GitProvider.GITLAB -> KEY_GITLAB_USER
        GitProvider.BITBUCKET -> KEY_BITBUCKET_USER
        GitProvider.GITEA -> KEY_GITEA_USER
        GitProvider.AZURE_DEVOPS -> KEY_AZURE_DEVOPS_USER
    }

    private fun normalizeRepositoryUrlForParsing(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("git@") -> {
                val withoutPrefix = trimmed.removePrefix("git@")
                val parts = withoutPrefix.split(":", limit = 2)
                if (parts.size == 2) "https://${parts[0]}/${parts[1]}" else "https://$withoutPrefix"
            }
            trimmed.startsWith("ssh://git@") -> {
                val withoutPrefix = trimmed.removePrefix("ssh://git@")
                val parts = withoutPrefix.split(":", limit = 2)
                if (parts.size == 2) "https://${parts[0]}/${parts[1]}" else "https://$withoutPrefix"
            }
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
            else -> trimmed
        }
    }

    private suspend fun fetchGitHubRepositorySize(path: String): Long? {
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val owner = segments[0]
        val repo = segments[1].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null
        val authHeader = getAccessToken(GitProvider.GITHUB)?.let { "Bearer $it" }
        val response = githubApi.getRepository(owner = owner, repo = repo, authorization = authHeader)
        val body = when {
            response.isSuccessful -> response.body()
            authHeader != null -> {
                val fallback = githubApi.getRepository(owner = owner, repo = repo, authorization = null)
                if (fallback.isSuccessful) fallback.body() else null
            }
            else -> null
        }
        return body?.size?.toLong()?.let { it * 1024L }
    }

    private suspend fun fetchGitLabRepositorySize(path: String): Long? {
        val segments = path.split('/').filter { it.isNotBlank() }.toMutableList()
        if (segments.isEmpty()) return null
        segments[segments.lastIndex] = segments[segments.lastIndex].removeSuffix(".git")
        val projectPath = segments.joinToString("/")
        if (projectPath.isBlank()) return null
        val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
        val authHeader = getAccessToken(GitProvider.GITLAB)?.let { "Bearer $it" }
        val response = gitlabApi.getProject(authorization = authHeader, projectId = encodedPath, statistics = true)
        val body = when {
            response.isSuccessful -> response.body()
            authHeader != null -> {
                val fallback = gitlabApi.getProject(authorization = null, projectId = encodedPath, statistics = true)
                if (fallback.isSuccessful) fallback.body() else null
            }
            else -> null
        }
        return body?.statistics?.repository_size ?: body?.statistics?.storage_size
    }
}

// ---------------------------------------------------------------------------
// Extension converters
// ---------------------------------------------------------------------------

private fun GitHubRepository.toGitRemoteRepository(): GitRemoteRepository = GitRemoteRepository(
    id = this.id,
    name = this.name,
    fullName = this.full_name,
    description = this.description,
    private = this.private,
    cloneUrl = this.clone_url,
    sshUrl = this.ssh_url,
    htmlUrl = this.html_url,
    defaultBranch = this.default_branch,
    owner = GitUser(
        id = this.owner.id,
        login = this.owner.login,
        name = this.owner.name,
        email = this.owner.email,
        avatarUrl = this.owner.avatar_url,
        provider = GitProvider.GITHUB
    ),
    provider = GitProvider.GITHUB,
    updatedAt = this.updated_at,
    approximateSizeBytes = this.size?.let { it.toLong() * 1024L }
)

private fun GitLabRepository.toGitRemoteRepository(): GitRemoteRepository = GitRemoteRepository(
    id = this.id,
    name = this.name,
    fullName = this.name_with_namespace,
    description = this.description,
    private = this.visibility == "private",
    cloneUrl = this.http_url_to_repo,
    sshUrl = this.ssh_url_to_repo,
    htmlUrl = this.web_url,
    defaultBranch = this.default_branch ?: "main",
    owner = GitUser(
        id = this.namespace.id,
        login = this.namespace.path,
        name = this.namespace.name,
        email = null,
        avatarUrl = this.namespace.avatar_url,
        provider = GitProvider.GITLAB
    ),
    provider = GitProvider.GITLAB,
    updatedAt = this.last_activity_at,
    approximateSizeBytes = this.statistics?.repository_size ?: this.statistics?.storage_size
)

private fun BitbucketRepository.toGitRemoteRepository(): GitRemoteRepository {
    val httpsClone = this.links?.clone?.firstOrNull { it.name == "https" }?.href ?: ""
    val sshClone = this.links?.clone?.firstOrNull { it.name == "ssh" }?.href ?: ""
    val webUrl = this.links?.html?.href ?: ""
    return GitRemoteRepository(
        id = this.uuid.hashCode().toLong(),
        name = this.name,
        fullName = this.full_name,
        description = this.description,
        private = this.is_private,
        cloneUrl = httpsClone,
        sshUrl = sshClone,
        htmlUrl = webUrl,
        defaultBranch = this.mainbranch?.name ?: "main",
        owner = GitUser(
            id = (this.owner?.account_id ?: "").hashCode().toLong(),
            login = this.owner?.username ?: this.owner?.display_name ?: "",
            name = this.owner?.display_name,
            email = null,
            avatarUrl = this.owner?.links?.avatar?.href,
            provider = GitProvider.BITBUCKET
        ),
        provider = GitProvider.BITBUCKET,
        updatedAt = this.updated_on,
        approximateSizeBytes = this.size
    )
}

private fun GiteaRepository.toGitRemoteRepository(instanceUrl: String): GitRemoteRepository = GitRemoteRepository(
    id = this.id,
    name = this.name,
    fullName = this.full_name,
    description = this.description,
    private = this.private,
    cloneUrl = this.clone_url,
    sshUrl = this.ssh_url,
    htmlUrl = this.html_url,
    defaultBranch = this.default_branch,
    owner = GitUser(
        id = this.owner.id,
        login = this.owner.login,
        name = this.owner.full_name?.takeIf { it.isNotBlank() } ?: this.owner.login,
        email = this.owner.email,
        avatarUrl = this.owner.avatar_url,
        provider = GitProvider.GITEA
    ),
    provider = GitProvider.GITEA,
    updatedAt = this.updated_at,
    approximateSizeBytes = this.size?.let { it * 1024L }
)

private fun AzureRepository.toGitRemoteRepository(currentUser: GitUser?, orgUrl: String): GitRemoteRepository {
    val webUrl = this.webUrl ?: "$orgUrl/${this.project.name}/_git/${this.name}"
    return GitRemoteRepository(
        id = this.id.hashCode().toLong(),
        name = this.name,
        fullName = "${this.project.name}/${this.name}",
        description = null,
        private = true,
        cloneUrl = this.remoteUrl ?: "",
        sshUrl = this.sshUrl ?: "",
        htmlUrl = webUrl,
        defaultBranch = this.defaultBranch?.removePrefix("refs/heads/") ?: "main",
        owner = currentUser ?: GitUser(
            id = 0,
            login = this.project.name,
            name = this.project.name,
            email = null,
            avatarUrl = null,
            provider = GitProvider.AZURE_DEVOPS
        ),
        provider = GitProvider.AZURE_DEVOPS,
        updatedAt = "",
        approximateSizeBytes = this.size
    )
}
