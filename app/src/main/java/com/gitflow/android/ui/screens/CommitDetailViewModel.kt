package com.gitflow.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.repository.IGitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommitDetailTab { Files, Info, FileTree }

data class CommitDetailUiState(
    // Main dialog state
    val selectedTab: CommitDetailTab = CommitDetailTab.Files,
    val fileDiffs: List<FileDiff> = emptyList(),
    val isLoadingDiffs: Boolean = true,
    val diffsLoadError: String? = null,
    val selectedFile: FileDiff? = null,
    // FileTree state
    val fileTree: FileTreeNode? = null,
    val isLoadingTree: Boolean = false,
    val treeLoadError: String? = null,
    val filterQuery: String = "",
    val showFilterBar: Boolean = false,
    val contextMenuTargetPath: String? = null,
    val isRestoringFile: Boolean = false,
    val restoreResult: String? = null,
    // Parent commits state
    val parentCommits: List<Commit> = emptyList(),
    val isLoadingParents: Boolean = false,
    // File history state
    val historyDialogFile: FileTreeNode? = null,
    val fileHistory: List<Commit> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val historyError: String? = null,
    val historyDiffCommit: Commit? = null,
    val historyDiff: FileDiff? = null,
    val isHistoryDiffLoading: Boolean = false,
    val historyDiffError: String? = null,
    val viewedFile: FileTreeNode? = null,
    val viewedFileContent: String? = null,
    val isViewedFileLoading: Boolean = false,
    val viewedFileError: String? = null
)

