package com.gitflow.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.*
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.repository.IGitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommitDetailUiState(
    // Main dialog state
    val selectedTab: Int = 0,
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
    // File history state
    val historyDialogFile: FileTreeNode? = null,
    val fileHistory: List<Commit> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val historyError: String? = null,
    val historyDiffCommit: Commit? = null,
    val historyDiff: FileDiff? = null,
    val isHistoryDiffLoading: Boolean = false,
    val historyDiffError: String? = null
)

class CommitDetailViewModel(
    private val gitRepository: IGitRepository,
    private val commit: Commit,
    private val repository: Repository?
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommitDetailUiState())
    val uiState: StateFlow<CommitDetailUiState> = _uiState.asStateFlow()

    init {
        loadDiffs()
    }

    fun loadDiffs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiffs = true, diffsLoadError = null) }
            try {
                val diffs = if (repository != null) {
                    gitRepository.getCommitDiffs(commit, repository)
                } else {
                    gitRepository.getCommitDiffs(commit)
                }
                _uiState.update { it.copy(fileDiffs = diffs, isLoadingDiffs = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDiffs = false, diffsLoadError = e.message) }
            }
        }
    }

    fun loadFileTree() {
        if (_uiState.value.fileTree != null || _uiState.value.isLoadingTree) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTree = true, treeLoadError = null) }
            try {
                val tree = if (repository != null) {
                    gitRepository.getCommitFileTree(commit, repository)
                } else {
                    gitRepository.getCommitFileTree(commit)
                }
                _uiState.update { it.copy(fileTree = tree, isLoadingTree = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingTree = false, treeLoadError = e.message) }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 3) loadFileTree()
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
                if (repository != null) {
                    gitRepository.restoreFileToCommit(commit, filePath, repository)
                } else {
                    gitRepository.restoreFileToCommit(commit, filePath)
                }
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
                if (repository != null) {
                    gitRepository.restoreFileToParentCommit(commit, filePath, repository)
                } else {
                    gitRepository.restoreFileToParentCommit(commit, filePath)
                }
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
            try {
                val history = if (repository != null) {
                    gitRepository.getFileHistory(commit, file.path, repository)
                } else {
                    gitRepository.getFileHistory(commit, file.path)
                }
                _uiState.update { it.copy(fileHistory = history, isHistoryLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHistoryLoading = false, historyError = e.message) }
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
            try {
                val diffs = if (repository != null) {
                    gitRepository.getCommitDiffs(historyCommit, repository)
                } else {
                    gitRepository.getCommitDiffs(historyCommit)
                }
                val matchingDiff = diffs.firstOrNull { it.path == filePath || it.oldPath == filePath }
                _uiState.update {
                    it.copy(
                        historyDiff = matchingDiff,
                        historyDiffError = if (matchingDiff == null) "No diff found for this file" else null,
                        isHistoryDiffLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHistoryDiffLoading = false, historyDiffError = e.message) }
            }
        }
    }

    fun dismissHistoryDiff() {
        _uiState.update { it.copy(historyDiffCommit = null, historyDiff = null, historyDiffError = null, isHistoryDiffLoading = false) }
    }
}

class CommitDetailViewModelFactory(
    private val gitRepository: IGitRepository,
    private val commit: Commit,
    private val repository: Repository?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CommitDetailViewModel(gitRepository, commit, repository) as T
}
