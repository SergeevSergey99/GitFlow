package com.gitflow.android.ui.repositories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.repository.RealGitRepository
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
    
    private var gitRepository: RealGitRepository? = null
    
    fun initializeRepositories(authManager: AuthManager) {
        gitRepository = RealGitRepository(authManager.context)
        
        // Выбираем первый доступный провайдер
        when {
            authManager.isAuthenticated(GitProvider.GITHUB) -> selectProvider(GitProvider.GITHUB, authManager)
            authManager.isAuthenticated(GitProvider.GITLAB) -> selectProvider(GitProvider.GITLAB, authManager)
            else -> _errorMessage.value = "Необходимо авторизоваться в GitHub или GitLab"
        }
    }
    
    fun selectProvider(provider: GitProvider, authManager: AuthManager) {
        if (!authManager.isAuthenticated(provider)) {
            _errorMessage.value = "Необходимо авторизоваться в ${provider.name}"
            return
        }
        
        _selectedProvider.value = provider
        loadRepositories(provider, authManager)
    }
    
    fun refreshRepositories(authManager: AuthManager) {
        _selectedProvider.value?.let { provider ->
            loadRepositories(provider, authManager)
        }
    }
    
    private fun loadRepositories(provider: GitProvider, authManager: AuthManager) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
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
    
    fun cloneRepository(
        repository: GitRemoteRepository,
        localPath: String,
        authManager: AuthManager,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isCloning.value = true
            _errorMessage.value = null
            
            try {
                val cloneUrl = authManager.getCloneUrl(repository, useHttps = true)
                    ?: throw Exception("Не удалось получить URL для клонирования")
                
                val gitRepo = gitRepository
                    ?: throw Exception("Git репозиторий не инициализирован")
                
                val result = gitRepo.cloneRepository(cloneUrl, localPath)
                
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _errorMessage.value = "Ошибка клонирования: ${result.exceptionOrNull()?.message}"
                }
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
}
