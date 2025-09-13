package com.gitflow.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.gitflow.android.data.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

class AuthManager(val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // OAuth конфигурация - в реальном приложении эти значения должны быть в секретном месте
    companion object {
        private const val GITHUB_CLIENT_ID = "your_github_client_id"
        private const val GITHUB_CLIENT_SECRET = "your_github_client_secret"
        private const val GITLAB_CLIENT_ID = "your_gitlab_client_id"
        private const val GITLAB_CLIENT_SECRET = "your_gitlab_client_secret"
        private const val REDIRECT_URI = "gitflow://oauth/callback"
        
        private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val GITHUB_API_URL = "https://github.com/"
        private const val GITLAB_AUTH_URL = "https://gitlab.com/oauth/authorize"
        private const val GITLAB_API_URL = "https://gitlab.com/"
        
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_GITLAB_TOKEN = "gitlab_token"
        private const val KEY_GITHUB_USER = "github_user"
        private const val KEY_GITLAB_USER = "gitlab_user"
    }
    
    private val githubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_URL)
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
    
    // Генерация URL для авторизации
    fun getAuthUrl(provider: GitProvider): String {
        return when (provider) {
            GitProvider.GITHUB -> {
                val scope = URLEncoder.encode("repo user", "UTF-8")
                "$GITHUB_AUTH_URL?client_id=$GITHUB_CLIENT_ID&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}&scope=$scope&response_type=code"
            }
            GitProvider.GITLAB -> {
                val scope = URLEncoder.encode("read_user read_repository write_repository", "UTF-8")
                "$GITLAB_AUTH_URL?client_id=$GITLAB_CLIENT_ID&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}&scope=$scope&response_type=code"
            }
        }
    }
    
    // Обработка OAuth callback
    suspend fun handleAuthCallback(provider: GitProvider, code: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                GitProvider.GITHUB -> handleGitHubCallback(code)
                GitProvider.GITLAB -> handleGitLabCallback(code)
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "Ошибка авторизации")
        }
    }
    
    private suspend fun handleGitHubCallback(code: String): AuthResult {
        try {
            val tokenRequest = mapOf(
                "client_id" to GITHUB_CLIENT_ID,
                "client_secret" to GITHUB_CLIENT_SECRET,
                "code" to code
            )
            
            val tokenResponse = githubApi.getAccessToken(tokenRequest)
            if (!tokenResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить токен")
            }
            
            val oauthResponse = tokenResponse.body()!!
            val token = OAuthToken(
                accessToken = oauthResponse.access_token,
                tokenType = oauthResponse.token_type,
                scope = oauthResponse.scope
            )
            
            val userResponse = githubApi.getCurrentUser("Bearer ${token.accessToken}")
            if (!userResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе")
            }
            
            val githubUser = userResponse.body()!!
            val user = GitUser(
                id = githubUser.id,
                login = githubUser.login,
                name = githubUser.name,
                email = githubUser.email,
                avatarUrl = githubUser.avatar_url,
                provider = GitProvider.GITHUB
            )
            
            // Сохраняем токен и пользователя
            saveToken(GitProvider.GITHUB, token)
            saveUser(GitProvider.GITHUB, user)
            
            return AuthResult(success = true, user = user, token = token)
        } catch (e: Exception) {
            return AuthResult(success = false, error = e.message ?: "Ошибка авторизации GitHub")
        }
    }
    
    private suspend fun handleGitLabCallback(code: String): AuthResult {
        try {
            val tokenResponse = gitlabApi.getAccessToken(
                clientId = GITLAB_CLIENT_ID,
                clientSecret = GITLAB_CLIENT_SECRET,
                code = code,
                redirectUri = REDIRECT_URI
            )
            
            if (!tokenResponse.isSuccessful) {
                return AuthResult(success = false, error = "Не удалось получить токен")
            }
            
            val oauthResponse = tokenResponse.body()!!
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
            
            val gitlabUser = userResponse.body()!!
            val user = GitUser(
                id = gitlabUser.id,
                login = gitlabUser.username,
                name = gitlabUser.name,
                email = gitlabUser.email,
                avatarUrl = gitlabUser.avatar_url,
                provider = GitProvider.GITLAB
            )
            
            // Сохраняем токен и пользователя
            saveToken(GitProvider.GITLAB, token)
            saveUser(GitProvider.GITLAB, user)
            
            return AuthResult(success = true, user = user, token = token)
        } catch (e: Exception) {
            return AuthResult(success = false, error = e.message ?: "Ошибка авторизации GitLab")
        }
    }
    
    // Получение списка доступных репозиториев
    suspend fun getRepositories(provider: GitProvider): List<GitRemoteRepository> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(provider) ?: return@withContext emptyList()
            val authHeader = "Bearer ${token.accessToken}"
            
            when (provider) {
                GitProvider.GITHUB -> getGitHubRepositories(authHeader)
                GitProvider.GITLAB -> getGitLabRepositories(authHeader)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun getGitHubRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        
        // Получаем репозитории пользователя
        val userReposResponse = githubApi.getUserRepositories(authHeader)
        if (userReposResponse.isSuccessful) {
            userReposResponse.body()?.forEach { repo ->
                repositories.add(repo.toGitRemoteRepository())
            }
        }
        
        // Получаем репозитории организаций
        val orgsResponse = githubApi.getUserOrganizations(authHeader)
        if (orgsResponse.isSuccessful) {
            orgsResponse.body()?.forEach { org ->
                val orgReposResponse = githubApi.getOrganizationRepositories(authHeader, org.login)
                if (orgReposResponse.isSuccessful) {
                    orgReposResponse.body()?.forEach { repo ->
                        repositories.add(repo.toGitRemoteRepository())
                    }
                }
            }
        }
        
        return repositories.distinctBy { it.id }
    }
    
    private suspend fun getGitLabRepositories(authHeader: String): List<GitRemoteRepository> {
        val repositories = mutableListOf<GitRemoteRepository>()
        
        // Получаем проекты пользователя
        val projectsResponse = gitlabApi.getUserProjects(authHeader)
        if (projectsResponse.isSuccessful) {
            projectsResponse.body()?.forEach { project ->
                repositories.add(project.toGitRemoteRepository())
            }
        }
        
        return repositories
    }
    
    // Проверка авторизации
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
    
    // Выход из аккаунта
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
    
    // Получение токена для клонирования
    fun getCloneUrl(repository: GitRemoteRepository, useHttps: Boolean = true): String? {
        val token = getToken(repository.provider) ?: return null
        
        return if (useHttps) {
            when (repository.provider) {
                GitProvider.GITHUB -> {
                    // Для GitHub используем токен в URL
                    repository.cloneUrl.replace("https://", "https://${token.accessToken}@")
                }
                GitProvider.GITLAB -> {
                    // Для GitLab используем токен в URL
                    repository.cloneUrl.replace("https://", "https://oauth2:${token.accessToken}@")
                }
            }
        } else {
            // SSH URL не требует токена в URL, но требует настроенного SSH ключа
            repository.sshUrl
        }
    }
    
    // Приватные методы для работы с хранением
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
        updatedAt = this.updated_at
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
        updatedAt = this.last_activity_at
    )
}
