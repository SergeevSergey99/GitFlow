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
import com.gitflow.android.data.models.StashEntry
import com.gitflow.android.data.models.SyncProgress
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
    val commitDescription: String = "",
    val isAmendMode: Boolean = false,
    val showStashDialog: Boolean = false,
    val stashList: List<StashEntry> = emptyList(),
    val isStashLoading: Boolean = false,
    val conflictDetails: MergeConflict? = null,
    val canPush: Boolean = false,
    val pendingPushCommits: Int = 0,
    val pendingPullCommits: Int = 0,
    /** Non-null while push is in progress; null otherwise. */
    val syncProgress: SyncProgress? = null,
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
        refreshPullCount()
    }

    fun loadChanges() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    conflictDetails = null,
                    commitMessage = "",
                    commitDescription = "",
                    isProcessing = false,
                    isConflictLoading = false
                )
            }
            try {
                val files = gitRepository.getChangedFiles(repository)
                val refreshed = gitRepository.refreshRepository(repository)
                val pullCount = loadPendingPullCount()
                _uiState.update {
                    it.copy(
                        changes = files,
                        isLoading = false,
                        canPush = refreshed?.hasRemoteOrigin ?: it.canPush,
                        pendingPushCommits = refreshed?.pendingPushCommits ?: it.pendingPushCommits,
                        pendingPullCommits = pullCount
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
        val pullCount = loadPendingPullCount()
        _uiState.update {
            it.copy(
                changes = files,
                canPush = refreshed?.hasRemoteOrigin ?: it.canPush,
                pendingPushCommits = refreshed?.pendingPushCommits ?: it.pendingPushCommits,
                pendingPullCommits = pullCount
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

    fun setCommitDescription(text: String) {
        _uiState.update { it.copy(commitDescription = text) }
    }

    fun toggleAmendMode() {
        val next = !_uiState.value.isAmendMode
        if (next) {
            viewModelScope.launch {
                val lastMessage = gitRepository.getLastCommitMessage(repository).orEmpty()
                val split = splitCommitMessage(lastMessage)
                _uiState.update {
                    it.copy(
                        isAmendMode = true,
                        commitMessage = split.first,
                        commitDescription = split.second
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isAmendMode = false, commitMessage = "", commitDescription = "") }
        }
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
        val title = _uiState.value.commitMessage.trim()
        val description = _uiState.value.commitDescription.trim()
        val message = buildString {
            append(title)
            if (description.isNotBlank()) {
                append("\n\n")
                append(description)
            }
        }
        if (title.isEmpty()) {
            emit(str(R.string.changes_commit_message_empty))
            return
        }
        val isAmend = _uiState.value.isAmendMode
        guard {
            val result = if (isAmend) {
                gitRepository.amendLastCommit(repository, message)
            } else {
                gitRepository.commit(repository, message)
            }
            if (result is GitResult.Success) {
                _uiState.update { it.copy(commitMessage = "", commitDescription = "", isAmendMode = false) }
                reloadChanges()
                emit(str(R.string.changes_commit_created))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_commit_failed))
            }
        }
    }

    fun push() {
        guard {
            val result = gitRepository.pushWithProgress(repository) { progress ->
                _uiState.value = _uiState.value.copy(syncProgress = progress)
            }
            _uiState.update { it.copy(syncProgress = null) }
            val msg = if (result is GitResult.Success) {
                str(R.string.changes_push_successful)
            } else {
                (result as? GitResult.Failure)?.message?.ifBlank { null } ?: str(R.string.changes_push_failed)
            }
            emit(msg)
        }
    }

    fun fetch() {
        guard {
            val result = gitRepository.fetch(repository)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_fetch_successful))
            } else {
                emit((result as? GitResult.Failure)?.message?.ifBlank { null } ?: str(R.string.changes_fetch_failed))
            }
        }
    }

    fun pull() {
        guard {
            val result = gitRepository.pull(repository)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_pull_successful))
            } else {
                emit((result as? GitResult.Failure)?.message?.ifBlank { null } ?: str(R.string.changes_pull_failed))
            }
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

    fun toggleFiles(files: List<FileChange>) {
        if (files.isEmpty()) return
        guard {
            files.forEach { file ->
                if (file.stage == ChangeStage.STAGED) {
                    gitRepository.unstageFile(repository, file.path)
                } else {
                    gitRepository.stageFile(repository, file)
                }
            }
            reloadChanges()
        }
    }

    fun discardFileChanges(path: String) {
        guard {
            val result = gitRepository.discardFileChanges(repository, path)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_discard_success))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_discard_failed))
            }
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

    fun openStashDialog() {
        _uiState.update { it.copy(showStashDialog = true, isStashLoading = true) }
        viewModelScope.launch {
            val entries = gitRepository.stashList(repository)
            _uiState.update { it.copy(stashList = entries, isStashLoading = false) }
        }
    }

    fun dismissStashDialog() {
        _uiState.update { it.copy(showStashDialog = false) }
    }

    fun stashSave(message: String) {
        guard {
            val result = gitRepository.stashSave(repository, message)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_stash_saved))
                // Reload stash list if dialog is open
                val entries = gitRepository.stashList(repository)
                _uiState.update { it.copy(stashList = entries) }
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_save_failed))
            }
        }
    }

    fun stashApply(stashIndex: Int) {
        guard {
            val result = gitRepository.stashApply(repository, stashIndex)
            if (result is GitResult.Success) {
                reloadChanges()
                emit(str(R.string.changes_stash_applied))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_apply_failed))
            }
        }
    }

    fun stashPop(stashIndex: Int) {
        guard {
            val result = gitRepository.stashPop(repository, stashIndex)
            if (result is GitResult.Success) {
                reloadChanges()
                val entries = gitRepository.stashList(repository)
                _uiState.update { it.copy(stashList = entries) }
                emit(str(R.string.changes_stash_applied))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_apply_failed))
            }
        }
    }

    fun stashDrop(stashIndex: Int) {
        guard {
            val result = gitRepository.stashDrop(repository, stashIndex)
            if (result is GitResult.Success) {
                val entries = gitRepository.stashList(repository)
                _uiState.update { it.copy(stashList = entries) }
                emit(str(R.string.changes_stash_dropped))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_drop_failed))
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun emit(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun refreshPullCount() {
        viewModelScope.launch {
            val count = loadPendingPullCount()
            _uiState.update { it.copy(pendingPullCommits = count) }
        }
    }

    private suspend fun loadPendingPullCount(): Int {
        return try {
            val branches = gitRepository.getBranches(repository)
            branches.firstOrNull { it.isLocal && it.name == repository.currentBranch }?.behind ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun splitCommitMessage(message: String): Pair<String, String> {
        val separatorIndex = message.indexOf("\n\n")
        return if (separatorIndex >= 0) {
            val title = message.substring(0, separatorIndex).trim()
            val description = message.substring(separatorIndex + 2).trim()
            title to description
        } else {
            val lines = message.lines()
            val title = lines.firstOrNull().orEmpty().trim()
            val description = lines.drop(1).joinToString("\n").trim()
            title to description
        }
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
