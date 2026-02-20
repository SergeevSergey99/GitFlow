package com.gitflow.android.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.ConflictResolutionStrategy
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.MergeConflict
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangesUiState(
    val changes: List<FileChange> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val isConflictLoading: Boolean = false,
    val commitMessage: String = "",
    val conflictDetails: MergeConflict? = null,
    val canPush: Boolean = false,
    val pendingPushCommits: Int = 0,
    // Transient snackbar message — cleared after display
    val message: String? = null
)

class ChangesViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val repository: Repository
) : AndroidViewModel(application) {

    private fun str(id: Int): String = getApplication<Application>().getString(id)
    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private val _uiState = MutableStateFlow(
        ChangesUiState(
            canPush = repository.hasRemoteOrigin,
            pendingPushCommits = repository.pendingPushCommits
        )
    )
    val uiState: StateFlow<ChangesUiState> = _uiState.asStateFlow()

    init {
        loadChanges()
    }

    // Called by the composable when the repository object is updated externally
    // (e.g. pendingPushCommits changed via another screen)
    fun onRepositoryUpdated(updated: Repository) {
        _uiState.update {
            it.copy(
                canPush = updated.hasRemoteOrigin,
                pendingPushCommits = updated.pendingPushCommits
            )
        }
    }

    fun loadChanges() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    conflictDetails = null,
                    commitMessage = "",
                    isProcessing = false,
                    isConflictLoading = false
                )
            }
            try {
                val files = gitRepository.getChangedFiles(repository)
                val refreshed = gitRepository.refreshRepository(repository)
                _uiState.update {
                    it.copy(
                        changes = files,
                        isLoading = false,
                        canPush = refreshed?.hasRemoteOrigin ?: it.canPush,
                        pendingPushCommits = refreshed?.pendingPushCommits ?: it.pendingPushCommits
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun reloadChanges() {
        val files = gitRepository.getChangedFiles(repository)
        val refreshed = gitRepository.refreshRepository(repository)
        _uiState.update {
            it.copy(
                changes = files,
                canPush = refreshed?.hasRemoteOrigin ?: it.canPush,
                pendingPushCommits = refreshed?.pendingPushCommits ?: it.pendingPushCommits
            )
        }
    }

    // Guarded launch: ignores calls while an operation is already running
    private fun guard(action: suspend () -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                action()
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun setCommitMessage(text: String) {
        _uiState.update { it.copy(commitMessage = text) }
    }

    fun stageAll() {
        guard {
            val result = gitRepository.stageAll(repository)
            if (result !is GitResult.Success) {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_unable_to_stage))
            }
            reloadChanges()
        }
    }

    fun commit() {
        val message = _uiState.value.commitMessage.trim()
        if (message.isEmpty()) {
            emit(str(R.string.changes_commit_message_empty))
            return
        }
        guard {
            val result = gitRepository.commit(repository, message)
            if (result is GitResult.Success) {
                _uiState.update { it.copy(commitMessage = "") }
                reloadChanges()
                emit(str(R.string.changes_commit_created))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_commit_failed))
            }
        }
    }

    fun push() {
        guard {
            val result = gitRepository.push(repository)
            val msg = if (result is GitResult.Success) {
                str(R.string.changes_push_successful)
            } else {
                (result as? GitResult.Failure)?.message?.ifBlank { null } ?: str(R.string.changes_push_failed)
            }
            emit(msg)
        }
    }

    fun toggleFile(file: FileChange) {
        guard {
            val result = if (file.stage == ChangeStage.STAGED) {
                gitRepository.unstageFile(repository, file.path)
            } else {
                gitRepository.stageFile(repository, file)
            }
            if (result !is GitResult.Success) {
                val fallback = if (file.stage == ChangeStage.STAGED)
                    str(R.string.changes_unable_to_unstage_file)
                else
                    str(R.string.changes_unable_to_stage_file)
                emit((result as? GitResult.Failure)?.message ?: fallback)
            }
            reloadChanges()
        }
    }

    fun acceptOurs(file: FileChange) {
        guard {
            val result = gitRepository.resolveConflict(repository, file.path, ConflictResolutionStrategy.OURS)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_conflict_resolved_current))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_conflict_resolve_failed))
            }
        }
    }

    fun acceptTheirs(file: FileChange) {
        guard {
            val result = gitRepository.resolveConflict(repository, file.path, ConflictResolutionStrategy.THEIRS)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_conflict_resolved_incoming))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_conflict_resolve_failed))
            }
        }
    }

    fun openConflict(file: FileChange) {
        if (_uiState.value.isConflictLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConflictLoading = true) }
            try {
                val conflict = gitRepository.getMergeConflict(repository, file.path)
                if (conflict != null) {
                    _uiState.update { it.copy(conflictDetails = conflict, isConflictLoading = false) }
                } else {
                    _uiState.update { it.copy(isConflictLoading = false) }
                    emit(str(R.string.changes_conflict_details_failed))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isConflictLoading = false) }
                emit(e.localizedMessage ?: str(R.string.changes_conflict_details_failed))
            }
        }
    }

    fun resolveConflictWithContent(resolvedContent: String) {
        val path = _uiState.value.conflictDetails?.path ?: return
        guard {
            val result = gitRepository.resolveConflictWithContent(repository, path, resolvedContent)
            if (result is GitResult.Success) {
                _uiState.update { it.copy(conflictDetails = null) }
                reloadChanges()
                emit(str(R.string.changes_conflict_manual_success))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_conflict_resolve_failed))
            }
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(conflictDetails = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun emit(message: String) {
        _uiState.update { it.copy(message = message) }
    }
}

class ChangesViewModelFactory(
    private val application: Application,
    private val gitRepository: IGitRepository,
    private val repository: Repository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ChangesViewModel(application, gitRepository, repository) as T
}