class CommitDetailViewModel(
    private val gitRepository: IGitRepository,
    private val commit: Commit,
    private val repository: Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommitDetailUiState())
    val uiState: StateFlow<CommitDetailUiState> = _uiState.asStateFlow()

    init {
        loadDiffs()
        if (commit.parents.isNotEmpty()) loadParentCommits()
    }

    fun loadParentCommits() {
        _uiState.update { it.copy(isLoadingParents = true) }
        viewModelScope.launch {
            val loaded = commit.parents.mapNotNull { hash -> gitRepository.getCommitByHash(hash, repository).getOrNull() }
            _uiState.update { it.copy(parentCommits = loaded, isLoadingParents = false) }
        }
    }

    fun loadDiffs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiffs = true, diffsLoadError = null) }
            when (val result = gitRepository.getCommitDiffs(commit, repository)) {
                is GitResult.Success -> _uiState.update { it.copy(fileDiffs = result.data, isLoadingDiffs = false) }
                is GitResult.Failure -> _uiState.update { it.copy(isLoadingDiffs = false, diffsLoadError = result.message) }
            }
        }
    }

    fun loadFileTree() {
        if (_uiState.value.fileTree != null || _uiState.value.isLoadingTree) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeLoadError = null) }
            when (val result = gitRepository.getCommitFileTree(commit, repository)) {
                is GitResult.Success -> _uiState.update { it.copy(fileTree = result.data, isLoadingTree = false) }
                is GitResult.Failure -> _uiState.update { it.copy(isLoadingTree = false, treeLoadError = result.message) }
            }
        }
    }

    fun selectTab(tab: CommitDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == CommitDetailTab.FileTree) loadFileTree()
    }

    fun selectFile(file: FileDiff?) {
        _uiState.update { it.copy(selectedFile = file) }
    }

    fun setFilterQuery(query: String) {
        _uiState.update { it.copy(filterQuery = query) }
    }

    fun toggleFilterBar() {
        _uiState.update { it.copy(showFilterBar = !it.showFilterBar, filterQuery = "") }
    }

    fun closeFilterBar() {
        _uiState.update { it.copy(showFilterBar = false, filterQuery = "") }
    }

    fun setContextMenuTarget(path: String?) {
        _uiState.update { it.copy(contextMenuTargetPath = path) }
    }

    fun restoreFileToCommit(filePath: String, onResult: (Boolean) -> Unit) {
        if (_uiState.value.isRestoringFile) return
        _uiState.update { it.copy(isRestoringFile = true, contextMenuTargetPath = null) }
        viewModelScope.launch {
            val result = try {
                gitRepository.restoreFileToCommit(commit, filePath, repository)
            } catch (e: Exception) {
                GitResult.Failure.Generic(e.message ?: "")
            }
            _uiState.update { it.copy(isRestoringFile = false) }
            onResult(result is GitResult.Success)
        }
    }

    fun restoreFileToParentCommit(filePath: String, onResult: (Boolean) -> Unit) {
        if (_uiState.value.isRestoringFile) return
        _uiState.update { it.copy(isRestoringFile = true, contextMenuTargetPath = null) }
        viewModelScope.launch {
            val result = try {
                gitRepository.restoreFileToParentCommit(commit, filePath, repository)
            } catch (e: Exception) {
                GitResult.Failure.Generic(e.message ?: "")
            }
            _uiState.update { it.copy(isRestoringFile = false) }
            onResult(result is GitResult.Success)
        }
    }

    fun loadFileHistory(file: FileTreeNode) {
        _uiState.update { it.copy(historyDialogFile = file, isHistoryLoading = true, historyError = null, fileHistory = emptyList()) }
        viewModelScope.launch {
            when (val result = gitRepository.getFileHistory(commit, file.path, repository)) {
                is GitResult.Success -> _uiState.update { it.copy(fileHistory = result.data, isHistoryLoading = false) }
                is GitResult.Failure -> _uiState.update { it.copy(isHistoryLoading = false, historyError = result.message) }
            }
        }
    }

    fun dismissHistory() {
        _uiState.update {
            it.copy(
                historyDialogFile = null,
                fileHistory = emptyList(),
                historyError = null,
                historyDiffCommit = null,
                historyDiff = null,
                historyDiffError = null,
                isHistoryDiffLoading = false
            )
        }
    }

    fun loadHistoryDiff(historyCommit: Commit) {
        if (_uiState.value.isHistoryDiffLoading) return
        _uiState.update { it.copy(historyDiffCommit = historyCommit, historyDiff = null, historyDiffError = null, isHistoryDiffLoading = true) }
        val filePath = _uiState.value.historyDialogFile?.path ?: return
        viewModelScope.launch {
            when (val result = gitRepository.getCommitDiffs(historyCommit, repository)) {
                is GitResult.Success -> {
                    val matchingDiff = result.data.firstOrNull { it.path == filePath || it.oldPath == filePath }
                    _uiState.update {
                        it.copy(
                            historyDiff = matchingDiff,
                            historyDiffError = if (matchingDiff == null) "No diff found for this file" else null,
                            isHistoryDiffLoading = false
                        )
                    }
                }
                is GitResult.Failure -> _uiState.update { it.copy(isHistoryDiffLoading = false, historyDiffError = result.message) }
            }
        }
    }

    fun openFileViewer(file: FileTreeNode) {
        _uiState.update {
            it.copy(viewedFile = file, viewedFileContent = null, isViewedFileLoading = true, viewedFileError = null)
        }
        viewModelScope.launch {
            val content = gitRepository.getFileContent(commit, file.path, repository)
            _uiState.update {
                it.copy(
                    viewedFileContent = content.getOrNull(),
                    isViewedFileLoading = false,
                    viewedFileError = (content as? GitResult.Failure)?.message
                )
            }
        }
    }

    fun closeFileViewer() {
        _uiState.update { it.copy(viewedFile = null, viewedFileContent = null, viewedFileError = null) }
    }

    suspend fun loadFileBytes(filePath: String): ByteArray? =
        gitRepository.getFileContentBytes(commit, filePath, repository).getOrNull()

    fun dismissHistoryDiff() {
        _uiState.update { it.copy(historyDiffCommit = null, historyDiff = null, historyDiffError = null, isHistoryDiffLoading = false) }
    }
}

