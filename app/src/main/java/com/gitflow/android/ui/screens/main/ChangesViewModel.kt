package com.gitflow.android.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.FileDiff
import com.gitflow.android.data.models.ConflictResolutionStrategy
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.MergeConflict
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.StashEntry
import com.gitflow.android.data.models.SyncProgress
import com.gitflow.android.data.models.getOrNull
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.repository.RepositorySession
import com.gitflow.android.data.repository.RepositorySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
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
    val message: String? = null,
    /** Persistent network error banner; null when no error. */
    val networkErrorMessage: String? = null,
    /** Whether the failed network operation can be retried. */
    val isRetryable: Boolean = false,
    /** True if the last remote operation failed due to an expired session. */
    val needsReAuth: Boolean = false,
    /** In-progress merge/rebase whose conflicts must be resolved on this screen. */
    val operationState: RepoOperationState = RepoOperationState.NONE,
    /**
     * Label for the "keep current branch" side, already corrected for git's rebase inversion
     * (during a rebase the current branch is the one being rebased, not the "onto" ref).
     */
    val conflictOursLabel: String? = null,
    /** Label for the "take incoming" side. */
    val conflictTheirsLabel: String? = null,
    val changesError: String? = null,
    val openedDiff: FileDiff? = null,
    val isDiffLoading: Boolean = false,
    val diffError: String? = null,
    val historyPath: String? = null,
    val fileHistory: List<Commit> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val historyError: String? = null
)

class ChangesViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val session: RepositorySession,
    private val repository: Repository
) : AndroidViewModel(application) {

    private fun str(id: Int): String = getApplication<Application>().getString(id)
    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    /** Stores the last failed network operation so the user can retry it. */
    private var pendingRetry: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(
        ChangesUiState(
            canPush = repository.hasRemoteOrigin,
            pendingPushCommits = repository.pendingPushCommits
        )
    )
    val uiState: StateFlow<ChangesUiState> = _uiState.asStateFlow()

    /** The MERGE_MSG prefilled into the commit fields, so it can be dropped if the merge is aborted. */
    private var mergeMessagePrefill: Pair<String, String>? = null

    init {
        refresh()
        viewModelScope.launch {
            session.snapshot
                .filterNotNull()
                .filter { it.repository.id == repository.id }
                .collect { applySnapshot(it) }
        }
        viewModelScope.launch {
            gitRepository.repositoryChanges
                .filter { it == repository.id }
                .collectLatest { reloadChanges() }
        }
    }

    /** Reloads the working-tree state without clobbering commit input. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            reloadChanges()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // On failure the previous list stays visible under the error banner.
    private suspend fun reloadChanges() {
        when (val result = gitRepository.getChangedFiles(repository)) {
            is GitResult.Success -> _uiState.update { it.copy(changes = result.data, changesError = null) }
            is GitResult.Failure -> _uiState.update { it.copy(changesError = result.message) }
        }
    }

    private suspend fun reloadStashList() {
        when (val result = gitRepository.stashList(repository)) {
            is GitResult.Success -> _uiState.update { it.copy(stashList = result.data, isStashLoading = false) }
            is GitResult.Failure -> {
                _uiState.update { it.copy(isStashLoading = false) }
                emit(result.message)
            }
        }
    }

    private suspend fun applySnapshot(snapshot: RepositorySnapshot) {
        val info = snapshot.conflictInfo
        val operation = info.operation
        // ConflictInfo uses source = the operation's subject branch, target = its base.
        // "Keep current branch" = ours: for a merge that's the base (current branch); for a
        // rebase git swaps sides, so ours is the subject (the branch being rebased).
        val (oursLabel, theirsLabel) = if (operation == RepoOperationState.REBASING) {
            info.sourceLabel to info.targetLabel
        } else {
            info.targetLabel to info.sourceLabel
        }
        _uiState.update {
            it.copy(
                canPush = snapshot.repository.hasRemoteOrigin,
                pendingPushCommits = snapshot.repository.pendingPushCommits,
                pendingPullCommits = snapshot.pendingPullCommits,
                operationState = operation,
                conflictOursLabel = oursLabel,
                conflictTheirsLabel = theirsLabel
            )
        }

        val prefill = mergeMessagePrefill
        val current = _uiState.value
        if (operation != RepoOperationState.MERGING && prefill != null) {
            mergeMessagePrefill = null
            if (current.commitMessage to current.commitDescription == prefill) {
                _uiState.update { it.copy(commitMessage = "", commitDescription = "") }
            }
        }
        // During a merge, offer git's prepared message (.git/MERGE_MSG) so the user can
        // commit the merge right away without inventing a message.
        if (operation == RepoOperationState.MERGING && current.commitMessage.isBlank() && !current.isAmendMode) {
            gitRepository.getMergeMessage(repository)?.let { mergeMessage ->
                val split = splitCommitMessage(mergeMessage)
                mergeMessagePrefill = split
                _uiState.update { it.copy(commitMessage = split.first, commitDescription = split.second) }
            }
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
                emit(str(R.string.changes_commit_created))
            } else {
                emit(failureMessage(result, R.string.changes_commit_failed))
            }
        }
    }

    fun push() {
        guard {
            clearNetworkError()
            val result = gitRepository.pushWithProgress(repository) { progress ->
                _uiState.value = _uiState.value.copy(syncProgress = progress)
            }
            _uiState.update { it.copy(syncProgress = null) }
            when (result) {
                is GitResult.Success -> emit(str(R.string.changes_push_successful))
                is GitResult.Failure.AuthRequired -> setReAuthRequired()
                is GitResult.Failure.NetworkError -> setNetworkError(result) { push() }
                is GitResult.Failure -> emit(result.message.ifBlank { null } ?: str(R.string.changes_push_failed))
            }
        }
    }

    fun fetch() {
        guard {
            clearNetworkError()
            val result = gitRepository.fetch(repository)
            when (result) {
                is GitResult.Success -> {
                    emit(str(R.string.changes_fetch_successful))
                }
                is GitResult.Failure.AuthRequired -> setReAuthRequired()
                is GitResult.Failure.NetworkError -> setNetworkError(result) { fetch() }
                is GitResult.Failure -> emit(result.message.ifBlank { null } ?: str(R.string.changes_fetch_failed))
            }
        }
    }

    fun pull() {
        guard {
            clearNetworkError()
            val result = gitRepository.pull(repository)
            when (result) {
                is GitResult.Success -> {
                    emit(str(R.string.changes_pull_successful))
                }
                is GitResult.Failure.Conflict -> {
                    emit(str(R.string.changes_pull_conflicts))
                }
                is GitResult.Failure.AuthRequired -> setReAuthRequired()
                is GitResult.Failure.NetworkError -> setNetworkError(result) { pull() }
                is GitResult.Failure -> emit(result.message.ifBlank { null } ?: str(R.string.changes_pull_failed))
            }
        }
    }

    fun retryNetworkOp() {
        pendingRetry?.invoke()
    }

    fun dismissNetworkError() {
        clearNetworkError()
    }

    private fun setNetworkError(error: GitResult.Failure.NetworkError, retry: () -> Unit) {
        val msg = if (error.isOffline) str(R.string.network_error_offline) else str(R.string.network_error_server_unreachable)
        pendingRetry = retry
        _uiState.update { it.copy(networkErrorMessage = msg, isRetryable = true) }
    }

    private fun setReAuthRequired() {
        _uiState.update { it.copy(needsReAuth = true, networkErrorMessage = str(R.string.auth_session_expired_banner)) }
    }

    fun dismissReAuth() {
        _uiState.update { it.copy(needsReAuth = false, networkErrorMessage = null) }
    }

    private fun clearNetworkError() {
        pendingRetry = null
        _uiState.update { it.copy(networkErrorMessage = null, isRetryable = false, needsReAuth = false) }
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
        }
    }

    fun discardFileChanges(path: String) {
        guard {
            val result = gitRepository.discardFileChanges(repository, path)
            if (result is GitResult.Success) {
                emit(str(R.string.changes_discard_success))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_discard_failed))
            }
        }
    }

    /** "Keep the current branch's version" as the user understands it. */
    fun acceptOurs(file: FileChange) =
        resolveWithStrategy(file, ConflictResolutionStrategy.OURS, R.string.changes_conflict_resolved_current)

    /** "Take the incoming version" as the user understands it. */
    fun acceptTheirs(file: FileChange) =
        resolveWithStrategy(file, ConflictResolutionStrategy.THEIRS, R.string.changes_conflict_resolved_incoming)

    private fun resolveWithStrategy(file: FileChange, userStrategy: ConflictResolutionStrategy, successMsgId: Int) {
        guard {
            // git inverts ours/theirs during a rebase: stage 2 ("ours") is the branch being
            // rebased ONTO, stage 3 ("theirs") is the current branch's replayed commit.
            // The UI buttons promise "current branch" / "incoming", so flip the strategy
            // while a rebase is in progress to honor what the user actually asked for.
            val effective = if (_uiState.value.operationState == RepoOperationState.REBASING) {
                when (userStrategy) {
                    ConflictResolutionStrategy.OURS -> ConflictResolutionStrategy.THEIRS
                    ConflictResolutionStrategy.THEIRS -> ConflictResolutionStrategy.OURS
                }
            } else {
                userStrategy
            }
            val result = gitRepository.resolveConflict(repository, file.path, effective)
            if (result is GitResult.Success) {
                emit(str(successMsgId))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_conflict_resolve_failed))
            }
        }
    }

    // continue/abort of a merge/rebase are driven from the conflict header (MainViewModel);
    // this screen only handles per-file conflict resolution and the merge commit.

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
                emit(str(R.string.changes_conflict_manual_success))
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_conflict_resolve_failed))
            }
        }
    }

    fun openDiff(file: FileChange) {
        _uiState.update { it.copy(isDiffLoading = true, diffError = null) }
        viewModelScope.launch {
            val result = gitRepository.getWorkingFileDiff(repository, file.path, file.stage)
            val diff = result.getOrNull()
            val error = when {
                result is GitResult.Failure -> result.message
                diff == null -> str(R.string.changes_diff_unavailable)
                else -> null
            }
            _uiState.update { it.copy(isDiffLoading = false, openedDiff = diff, diffError = error) }
        }
    }

    fun dismissChangesError() {
        _uiState.update { it.copy(changesError = null) }
    }

    fun closeDiff() {
        _uiState.update { it.copy(openedDiff = null, diffError = null) }
    }

    fun openFileHistory(path: String) {
        _uiState.update {
            it.copy(historyPath = path, fileHistory = emptyList(), isHistoryLoading = true, historyError = null)
        }
        viewModelScope.launch {
            val history = gitRepository.getFileHistoryForPath(repository, path)
            val commits = history.getOrNull().orEmpty()
            val error = when {
                history is GitResult.Failure -> str(R.string.commit_detail_history_error)
                commits.isEmpty() -> str(R.string.commit_detail_history_no_changes)
                else -> null
            }
            _uiState.update { it.copy(fileHistory = commits, isHistoryLoading = false, historyError = error) }
        }
    }

    fun closeFileHistory() {
        _uiState.update { it.copy(historyPath = null) }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(conflictDetails = null) }
    }

    fun openStashDialog() {
        _uiState.update { it.copy(showStashDialog = true, isStashLoading = true) }
        viewModelScope.launch { reloadStashList() }
    }

    fun dismissStashDialog() {
        _uiState.update { it.copy(showStashDialog = false) }
    }

    fun stashSave(message: String, includeUntracked: Boolean) {
        guard {
            val result = gitRepository.stashSave(repository, message, includeUntracked)
            if (result is GitResult.Success) {
                emit(str(R.string.changes_stash_saved))
                reloadStashList()
            } else {
                emit(failureMessage(result, R.string.changes_stash_save_failed))
            }
        }
    }

    fun stashApply(stashIndex: Int) {
        guard {
            val result = gitRepository.stashApply(repository, stashIndex)
            if (result is GitResult.Success) {
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
                emit(str(R.string.changes_stash_applied))
                reloadStashList()
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_apply_failed))
            }
        }
    }

    fun stashDrop(stashIndex: Int) {
        guard {
            val result = gitRepository.stashDrop(repository, stashIndex)
            if (result is GitResult.Success) {
                emit(str(R.string.changes_stash_dropped))
                reloadStashList()
            } else {
                emit((result as? GitResult.Failure)?.message ?: str(R.string.changes_stash_drop_failed))
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun failureMessage(result: GitResult<*>, fallbackId: Int): String = when (result) {
        is GitResult.Failure.IdentityRequired -> str(R.string.changes_identity_required)
        is GitResult.Failure -> result.message
        else -> str(fallbackId)
    }

    private fun emit(message: String) {
        _uiState.update { it.copy(message = message) }
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

