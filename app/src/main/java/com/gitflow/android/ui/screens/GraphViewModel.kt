package com.gitflow.android.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.R
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.ResetMode
import com.gitflow.android.data.models.getOrNull
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.repository.genericFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [closeDialogs] is false when the operation failed, so the user can retry from the same dialog. */
data class GraphOperationResult(val message: String, val closeDialogs: Boolean)

data class GraphUiState(
    val commits: List<Commit> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val hasMoreCommits: Boolean = true,
    val actionCommit: Commit? = null,
    val actionCommitTags: List<String> = emptyList(),
    val isTagsLoading: Boolean = false,
    val isOperationRunning: Boolean = false,
    val operationResult: GraphOperationResult? = null,
    val searchResults: List<Commit> = emptyList(),
    val isSearching: Boolean = false
)

class GraphViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val repository: Repository
) : AndroidViewModel(application) {

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private var pageSize = PAGE_SIZE
    private var commitsJob: Job? = null
    private var tagsJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadCommits()
        viewModelScope.launch {
            gitRepository.repositoryChanges
                .filter { it == repository.id }
                .collect {
                    loadCommits()
                    _uiState.value.actionCommit?.let { loadTags(it) }
                }
        }
    }

    fun loadMore() {
        pageSize += PAGE_SIZE
        loadCommits()
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = gitRepository.searchCommits(repository, query, SEARCH_LIMIT).getOrNull().orEmpty()
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun selectActionCommit(commit: Commit) {
        _uiState.update { it.copy(actionCommit = commit, actionCommitTags = emptyList()) }
        loadTags(commit)
    }

    fun clearActionCommit() {
        tagsJob?.cancel()
        _uiState.update { it.copy(actionCommit = null, actionCommitTags = emptyList(), isTagsLoading = false) }
    }

    fun resetTo(commit: Commit, mode: ResetMode, successMessage: String) =
        runOperation(successMessage) { gitRepository.resetToCommit(repository, commit.hash, mode) }

    fun createTag(commit: Commit, tagName: String, successMessage: String) =
        runOperation(successMessage) { gitRepository.createTag(repository, tagName, commit.hash) }

    fun deleteTag(tagName: String, successMessage: String) =
        runOperation(successMessage) { gitRepository.deleteTag(repository, tagName) }

    fun cherryPick(commit: Commit, successMessage: String) =
        runOperation(successMessage) { gitRepository.cherryPickCommit(repository, commit.hash) }

    fun merge(commit: Commit, successMessage: String) =
        runOperation(successMessage) { gitRepository.mergeCommitIntoCurrentBranch(repository, commit.hash) }

    fun consumeOperationResult() {
        _uiState.update { it.copy(operationResult = null) }
    }

    fun retry() = loadCommits()

    private fun loadCommits() {
        commitsJob?.cancel()
        commitsJob = viewModelScope.launch {
            when (val result = gitRepository.getCommits(repository, page = 0, pageSize = pageSize)) {
                is GitResult.Success -> _uiState.update {
                    it.copy(
                        commits = result.data,
                        hasMoreCommits = result.data.size >= pageSize,
                        isLoading = false,
                        loadError = null
                    )
                }
                is GitResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.message) }
            }
        }
    }

    private fun loadTags(commit: Commit) {
        tagsJob?.cancel()
        _uiState.update { it.copy(isTagsLoading = true) }
        tagsJob = viewModelScope.launch {
            val tags = gitRepository.getTagsForCommit(repository, commit.hash).getOrNull().orEmpty()
            _uiState.update { it.copy(actionCommitTags = tags, isTagsLoading = false) }
        }
    }

    // History reloads on the repository change event every one of these operations emits.
    private fun runOperation(successMessage: String, operation: suspend () -> GitResult<Unit>) {
        if (_uiState.value.isOperationRunning) return
        _uiState.update { it.copy(isOperationRunning = true) }
        viewModelScope.launch {
            val outcome = try {
                operation()
            } catch (exception: Exception) {
                genericFailure(exception)
            }
            val result = when (outcome) {
                is GitResult.Success -> GraphOperationResult(successMessage, closeDialogs = true)
                is GitResult.Failure.Conflict ->
                    GraphOperationResult(str(R.string.graph_commit_operation_conflicts), closeDialogs = true)
                is GitResult.Failure -> {
                    val reason = outcome.message.ifBlank { str(R.string.graph_commit_operation_failed_generic) }
                    GraphOperationResult(str(R.string.graph_commit_operation_failed, reason), closeDialogs = false)
                }
            }
            if (result.closeDialogs) clearActionCommit()
            _uiState.update { it.copy(isOperationRunning = false, operationResult = result) }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_LIMIT = 200
    }
}
