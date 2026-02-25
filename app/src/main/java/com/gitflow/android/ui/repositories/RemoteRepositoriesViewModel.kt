package com.gitflow.android.ui.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.services.CloneRepositoryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteRepositoriesViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _repositories = MutableStateFlow<List<GitRemoteRepository>>(emptyList())
    val repositories: StateFlow<List<GitRemoteRepository>> = _repositories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCloning = MutableStateFlow(false)
    val isCloning: StateFlow<Boolean> = _isCloning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedProvider = MutableStateFlow<GitProvider?>(null)
    val selectedProvider: StateFlow<GitProvider?> = _selectedProvider.asStateFlow()

    private val _authenticatedProviders = MutableStateFlow<List<GitProvider>>(emptyList())
    val authenticatedProviders: StateFlow<List<GitProvider>> = _authenticatedProviders.asStateFlow()

    init {
        refreshAuthState()
    }

    /** Re-reads auth state — call when returning to this screen after account changes. */
    fun refreshAuthState() {
        val providers = GitProvider.entries.filter { authManager.isAuthenticated(it) }
        _authenticatedProviders.value = providers

        when {
            providers.isEmpty() -> {
                _errorMessage.value = "Необходимо авторизоваться в одном из Git-провайдеров. Перейдите в Настройки → Управление аккаунтами."
                _isLoading.value = false
            }
            _selectedProvider.value == null || !providers.contains(_selectedProvider.value) -> {
                selectProvider(providers.first())
            }
        }
    }

    fun selectProvider(provider: GitProvider) {
        try {
            if (!authManager.isAuthenticated(provider)) {
                _errorMessage.value = "Необходимо авторизоваться в ${provider.name}"
                return
            }
            _selectedProvider.value = provider
            loadRepositories(provider)
        } catch (e: Exception) {
            _errorMessage.value = "Ошибка при выборе провайдера: ${e.message}"
        }
    }

    fun refreshRepositories() {
        _selectedProvider.value?.let { loadRepositories(it) }
    }

    private fun loadRepositories(provider: GitProvider) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                val repos = authManager.getRepositories(provider)
                _repositories.value = repos.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки репозиториев: ${e.message}"
                _repositories.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startCloneInBackground(
        context: Context,
        repository: GitRemoteRepository,
        localPath: String,
        onStarted: () -> Unit
    ) {
        viewModelScope.launch {
            _isCloning.value = true
            _errorMessage.value = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    _errorMessage.value = context.getString(R.string.notification_permission_required)
                    return@launch
                }
                val cloneUrl = authManager.getCloneUrl(repository)
                    ?: throw Exception("Не удалось получить URL для клонирования")
                val started = CloneRepositoryService.start(
                    context = context,
                    repository = repository,
                    cloneUrl = cloneUrl,
                    localPath = localPath
                )
                if (!started) {
                    _errorMessage.value = context.getString(R.string.clone_wifi_only_error)
                    return@launch
                }
                onStarted()
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка клонирования: ${e.message}"
            } finally {
                _isCloning.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun showError(message: String) {
        _errorMessage.value = message
    }
}
