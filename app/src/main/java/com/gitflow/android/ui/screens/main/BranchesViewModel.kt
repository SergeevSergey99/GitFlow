package com.gitflow.android.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.Branch
import com.gitflow.android.data.models.GitResult
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BranchesUiState(
    val branches: List<Branch> = emptyList(),
    val isLoading: Boolean = false,
    val operationInProgress: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    /** In-progress git operation (merge/rebase) whose conflicts are awaiting resolution. */
    val operationState: RepoOperationState = RepoOperationState.NONE,
    /** Incremented on every successful mutation so the host can refresh without closing the dialog. */
    val mutationSignal: Int = 0
)

class BranchesViewModel(
    application: Application,
    private val repository: Repository,
    private val gitRepository: IGitRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BranchesUiState())
    val uiState: StateFlow<BranchesUiState> = _uiState.asStateFlow()

    private var isProcessing = false

    init {
        loadBranches()
    }

    fun loadBranches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val branches = gitRepository.getBranches(repository)
            val state = gitRepository.getRepositoryState(repository)
            _uiState.update { it.copy(branches = branches, operationState = state, isLoading = false) }
        }
    }

    fun checkoutBranch(branch: Branch) {
        runOp {
            when (val result = gitRepository.checkoutBranch(repository, branch.name, branch.isLocal)) {
                is GitResult.Success -> succeed("Switched to ${branch.name}")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun createBranch(name: String, checkout: Boolean) {
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Branch name cannot be empty") }
            return
        }
        runOp {
            when (val result = gitRepository.createBranch(repository, name.trim(), checkout)) {
                is GitResult.Success -> succeed("Branch $name created")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun deleteBranch(branch: Branch, force: Boolean) {
        runOp {
            when (val result = gitRepository.deleteBranch(repository, branch.name, force)) {
                is GitResult.Success -> succeed("Branch ${branch.name} deleted")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun pushBranch(branch: Branch) {
        runOp {
            when (val result = gitRepository.pushBranch(repository, branch.name)) {
                is GitResult.Success -> succeed("Pushed ${branch.name}")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun renameBranch(branch: Branch, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == branch.name) return
        runOp {
            when (val result = gitRepository.renameBranch(repository, branch.name, trimmed)) {
                is GitResult.Success -> succeed("Branch renamed to $trimmed")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun mergeBranch(branch: Branch) {
        runOp {
            when (val result = gitRepository.mergeBranch(repository, branch.name)) {
                is GitResult.Success -> succeed("Merged ${branch.name} into current branch")
                is GitResult.Failure.Conflict -> conflict()
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun rebaseOnto(branch: Branch) {
        runOp {
            when (val result = gitRepository.rebaseCurrentOnto(repository, branch.name)) {
                is GitResult.Success -> succeed("Rebased current branch onto ${branch.name}")
                is GitResult.Failure.Conflict -> conflict()
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun continueRebase() {
        runOp {
            when (val result = gitRepository.rebaseContinue(repository)) {
                is GitResult.Success -> succeed("Rebase completed")
                is GitResult.Failure.Conflict -> conflict()
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    /** Aborts whichever operation (merge or rebase) is currently in progress. */
    fun abortOperation() {
        runOp {
            val result = when (_uiState.value.operationState) {
                RepoOperationState.REBASING -> gitRepository.rebaseAbort(repository)
                else -> gitRepository.abortMerge(repository)
            }
            when (result) {
                is GitResult.Success -> succeed("Operation aborted")
                is GitResult.Failure -> fail(result.message)
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /** Runs [block] under the single-operation guard, then reloads branch + operation state. */
    private fun runOp(block: suspend () -> Unit) {
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, errorMessage = null, message = null) }
            try {
                block()
            } finally {
                isProcessing = false
                _uiState.update { it.copy(operationInProgress = false) }
                loadBranches()
            }
        }
    }

    private fun succeed(msg: String) {
        _uiState.update { it.copy(message = msg, mutationSignal = it.mutationSignal + 1) }
    }

    /**
     * A conflict left an operation in progress. No message is set — the OperationBanner
     * (driven by operationState from the follow-up loadBranches) already conveys it, and a
     * second error card would be redundant. Just signal the host to re-check state.
     */
    private fun conflict() {
        _uiState.update { it.copy(mutationSignal = it.mutationSignal + 1) }
    }

    private fun fail(msg: String) {
        _uiState.update { it.copy(errorMessage = msg) }
    }
}
