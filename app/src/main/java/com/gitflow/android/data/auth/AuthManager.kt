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
    
    init {
        try {
            android.util.Log.d("AuthManager", "Начинаем инициализацию AuthManager")
            OAuthConfig.initialize(context)

            // Проверка инициализации OAuth конфигурации
            if (!OAuthConfig.isConfigured()) {
                android.util.Log.w("AuthManager", "OAuth конфигурация не загружена! Проверьте наличие oauth.properties файла или переменных окружения")
            } else {
                android.util.Log.i("AuthManager", "OAuth конфигурация успешно загружена")
            }
            android.util.Log.d("AuthManager", "AuthManager инициализирован успешно")
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Ошибка инициализации AuthManager: ${e.message}", e)
            throw e
        }
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
    
    // Генерация URL для авторизации
    fun getAuthUrl(provider: GitProvider): String {
        if (!OAuthConfig.isConfigured()) {
            throw IllegalStateException("OAuth конфигурация не загружена! Проверьте наличие oauth.properties файла или переменных окружения")
        }
        
        return when (provider) {
            GitProvider.GITHUB -> {
                val scope = URLEncoder.encode("repo user", "UTF-8")
                "$GITHUB_AUTH_URL?client_id=${OAuthConfig.githubClientId}&redirect_uri=${URLEncoder.encode(OAuthConfig.REDIRECT_URI, "UTF-8")}&scope=$scope&response_type=code"
            }
            GitProvider.GITLAB -> {
                val scope = URLEncoder.encode("read_user read_repository write_repository", "UTF-8")
                "$GITLAB_AUTH_URL?client_id=${OAuthConfig.gitlabClientId}&redirect_uri=${URLEncoder.encode(OAuthConfig.REDIRECT_URI, "UTF-8")}&scope=$scope&response_type=code"
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
                "client_id" to OAuthConfig.githubClientId,
                "client_secret" to OAuthConfig.githubClientSecret,
                "code" to code,
                "redirect_uri" to OAuthConfig.REDIRECT_URI
            )

            android.util.Log.d("AuthManager", "Отправляем запрос на получение токена GitHub")
            android.util.Log.d("AuthManager", "Client ID: ${OAuthConfig.githubClientId}")
            android.util.Log.d("AuthManager", "Code: $code")
            val tokenResponse = githubTokenApi.getAccessToken(tokenRequest)

            if (!tokenResponse.isSuccessful) {
                val errorBody = tokenResponse.errorBody()?.string()
                android.util.Log.e("AuthManager", "Ошибка получения токена: ${tokenResponse.code()}, $errorBody")
                return AuthResult(success = false, error = "Не удалось получить токен: ${tokenResponse.code()}")
            }
            
            val oauthResponse = tokenResponse.body()!!
            android.util.Log.d("AuthManager", "Токен GitHub успешно получен")

            val token = OAuthToken(
                accessToken = oauthResponse.access_token,
                tokenType = oauthResponse.token_type,
                scope = oauthResponse.scope
            )
            android.util.Log.d("AuthManager", "Access Token: ${token.accessToken}")
            android.util.Log.d("AuthManager", "Token Type: ${token.tokenType}")
            android.util.Log.d("AuthManager", "Scope: ${token.scope}")

            android.util.Log.d("AuthManager", "Начинаем запрос к GitHub API для получения пользователя")
            val userResponse = try {
                val response = githubApi.getCurrentUser("Bearer ${token.accessToken}")
                android.util.Log.d("AuthManager", "Запрос к GitHub API выполнен")
                android.util.Log.d("AuthManager", "userResponse isSuccessful: ${response.isSuccessful}")
                android.util.Log.d("AuthManager", "userResponse code: ${response.code()}")
                response
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Ошибка при выполнении запроса к GitHub API: ${e.message}", e)
                return AuthResult(success = false, error = "Ошибка сети: ${e.message}")
            }

            if (!userResponse.isSuccessful) {
                val errorBody = userResponse.errorBody()?.string()
                android.util.Log.e("AuthManager", "Ошибка получения пользователя: ${userResponse.code()}, $errorBody")
                return AuthResult(success = false, error = "Не удалось получить информацию о пользователе: ${userResponse.code()}")
            }

            android.util.Log.d("AuthManager", "Успешно получен ответ от GitHub API для пользователя")
            val user = try {
                val githubUser = userResponse.body()!!
                android.util.Log.d("AuthManager", "GitHub пользователь: ${githubUser.login}, id: ${githubUser.id}")
                GitUser(
                    id = githubUser.id,
                    login = githubUser.login,
                    name = githubUser.name,
                    email = githubUser.email,
                    avatarUrl = githubUser.avatar_url,
                    provider = GitProvider.GITHUB
                )
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Ошибка парсинга пользователя GitHub: ${e.message}", e)
                return AuthResult(success = false, error = "Ошибка парсинга пользователя: ${e.message}")
            }
            
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
                clientId = OAuthConfig.gitlabClientId,
                clientSecret = OAuthConfig.gitlabClientSecret,
                code = code,
                redirectUri = OAuthConfig.REDIRECT_URI
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
            android.util.Log.d("AuthManager", "Начинаем получение репозиториев для провайдера: $provider")

            val token = getToken(provider)
            if (token == null) {
                android.util.Log.e("AuthManager", "Токен не найден для провайдера: $provider")
                throw Exception("Токен не найден для провайдера $provider")
            }

            val authHeader = "Bearer ${token.accessToken}"
            android.util.Log.d("AuthManager", "Токен найден, выполняем запрос к API")

            val repositories = when (provider) {
                GitProvider.GITHUB -> getGitHubRepositories(authHeader)
                GitProvider.GITLAB -> getGitLabRepositories(authHeader)
            }

            android.util.Log.d("AuthManager", "Получено ${repositories.size} репозиториев для провайдера $provider")
            repositories
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Ошибка при получении репозиториев для провайдера $provider: ${e.message}", e)
            throw e
        }
    }
    
    private suspend fun getGitHubRepositories(authHeader: String): List<GitRemoteRepository> {
        try {
            android.util.Log.d("AuthManager", "Запрашиваем репозитории пользователя от GitHub API")
            val repositories = mutableListOf<GitRemoteRepository>()

            // Получаем репозитории пользователя
            val userReposResponse = githubApi.getUserRepositories(authHeader)
            android.util.Log.d("AuthManager", "Ответ от getUserRepositories: код ${userReposResponse.code()}, успешно: ${userReposResponse.isSuccessful}")

            if (userReposResponse.isSuccessful) {
                val userRepos = userReposResponse.body()
                android.util.Log.d("AuthManager", "Получено ${userRepos?.size ?: 0} репозиториев пользователя")
                userRepos?.forEach { repo ->
                    repositories.add(repo.toGitRemoteRepository())
                }
            } else {
                val errorBody = userReposResponse.errorBody()?.string()
                android.util.Log.e("AuthManager", "Ошибка получения репозиториев пользователя: ${userReposResponse.code()}, $errorBody")
                throw Exception("Ошибка получения репозиториев пользователя: ${userReposResponse.code()}")
            }

            // Получаем репозитории организаций
            try {
                android.util.Log.d("AuthManager", "Запрашиваем организации пользователя")
                val orgsResponse = githubApi.getUserOrganizations(authHeader)
                android.util.Log.d("AuthManager", "Ответ от getUserOrganizations: код ${orgsResponse.code()}, успешно: ${orgsResponse.isSuccessful}")

                if (orgsResponse.isSuccessful) {
                    val orgs = orgsResponse.body()
                    android.util.Log.d("AuthManager", "Найдено ${orgs?.size ?: 0} организаций")
                    orgs?.forEach { org ->
                        try {
                            android.util.Log.d("AuthManager", "Запрашиваем репозитории для организации: ${org.login}")
                            val orgReposResponse = githubApi.getOrganizationRepositories(authHeader, org.login)
                            if (orgReposResponse.isSuccessful) {
                                val orgRepos = orgReposResponse.body()
                                android.util.Log.d("AuthManager", "Получено ${orgRepos?.size ?: 0} репозиториев для организации ${org.login}")
                                orgRepos?.forEach { repo ->
                                    repositories.add(repo.toGitRemoteRepository())
                                }
                            } else {
                                android.util.Log.w("AuthManager", "Не удалось получить репозитории для организации ${org.login}: ${orgReposResponse.code()}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("AuthManager", "Ошибка при получении репозиториев организации ${org.login}: ${e.message}")
                        }
                    }
                } else {
                    android.util.Log.w("AuthManager", "Не удалось получить список организаций: ${orgsResponse.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Ошибка при получении организаций: ${e.message}. Продолжаем только с пользовательскими репозиториями")
            }

            val result = repositories.distinctBy { it.id }
            android.util.Log.d("AuthManager", "Итого уникальных репозиториев GitHub: ${result.size}")
            return result
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Ошибка в getGitHubRepositories: ${e.message}", e)
            throw e
        }
    }
    
    private suspend fun getGitLabRepositories(authHeader: String): List<GitRemoteRepository> {
        try {
            android.util.Log.d("AuthManager", "Запрашиваем проекты пользователя от GitLab API")
            val repositories = mutableListOf<GitRemoteRepository>()

            // Получаем проекты пользователя
            val projectsResponse = gitlabApi.getUserProjects(authHeader)
            android.util.Log.d("AuthManager", "Ответ от getUserProjects: код ${projectsResponse.code()}, успешно: ${projectsResponse.isSuccessful}")

            if (projectsResponse.isSuccessful) {
                val projects = projectsResponse.body()
                android.util.Log.d("AuthManager", "Получено ${projects?.size ?: 0} проектов GitLab")
                projects?.forEach { project ->
                    repositories.add(project.toGitRemoteRepository())
                }
            } else {
                val errorBody = projectsResponse.errorBody()?.string()
                android.util.Log.e("AuthManager", "Ошибка получения проектов GitLab: ${projectsResponse.code()}, $errorBody")
                throw Exception("Ошибка получения проектов GitLab: ${projectsResponse.code()}")
            }

            android.util.Log.d("AuthManager", "Итого репозиториев GitLab: ${repositories.size}")
            return repositories
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Ошибка в getGitLabRepositories: ${e.message}", e)
            throw e
        }
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

    fun getAccessToken(provider: GitProvider): String? {
        return getToken(provider)?.accessToken
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
        android.util.Log.d("AuthManager", "getCloneUrl вызван для репозитория: ${repository.fullName}")
        android.util.Log.d("AuthManager", "Provider: ${repository.provider}")

        val token = getToken(repository.provider)
        if (token == null) {
            android.util.Log.e("AuthManager", "Токен не найден для провайдера: ${repository.provider}")
            return null
        }

        android.util.Log.d("AuthManager", "Формируем clone URL для репозитория: ${repository.fullName}")
        android.util.Log.d("AuthManager", "Оригинальный clone URL: ${repository.cloneUrl}")
        android.util.Log.d("AuthManager", "Токен найден: ${token.accessToken.take(7)}...")

        return if (useHttps) {
            val cloneUrl = when (repository.provider) {
                GitProvider.GITHUB -> {
                    // Для GitHub используем токен в URL с именем пользователя
                    repository.cloneUrl.replace("https://", "https://${token.accessToken}@")
                }
                GitProvider.GITLAB -> {
                    // Для GitLab используем токен в URL
                    repository.cloneUrl.replace("https://", "https://oauth2:${token.accessToken}@")
                }
            }
            android.util.Log.d("AuthManager", "Итоговый clone URL: $cloneUrl")
            cloneUrl
        } else {
            // SSH URL не требует токена в URL, но требует настроенного SSH ключа
            android.util.Log.d("AuthManager", "Используем SSH URL: ${repository.sshUrl}")
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
