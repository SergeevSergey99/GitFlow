package com.gitflow.android.data.auth

import android.content.Context
import com.gitflow.android.data.models.GitProvider
import java.io.IOException
import java.util.Properties
import timber.log.Timber

object OAuthConfig {
    private var _githubClientId: String = ""
    private var _githubClientSecret: String = ""
    private var _gitlabClientId: String = ""
    private var _gitlabClientSecret: String = ""
    private var _bitbucketClientId: String = ""
    private var _bitbucketClientSecret: String = ""

    const val REDIRECT_URI = "gitflow://oauth/callback"

    val githubClientId: String get() = _githubClientId
    val githubClientSecret: String get() = _githubClientSecret
    val gitlabClientId: String get() = _gitlabClientId
    val gitlabClientSecret: String get() = _gitlabClientSecret
    val bitbucketClientId: String get() = _bitbucketClientId
    val bitbucketClientSecret: String get() = _bitbucketClientSecret

    fun initialize(context: Context) {
        loadFromAssets(context)
    }

    private fun loadFromAssets(context: Context) {
        try {
            val properties = Properties()
            context.assets.open("oauth.properties").use { inputStream ->
                properties.load(inputStream)
            }
            applyProperties(properties)
        } catch (e: IOException) {
            Timber.w("oauth.properties not found in assets: ${e.message}")
            loadFromEnvironmentOrFile(context)
        }
    }

    private fun applyProperties(properties: Properties) {
        _githubClientId = properties.getProperty("github.client.id", "")
        _githubClientSecret = properties.getProperty("github.client.secret", "")
        _gitlabClientId = properties.getProperty("gitlab.client.id", "")
        _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")
        _bitbucketClientId = properties.getProperty("bitbucket.client.id", "")
        _bitbucketClientSecret = properties.getProperty("bitbucket.client.secret", "")
    }

    private fun loadFromEnvironmentOrFile(context: Context) {
        _githubClientId = System.getenv("GITHUB_CLIENT_ID") ?: ""
        _githubClientSecret = System.getenv("GITHUB_CLIENT_SECRET") ?: ""
        _gitlabClientId = System.getenv("GITLAB_CLIENT_ID") ?: ""
        _gitlabClientSecret = System.getenv("GITLAB_CLIENT_SECRET") ?: ""
        _bitbucketClientId = System.getenv("BITBUCKET_CLIENT_ID") ?: ""
        _bitbucketClientSecret = System.getenv("BITBUCKET_CLIENT_SECRET") ?: ""

        try {
            val properties = Properties()
            context.openFileInput("oauth.properties").use { inputStream ->
                properties.load(inputStream)
            }
            if (_githubClientId.isEmpty()) _githubClientId = properties.getProperty("github.client.id", "")
            if (_githubClientSecret.isEmpty()) _githubClientSecret = properties.getProperty("github.client.secret", "")
            if (_gitlabClientId.isEmpty()) _gitlabClientId = properties.getProperty("gitlab.client.id", "")
            if (_gitlabClientSecret.isEmpty()) _gitlabClientSecret = properties.getProperty("gitlab.client.secret", "")
            if (_bitbucketClientId.isEmpty()) _bitbucketClientId = properties.getProperty("bitbucket.client.id", "")
            if (_bitbucketClientSecret.isEmpty()) _bitbucketClientSecret = properties.getProperty("bitbucket.client.secret", "")
        } catch (e: IOException) {
            Timber.w("oauth.properties file not found: ${e.message}")
        }
    }

    /** Returns true if the given provider's OAuth credentials are available. */
    fun isProviderConfigured(provider: GitProvider): Boolean = when (provider) {
        GitProvider.GITHUB -> _githubClientId.isNotEmpty() && _githubClientSecret.isNotEmpty()
        GitProvider.GITLAB -> _gitlabClientId.isNotEmpty() && _gitlabClientSecret.isNotEmpty()
        GitProvider.BITBUCKET -> _bitbucketClientId.isNotEmpty() && _bitbucketClientSecret.isNotEmpty()
        GitProvider.GITEA, GitProvider.AZURE_DEVOPS -> true // PAT-based, no app credentials needed
    }

    /** Returns true if at least one OAuth provider is configured. */
    fun isConfigured(): Boolean = GitProvider.entries.any { isProviderConfigured(it) }
}
