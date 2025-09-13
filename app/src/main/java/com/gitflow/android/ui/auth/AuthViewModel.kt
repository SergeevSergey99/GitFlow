package com.gitflow.android.ui.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    
    private val _githubUser = MutableStateFlow<GitUser?>(null)
    val githubUser: StateFlow<GitUser?> = _githubUser.asStateFlow()
    
    private val _gitlabUser = MutableStateFlow<GitUser?>(null)
    val gitlabUser: StateFlow<GitUser?> = _gitlabUser.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    var currentProvider: GitProvider? = null
        private set
    
    fun initializeAuth(authManager: AuthManager) {
        _githubUser.value = authManager.getCurrentUser(GitProvider.GITHUB)
        _gitlabUser.value = authManager.getCurrentUser(GitProvider.GITLAB)
    }
    
    fun startAuth(
        provider: GitProvider,
        authManager: AuthManager,
        launchIntent: (Intent) -> Unit
    ) {
        currentProvider = provider
        _errorMessage.value = null
        _isLoading.value = true
        
        try {
            val authUrl = authManager.getAuthUrl(provider)
            val intent = Intent(authManager.context, OAuthActivity::class.java).apply {
                putExtra(OAuthActivity.EXTRA_PROVIDER, provider.name)
                putExtra(OAuthActivity.EXTRA_AUTH_URL, authUrl)
                putExtra(OAuthActivity.EXTRA_REDIRECT_URI, "gitflow://oauth/callback")
            }
            launchIntent(intent)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorMessage.value = "Ошибка запуска авторизации: ${e.message}"
        }
    }
    
    fun handleAuthCallback(provider: GitProvider, code: String, authManager: AuthManager) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val result = authManager.handleAuthCallback(provider, code)
                
                if (result.success) {
                    when (provider) {
                        GitProvider.GITHUB -> _githubUser.value = result.user
                        GitProvider.GITLAB -> _gitlabUser.value = result.user
                    }
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
    
    fun logout(provider: GitProvider, authManager: AuthManager) {
        authManager.logout(provider)
        when (provider) {
            GitProvider.GITHUB -> _githubUser.value = null
            GitProvider.GITLAB -> _gitlabUser.value = null
        }
    }
    
    fun setError(error: String) {
        _errorMessage.value = error
        _isLoading.value = false
        currentProvider = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
