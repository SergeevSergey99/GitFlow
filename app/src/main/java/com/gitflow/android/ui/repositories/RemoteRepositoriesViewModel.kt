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

class RemoteRepositoriesViewModel : ViewModel() {
    
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
    
    fun initializeRepositories(authManager: AuthManager) {
        try {
            android.util.Log.d("RemoteRepositoriesViewModel", "Инициализация RemoteRepositoriesViewModel")

            // Выбираем первый доступный провайдер
            when {
                authManager.isAuthenticated(GitProvider.GITHUB) -> {
                    android.util.Log.d("RemoteRepositoriesViewModel", "GitHub авторизован, выбираем GitHub")
                    selectProvider(GitProvider.GITHUB, authManager)
                }
                authManager.isAuthenticated(GitProvider.GITLAB) -> {
                    android.util.Log.d("RemoteRepositoriesViewModel", "GitLab авторизован, выбираем GitLab")
                    selectProvider(GitProvider.GITLAB, authManager)
                }
                else -> {
                    android.util.Log.w("RemoteRepositoriesViewModel", "Ни один из провайдеров не авторизован")
                    _errorMessage.value = "Необходимо авторизоваться в GitHub или GitLab. Настройте OAuth конфигурацию в oauth.properties"
                    _isLoading.value = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RemoteRepositoriesViewModel", "Ошибка при инициализации: ${e.message}", e)
            _errorMessage.value = "Ошибка инициализации: ${e.message}"
        }
    }
    
    fun selectProvider(provider: GitProvider, authManager: AuthManager) {
        try {
            android.util.Log.d("RemoteRepositoriesViewModel", "Выбор провайдера: $provider")

            if (!authManager.isAuthenticated(provider)) {
                android.util.Log.e("RemoteRepositoriesViewModel", "Провайдер $provider не авторизован")
                _errorMessage.value = "Необходимо авторизоваться в ${provider.name}"
                return
            }

            android.util.Log.d("RemoteRepositoriesViewModel", "Провайдер $provider выбран, загружаем репозитории")
            _selectedProvider.value = provider
            loadRepositories(provider, authManager)
        } catch (e: Exception) {
            android.util.Log.e("RemoteRepositoriesViewModel", "Ошибка при выборе провайдера $provider: ${e.message}", e)
            _errorMessage.value = "Ошибка при выборе провайдера: ${e.message}"
        }
    }
    
    fun refreshRepositories(authManager: AuthManager) {
        _selectedProvider.value?.let { provider ->
            loadRepositories(provider, authManager)
        }
    }
    
    private fun loadRepositories(provider: GitProvider, authManager: AuthManager) {
        viewModelScope.launch {
            try {
                android.util.Log.d("RemoteRepositoriesViewModel", "Начинаем загрузку репозиториев для провайдера: $provider")
                _isLoading.value = true
                _errorMessage.value = null

                val repos = authManager.getRepositories(provider)
                android.util.Log.d("RemoteRepositoriesViewModel", "Успешно загружено ${repos.size} репозиториев")
                _repositories.value = repos.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                android.util.Log.e("RemoteRepositoriesViewModel", "Ошибка загрузки репозиториев для провайдера $provider: ${e.message}", e)
                _errorMessage.value = "Ошибка загрузки репозиториев: ${e.message}"
                _repositories.value = emptyList()
            } finally {
                android.util.Log.d("RemoteRepositoriesViewModel", "Загрузка репозиториев завершена")
                _isLoading.value = false
            }
        }
    }
    
    fun startCloneInBackground(
        context: Context,
        repository: GitRemoteRepository,
        localPath: String,
        authManager: AuthManager,
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

                android.util.Log.d("RemoteRepositoriesViewModel", "Запрашиваем clone URL для репозитория: ${repository.fullName}")
                val cloneUrl = authManager.getCloneUrl(repository, useHttps = true)
                    ?: throw Exception("Не удалось получить URL для клонирования")

                android.util.Log.d("RemoteRepositoriesViewModel", "Получен clone URL: $cloneUrl")

                CloneRepositoryService.start(
                    context = context,
                    repository = repository,
                    cloneUrl = cloneUrl,
                    localPath = localPath
                )

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
