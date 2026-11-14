package com.gitflow.android.data.auth

import android.content.Context
import com.gitflow.android.data.models.GitProvider
import java.io.IOException
import java.util.Properties
import timber.log.Timber

object OAuthConfig {
    private var _githubClientId: String = ""
    private var _gitlabClientId: String = ""
    private var _bitbucketClientId: String = ""
    private var _bitbucketClientSecret: String = ""

    const val REDIRECT_URI = "gitflow://oauth/callback"

    val githubClientId: String get() = _githubClientId
    val gitlabClientId: String get() = _gitlabClientId
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
        _gitlabClientId = properties.getProperty("gitlab.client.id", "")
        _bitbucketClientId = properties.getProperty("bitbucket.client.id", "")
        _bitbucketClientSecret = properties.getProperty("bitbucket.client.secret", "")
    }

    private fun loadFromEnvironmentOrFile(context: Context) {
        _githubClientId = System.getenv("GITHUB_CLIENT_ID") ?: ""
        _gitlabClientId = System.getenv("GITLAB_CLIENT_ID") ?: ""
        _bitbucketClientId = System.getenv("BITBUCKET_CLIENT_ID") ?: ""
        _bitbucketClientSecret = System.getenv("BITBUCKET_CLIENT_SECRET") ?: ""

        try {
            val properties = Properties()
            context.openFileInput("oauth.properties").use { inputStream ->
                properties.load(inputStream)
            }
            if (_githubClientId.isEmpty()) _githubClientId = properties.getProperty("github.client.id", "")
            if (_gitlabClientId.isEmpty()) _gitlabClientId = properties.getProperty("gitlab.client.id", "")
            if (_bitbucketClientId.isEmpty()) _bitbucketClientId = properties.getProperty("bitbucket.client.id", "")
            if (_bitbucketClientSecret.isEmpty()) _bitbucketClientSecret = properties.getProperty("bitbucket.client.secret", "")
        } catch (e: IOException) {
            Timber.w("oauth.properties file not found: ${e.message}")
        }
    }

    /** Returns true if the given provider's OAuth credentials are available. */
    fun isProviderConfigured(provider: GitProvider): Boolean = when (provider) {
        // GitHub (device flow) and GitLab (public client with PKCE) need no secret: an APK can't keep one.
        GitProvider.GITHUB -> _githubClientId.isNotEmpty()
        GitProvider.GITLAB -> _gitlabClientId.isNotEmpty()
        GitProvider.BITBUCKET -> _bitbucketClientId.isNotEmpty() && _bitbucketClientSecret.isNotEmpty()
        GitProvider.GITEA, GitProvider.AZURE_DEVOPS -> true // PAT-based, no app credentials needed
    }

    /** Returns true if at least one OAuth provider is configured. */
    fun isConfigured(): Boolean = GitProvider.entries.any { isProviderConfigured(it) }
}
