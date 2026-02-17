package com.gitflow.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gitflow.android.data.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.net.URLEncoder

class AuthManager(val context: Context) {

    private val preferences: SharedPreferences = createEncryptedPreferences()
    private val gson = Gson()

    // In-memory map: state -> code_verifier (for PKCE)
    private val pendingAuthStates = mutableMapOf<String, String>()

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

        private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val GITHUB_API_URL = "https://api.github.com/"
        private const val GITLAB_AUTH_URL = "https://gitlab.com/oauth/authorize"
        private const val GITLAB_API_URL = "https://gitlab.com/"

        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITLAB_TOKEN = "gitlab_token"
        private const val KEY_GITHUB_USER = "github_user"
        private const val KEY_GITLAB_USER = "gitlab_user"
    }

    private val githubTokenApi: GitHubApi by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://github.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    private val githubApi: GitHubApi by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(GITHUB_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    private val gitlabApi: GitLabApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITLAB_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitLabApi::class.java)
    }

    /**
     * Returns (authUrl, state) pair. The state must be passed to OAuthActivity
     * and validated on callback.
     */
    fun getAuthUrl(provider: GitProvider): Pair<String, String> {
        if (!OAuthConfig.isConfigured()) {
            throw IllegalStateException("OAuth конфигурация не загружена! Проверьте наличие oauth.properties файла или переменных окружения")
        }

        val codeVerifier = PKCEHelper.generateCodeVerifier()
        val codeChallenge = PKCEHelper.generateCodeChallenge(codeVerifier)
        val state = java.util.UUID.randomUUID().toString()

        pendingAuthStates[state] = codeVerifier

        val url = when (provider) {
            GitProvider.GITHUB -> {
                val scope = URLEncoder.encode("repo user", "UTF-8")
                "$GITHUB_AUTH_URL?client_id=${OAuthConfig.githubClientId}" +
                    "&redirect_uri=${URLEncoder.encode(OAuthConfig.REDIRECT_URI, "UTF-8")}" +
                    "&scope=$scope&response_type=code" +
                    "&code_challenge=${URLEncoder.encode(codeChallenge, "UTF-8")}" +
                    "&code_challenge_method=S256" +
                    "&state=${URLEncoder.encode(state, "UTF-8")}"
            }
            GitProvider.GITLAB -> {
                val scope = URLEncoder.encode("read_user read_repository write_repository", "UTF-8")
                "$GITLAB_AUTH_URL?client_id=${OAuthConfig.gitlabClientId}" +
                    "&redirect_uri=${URLEncoder.encode(OAuthConfig.REDIRECT_URI, "UTF-8")}" +
                    "&scope=$scope&response_type=code" +
                    "&code_challenge=${URLEncoder.encode(codeChallenge, "UTF-8")}" +
                    "&code_challenge_method=S256" +
                    "&state=${URLEncoder.encode(state, "UTF-8")}"
            }
        }
        return Pair(url, state)
    }

    suspend fun handleAuthCallback(provider: GitProvider, code: String, state: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val codeVerifier = pendingAuthStates.remove(state)
                ?: return@withContext AuthResult(success = false, error = "Invalid or expired OAuth state")

            when (provider) {
                GitProvider.GITHUB -> handleGitHubCallback(code, codeVerifier)
                GitProvider.GITLAB -> handleGitLabCallback(code, codeVerifier)
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "Ошибка авторизации")
        }
    }

    private suspend fun handleGitHubCallback(code: String, codeVerifier: String): AuthResult {
        try {
            val tokenRequest = mapOf(
                "client_id" to OAuthConfig.githubClientId,
                "client_secret" to OAuthConfig.githubClientSecret,
                "code" to code,
                "redirect_uri" to OAuthConfig.REDIRECT_URI,
                "code_verifier" to codeVerifier
            )

            val tokenResponse = githubTokenApi.getAccessToken(tokenRequest)

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

            val userResponse = try {
                githubApi.getCurrentUser("Bearer ${token.accessToken}")
            } catch (e: Exception) {
                return AuthResult(success = false, error = "Ошибка сети: ${e.message}")
            }

            if (!userResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе: ${userResponse.code()}")
            }

            val githubUser = userResponse.body()
                ?: return AuthResult(success = false, error = "Empty user response")
            val user = GitUser(
                id = githubUser.id,
                login = githubUser.login,
                name = githubUser.name,
                email = githubUser.email,
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
                expiresAt = oauthResponse.expires_in?.let { System.currentTimeMillis() + (it * 1000) }
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

    suspend fun getRepositories(provider: GitProvider): List<GitRemoteRepository> = withContext(Dispatchers.IO) {
        val token = getToken(provider)
            ?: throw Exception("Токен не найден для провайдера $provider")

        val authHeader = "Bearer ${token.accessToken}"

        when (provider) {
            GitProvider.GITHUB -> getGitHubRepositories(authHeader)
            GitProvider.GITLAB -> getGitLabRepositories(authHeader)
        }
    }

    private suspend fun getGitHubRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()

        val userReposResponse = githubApi.getUserRepositories(authHeader)

        if (userReposResponse.isSuccessful) {
            userReposResponse.body()?.forEach { repo ->
                repositories.add(repo.toGitRemoteRepository())
            }
        } else {
            throw Exception("Ошибка получения репозиториев пользователя: ${userReposResponse.code()}")
        }

        try {
            val orgsResponse = githubApi.getUserOrganizations(authHeader)
            if (orgsResponse.isSuccessful) {
                orgsResponse.body()?.forEach { org ->
                    try {
                        val orgReposResponse = githubApi.getOrganizationRepositories(authHeader, org.login)
                        if (orgReposResponse.isSuccessful) {
                            orgReposResponse.body()?.forEach { repo ->
                                repositories.add(repo.toGitRemoteRepository())
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        return repositories.distinctBy { it.id }
    }

    private suspend fun getGitLabRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()

        val projectsResponse = gitlabApi.getUserProjects(authHeader)

        if (projectsResponse.isSuccessful) {
            projectsResponse.body()?.forEach { project ->
                repositories.add(project.toGitRemoteRepository())
            }
        } else {
            throw Exception("Ошибка получения проектов GitLab: ${projectsResponse.code()}")
        }

        return repositories
    }

    fun isAuthenticated(provider: GitProvider): Boolean {
        return getToken(provider) != null
    }

    fun getCurrentUser(provider: GitProvider): GitUser? {
        val userJson = when (provider) {
            GitProvider.GITHUB -> preferences.getString(KEY_GITHUB_USER, null)
            GitProvider.GITLAB -> preferences.getString(KEY_GITLAB_USER, null)
        }
        return userJson?.let { gson.fromJson(it, GitUser::class.java) }
    }

    fun getAccessToken(provider: GitProvider): String? {
        return getToken(provider)?.accessToken
    }

    fun logout(provider: GitProvider) {
        val editor = preferences.edit()
        when (provider) {
            GitProvider.GITHUB -> {
                editor.remove(KEY_GITHUB_TOKEN)
                editor.remove(KEY_GITHUB_USER)
            }
            GitProvider.GITLAB -> {
                editor.remove(KEY_GITLAB_TOKEN)
                editor.remove(KEY_GITLAB_USER)
            }
        }
        editor.apply()
    }

    suspend fun getRepositoryApproximateSize(rawUrl: String): Long? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeRepositoryUrlForParsing(rawUrl)
        if (normalizedUrl.isBlank()) return@withContext null

        val uri = try {
            URI(normalizedUrl)
        } catch (e: Exception) {
            return@withContext null
        }

        val host = uri.host ?: return@withContext null
        val path = uri.path?.trim('/') ?: return@withContext null
        if (path.isBlank()) return@withContext null

        return@withContext try {
            when {
                host.contains("github.com", ignoreCase = true) -> fetchGitHubRepositorySize(path)
                host.contains("gitlab.com", ignoreCase = true) -> fetchGitLabRepositorySize(path)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the clean clone URL without embedded tokens.
     * Authentication is handled by RealGitRepository.resolveCredentialsProvider().
     */
    fun getCloneUrl(repository: GitRemoteRepository): String? {
        val token = getToken(repository.provider) ?: return null
        return repository.cloneUrl
    }

    private fun saveToken(provider: GitProvider, token: OAuthToken) {
        val key = when (provider) {
            GitProvider.GITHUB -> KEY_GITHUB_TOKEN
            GitProvider.GITLAB -> KEY_GITLAB_TOKEN
        }
        preferences.edit().putString(key, gson.toJson(token)).apply()
    }

    private fun saveUser(provider: GitProvider, user: GitUser) {
        val key = when (provider) {
            GitProvider.GITHUB -> KEY_GITHUB_USER
            GitProvider.GITLAB -> KEY_GITLAB_USER
        }
        preferences.edit().putString(key, gson.toJson(user)).apply()
    }

    private fun getToken(provider: GitProvider): OAuthToken? {
        val key = when (provider) {
            GitProvider.GITHUB -> KEY_GITHUB_TOKEN
            GitProvider.GITLAB -> KEY_GITLAB_TOKEN
        }
        val tokenJson = preferences.getString(key, null)
        return tokenJson?.let { gson.fromJson(it, OAuthToken::class.java) }
    }

    private fun normalizeRepositoryUrlForParsing(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""

        return when {
            trimmed.startsWith("git@") -> {
                val withoutPrefix = trimmed.removePrefix("git@")
                val parts = withoutPrefix.split(":", limit = 2)
                if (parts.size == 2) {
                    "https://${parts[0]}/${parts[1]}"
                } else {
                    "https://$withoutPrefix"
                }
            }
            trimmed.startsWith("ssh://git@") -> {
                val withoutPrefix = trimmed.removePrefix("ssh://git@")
                val parts = withoutPrefix.split(":", limit = 2)
                if (parts.size == 2) {
                    "https://${parts[0]}/${parts[1]}"
                } else {
                    "https://$withoutPrefix"
                }
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

        val lastIndex = segments.lastIndex
        segments[lastIndex] = segments[lastIndex].removeSuffix(".git")
        val projectPath = segments.joinToString("/")
        if (projectPath.isBlank()) return null

        val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
        val authHeader = getAccessToken(GitProvider.GITLAB)?.let { "Bearer $it" }

        val response = gitlabApi.getProject(
            authorization = authHeader,
            projectId = encodedPath,
            statistics = true
        )

        val body = when {
            response.isSuccessful -> response.body()
            authHeader != null -> {
                val fallback = gitlabApi.getProject(
                    authorization = null,
                    projectId = encodedPath,
                    statistics = true
                )
                if (fallback.isSuccessful) fallback.body() else null
            }
            else -> null
        }

        return body?.statistics?.repository_size ?: body?.statistics?.storage_size
    }
}

// Расширения для конвертации
private fun GitHubRepository.toGitRemoteRepository(): GitRemoteRepository {
    return GitRemoteRepository(
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
}

private fun GitLabRepository.toGitRemoteRepository(): GitRemoteRepository {
    return GitRemoteRepository(
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
}
