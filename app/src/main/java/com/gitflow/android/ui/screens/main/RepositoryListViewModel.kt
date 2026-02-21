package com.gitflow.android.ui.screens.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.services.CloneRepositoryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepositoryListUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val repositoryToDelete: Repository? = null,
    val deleteMessage: String? = null
)

class RepositoryListViewModel(
    application: Application,
    private val gitRepository: IGitRepository
) : AndroidViewModel(application) {

    private fun str(id: Int): String = getApplication<Application>().getString(id)

    private val _uiState = MutableStateFlow(RepositoryListUiState())
    val uiState: StateFlow<RepositoryListUiState> = _uiState.asStateFlow()

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, errorMessage = null) }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, errorMessage = null, isLoading = false) }
    }

    fun clearDeleteMessage() {
        _uiState.update { it.copy(deleteMessage = null) }
    }

    fun setDialogError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
    }

    fun createRepository(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val app = getApplication<Application>()
            val baseDir = AppSettingsManager(app).getRepositoriesBaseDir(app)
            val localPath = "${baseDir.absolutePath}/$name"
            when (val result = gitRepository.createRepository(name, localPath)) {
                is GitResult.Success -> _uiState.update { it.copy(showAddDialog = false, isLoading = false) }
                is GitResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.message.ifBlank { str(R.string.repo_list_create_error) }, isLoading = false)
                }
            }
        }
    }

    fun addRepository(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = gitRepository.addRepository(path)) {
                is GitResult.Success -> _uiState.update { it.copy(showAddDialog = false, isLoading = false) }
                is GitResult.Failure -> _uiState.update {
                    it.copy(errorMessage = result.message.ifBlank { str(R.string.repo_list_add_error) }, isLoading = false)
                }
            }
        }
    }

    fun startManualClone(
        context: Context,
        authManager: AuthManager,
        name: String,
        url: String,
        approximateSize: Long?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val app = getApplication<Application>()
                val baseDir = AppSettingsManager(app).getRepositoriesBaseDir(app)
                val fallbackName = url.substringAfterLast('/').removeSuffix(".git")
                val targetName = if (name.isBlank()) fallbackName else name
                val targetPath = "${baseDir.absolutePath}/$targetName"

                val resolvedSize = try {
                    approximateSize ?: authManager.getRepositoryApproximateSize(url)
                } catch (e: Exception) {
                    null
                }

                val started = CloneRepositoryService.start(
                    context = context,
                    repoName = targetName,
                    repoFullName = url,
                    cloneUrl = url,
                    localPath = targetPath,
                    approximateSize = resolvedSize
                )

                if (started) {
                    _uiState.update { it.copy(showAddDialog = false, isLoading = false, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = str(R.string.clone_wifi_only_error), isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unknown error occurred", isLoading = false) }
            }
        }
    }

    fun showDeleteConfirm(repo: Repository) {
        _uiState.update { it.copy(showDeleteConfirmDialog = true, repositoryToDelete = repo, deleteMessage = null) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false, repositoryToDelete = null, deleteMessage = null) }
    }

    fun deleteRepository(repo: Repository, deleteFiles: Boolean) {
        viewModelScope.launch {
            if (deleteFiles) {
                when (val result = gitRepository.removeRepositoryWithFiles(repo.id)) {
                    is GitResult.Success -> _uiState.update {
                        it.copy(showDeleteConfirmDialog = false, repositoryToDelete = null, deleteMessage = null)
                    }
                    is GitResult.Failure -> _uiState.update {
                        it.copy(deleteMessage = result.message.ifBlank { str(R.string.repo_list_delete_error) })
                    }
                }
            } else {
                gitRepository.removeRepository(repo.id)
                _uiState.update { it.copy(showDeleteConfirmDialog = false, repositoryToDelete = null, deleteMessage = null) }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val gitRepository: IGitRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RepositoryListViewModel(application, gitRepository) as T
    }
}
