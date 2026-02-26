package com.gitflow.android.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authManager: AuthManager,
    private val settingsManager: AppSettingsManager
) : ViewModel() {

    private val _githubUser = MutableStateFlow<GitUser?>(null)
    val githubUser: StateFlow<GitUser?> = _githubUser.asStateFlow()

    private val _gitlabUser = MutableStateFlow<GitUser?>(null)
    val gitlabUser: StateFlow<GitUser?> = _gitlabUser.asStateFlow()

    private val _bitbucketUser = MutableStateFlow<GitUser?>(null)
    val bitbucketUser: StateFlow<GitUser?> = _bitbucketUser.asStateFlow()

    private val _giteaUser = MutableStateFlow<GitUser?>(null)
    val giteaUser: StateFlow<GitUser?> = _giteaUser.asStateFlow()

    private val _azureUser = MutableStateFlow<GitUser?>(null)
    val azureUser: StateFlow<GitUser?> = _azureUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _localAuthorName = MutableStateFlow(settingsManager.getLocalAuthorName())
    val localAuthorName: StateFlow<String> = _localAuthorName.asStateFlow()

    private val _localAuthorEmail = MutableStateFlow(settingsManager.getLocalAuthorEmail())
    val localAuthorEmail: StateFlow<String> = _localAuthorEmail.asStateFlow()

    var currentProvider: GitProvider? = null
        private set

    init {
        _githubUser.value = authManager.getCurrentUser(GitProvider.GITHUB)
        _gitlabUser.value = authManager.getCurrentUser(GitProvider.GITLAB)
        _bitbucketUser.value = authManager.getCurrentUser(GitProvider.BITBUCKET)
        _giteaUser.value = authManager.getCurrentUser(GitProvider.GITEA)
        _azureUser.value = authManager.getCurrentUser(GitProvider.AZURE_DEVOPS)

        // Auto-populate local author from first connected provider if not yet set
        if (settingsManager.getLocalAuthorName().isBlank()) {
            val firstUser = GitProvider.entries
                .mapNotNull { authManager.getCurrentUser(it) }
                .firstOrNull()
            if (firstUser != null) {
                val name = firstUser.name?.takeIf { it.isNotBlank() } ?: firstUser.login
                val email = firstUser.email?.takeIf { it.isNotBlank() }
                    ?: "${firstUser.login}@users.noreply.github.com"
                settingsManager.setLocalAuthorName(name)
                settingsManager.setLocalAuthorEmail(email)
                _localAuthorName.value = name
                _localAuthorEmail.value = email
            }
        }
    }

    fun setLocalAuthorName(name: String) {
        settingsManager.setLocalAuthorName(name)
        _localAuthorName.value = name
    }

    fun setLocalAuthorEmail(email: String) {
        settingsManager.setLocalAuthorEmail(email)
        _localAuthorEmail.value = email
    }

    /** Starts the OAuth WebView flow for GitHub, GitLab, or Bitbucket. */
    fun startAuth(provider: GitProvider, launchIntent: (Intent) -> Unit) {
        currentProvider = provider
        _errorMessage.value = null
        _isLoading.value = true

        try {
            val (authUrl, state) = authManager.getAuthUrl(provider)
            val intent = Intent(authManager.context, OAuthActivity::class.java).apply {
                putExtra(OAuthActivity.EXTRA_PROVIDER, provider.name)
                putExtra(OAuthActivity.EXTRA_AUTH_URL, authUrl)
                putExtra(OAuthActivity.EXTRA_REDIRECT_URI, "gitflow://oauth/callback")
                putExtra(OAuthActivity.EXTRA_STATE, state)
            }
            launchIntent(intent)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorMessage.value = "Ошибка запуска авторизации: ${e.message}"
        }
    }

    /** Handles the OAuth callback code from OAuthActivity (GitHub, GitLab, Bitbucket). */
    fun handleAuthCallback(provider: GitProvider, code: String, state: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = authManager.handleAuthCallback(provider, code, state)
                if (result.success) {
                    setUserForProvider(provider, result.user)
                } else {
                    _errorMessage.value = result.error ?: "Ошибка авторизации"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка обработки авторизации: ${e.message}"
            } finally {
                _isLoading.value = false
                currentProvider = null
            }
        }
    }

    /** Validates a Personal Access Token for Gitea or Azure DevOps. */
    fun validatePAT(provider: GitProvider, instanceUrl: String, username: String, pat: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = authManager.validateAndSavePAT(provider, instanceUrl, username, pat)
                if (result.success) {
                    setUserForProvider(provider, result.user)
                } else {
                    _errorMessage.value = result.error ?: "Ошибка проверки токена"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка проверки токена: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout(provider: GitProvider) {
        authManager.logout(provider)
        setUserForProvider(provider, null)
    }

    fun setError(error: String) {
        _errorMessage.value = error
        _isLoading.value = false
        currentProvider = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun setUserForProvider(provider: GitProvider, user: GitUser?) {
        when (provider) {
            GitProvider.GITHUB -> _githubUser.value = user
            GitProvider.GITLAB -> _gitlabUser.value = user
            GitProvider.BITBUCKET -> _bitbucketUser.value = user
            GitProvider.GITEA -> _giteaUser.value = user
            GitProvider.AZURE_DEVOPS -> _azureUser.value = user
        }
    }
}
