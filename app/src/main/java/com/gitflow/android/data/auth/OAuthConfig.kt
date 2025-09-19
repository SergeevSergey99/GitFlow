package com.gitflow.android.data.auth

import android.content.Context
import java.io.IOException
import java.util.Properties

object OAuthConfig {
    private var _githubClientId: String = ""
    private var _githubClientSecret: String = ""
    private var _gitlabClientId: String = ""
    private var _gitlabClientSecret: String = ""
    
    const val REDIRECT_URI = "gitflow://oauth/callback"
    
    val githubClientId: String get() = _githubClientId
    val githubClientSecret: String get() = _githubClientSecret
    val gitlabClientId: String get() = _gitlabClientId
    val gitlabClientSecret: String get() = _gitlabClientSecret
    
    fun initialize(context: Context) {
        try {
            android.util.Log.d("OAuthConfig", "Начинаем инициализацию OAuth конфигурации")
            loadFromAssets(context)
            android.util.Log.d("OAuthConfig", "Инициализация OAuth конфигурации завершена")
        } catch (e: Exception) {
            android.util.Log.e("OAuthConfig", "Ошибка инициализации OAuth конфигурации: ${e.message}", e)
            throw e
        }
    }
    
    private fun loadFromAssets(context: Context) {
        try {
            android.util.Log.d("OAuthConfig", "Пытаемся загрузить oauth.properties из assets")
            val properties = Properties()
            context.assets.open("oauth.properties").use { inputStream ->
                android.util.Log.d("OAuthConfig", "Файл oauth.properties найден в assets")
                properties.load(inputStream)
            }

            _githubClientId = properties.getProperty("github.client.id", "")
            _githubClientSecret = properties.getProperty("github.client.secret", "")
            _gitlabClientId = properties.getProperty("gitlab.client.id", "")
            _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")

            android.util.Log.d("OAuthConfig", "Загружены значения из oauth.properties")
            android.util.Log.d("OAuthConfig", "GitHub Client ID: ${if(_githubClientId.isNotEmpty()) "задан" else "пуст"}")
            android.util.Log.d("OAuthConfig", "GitLab Client ID: ${if(_gitlabClientId.isNotEmpty()) "задан" else "пуст"}")

        } catch (e: IOException) {
            android.util.Log.w("OAuthConfig", "Файл oauth.properties не найден в assets: ${e.message}")
            // Попытка загрузить из переменных окружения или файла в корне проекта
            loadFromEnvironmentOrFile(context)
        }
    }
    
    private fun loadFromEnvironmentOrFile(context: Context) {
        android.util.Log.d("OAuthConfig", "Пытаемся загрузить из переменных окружения")

        // Загрузка из переменных окружения
        _githubClientId = System.getenv("GITHUB_CLIENT_ID") ?: ""
        _githubClientSecret = System.getenv("GITHUB_CLIENT_SECRET") ?: ""
        _gitlabClientId = System.getenv("GITLAB_CLIENT_ID") ?: ""
        _gitlabClientSecret = System.getenv("GITLAB_CLIENT_SECRET") ?: ""

        android.util.Log.d("OAuthConfig", "Из переменных окружения загружено:")
        android.util.Log.d("OAuthConfig", "GitHub Client ID: ${if(_githubClientId.isNotEmpty()) "задан" else "пуст"}")
        android.util.Log.d("OAuthConfig", "GitLab Client ID: ${if(_gitlabClientId.isNotEmpty()) "задан" else "пуст"}")

        // Если переменные окружения пусты, пытаемся загрузить из файла в корне проекта
        if (_githubClientId.isEmpty() || _githubClientSecret.isEmpty() ||
            _gitlabClientId.isEmpty() || _gitlabClientSecret.isEmpty()) {

            android.util.Log.d("OAuthConfig", "Некоторые значения пусты, пытаемся загрузить из файла")
            try {
                val properties = Properties()
                context.openFileInput("oauth.properties").use { inputStream ->
                    properties.load(inputStream)
                }

                if (_githubClientId.isEmpty()) _githubClientId = properties.getProperty("github.client.id", "")
                if (_githubClientSecret.isEmpty()) _githubClientSecret = properties.getProperty("github.client.secret", "")
                if (_gitlabClientId.isEmpty()) _gitlabClientId = properties.getProperty("gitlab.client.id", "")
                if (_gitlabClientSecret.isEmpty()) _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")

                android.util.Log.d("OAuthConfig", "Значения загружены из файла oauth.properties")
            } catch (e: IOException) {
                android.util.Log.w("OAuthConfig", "Файл oauth.properties в корне не найден: ${e.message}")
                android.util.Log.w("OAuthConfig", "Используем пустые значения по умолчанию")
            }
        }

        android.util.Log.d("OAuthConfig", "Итоговые значения:")
        android.util.Log.d("OAuthConfig", "Конфигурация завершена: ${isConfigured()}")
    }
    
    fun isConfigured(): Boolean {
        return _githubClientId.isNotEmpty() && _githubClientSecret.isNotEmpty() &&
               _gitlabClientId.isNotEmpty() && _gitlabClientSecret.isNotEmpty()
    }
}