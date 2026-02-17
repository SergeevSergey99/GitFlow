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
        loadFromAssets(context)
    }

    private fun loadFromAssets(context: Context) {
        try {
            val properties = Properties()
            context.assets.open("oauth.properties").use { inputStream ->
                properties.load(inputStream)
            }

            _githubClientId = properties.getProperty("github.client.id", "")
            _githubClientSecret = properties.getProperty("github.client.secret", "")
            _gitlabClientId = properties.getProperty("gitlab.client.id", "")
            _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")
        } catch (e: IOException) {
            android.util.Log.w("OAuthConfig", "oauth.properties not found in assets: ${e.message}")
            loadFromEnvironmentOrFile(context)
        }
    }

    private fun loadFromEnvironmentOrFile(context: Context) {
        _githubClientId = System.getenv("GITHUB_CLIENT_ID") ?: ""
        _githubClientSecret = System.getenv("GITHUB_CLIENT_SECRET") ?: ""
        _gitlabClientId = System.getenv("GITLAB_CLIENT_ID") ?: ""
        _gitlabClientSecret = System.getenv("GITLAB_CLIENT_SECRET") ?: ""

        if (_githubClientId.isEmpty() || _githubClientSecret.isEmpty() ||
            _gitlabClientId.isEmpty() || _gitlabClientSecret.isEmpty()) {
            try {
                val properties = Properties()
                context.openFileInput("oauth.properties").use { inputStream ->
                    properties.load(inputStream)
                }

                if (_githubClientId.isEmpty()) _githubClientId = properties.getProperty("github.client.id", "")
                if (_githubClientSecret.isEmpty()) _githubClientSecret = properties.getProperty("github.client.secret", "")
                if (_gitlabClientId.isEmpty()) _gitlabClientId = properties.getProperty("gitlab.client.id", "")
                if (_gitlabClientSecret.isEmpty()) _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")
            } catch (e: IOException) {
                android.util.Log.w("OAuthConfig", "oauth.properties file not found: ${e.message}")
            }
        }
    }

    fun isConfigured(): Boolean {
        return _githubClientId.isNotEmpty() && _githubClientSecret.isNotEmpty() &&
               _gitlabClientId.isNotEmpty() && _gitlabClientSecret.isNotEmpty()
    }
}
