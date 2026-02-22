package com.gitflow.android.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.Branch
import com.gitflow.android.data.models.GitResult
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
    val errorMessage: String? = null
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
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val branches = gitRepository.getBranches(repository)
            _uiState.update { it.copy(branches = branches, isLoading = false) }
        }
    }

    fun checkoutBranch(branch: Branch) {
        if (!guard()) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, errorMessage = null) }
            val result = gitRepository.checkoutBranch(repository, branch.name, branch.isLocal)
            when (result) {
                is GitResult.Success -> {
                    _uiState.update { it.copy(message = "Switched to ${branch.name}") }
                    loadBranches()
                }
                is GitResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
            isProcessing = false
            _uiState.update { it.copy(operationInProgress = false) }
        }
    }

    fun createBranch(name: String, checkout: Boolean) {
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Branch name cannot be empty") }
            return
        }
        if (!guard()) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, errorMessage = null) }
            val result = gitRepository.createBranch(repository, name.trim(), checkout)
            when (result) {
                is GitResult.Success -> {
                    _uiState.update { it.copy(message = "Branch $name created") }
                    loadBranches()
                }
                is GitResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
            isProcessing = false
            _uiState.update { it.copy(operationInProgress = false) }
        }
    }

    fun deleteBranch(branch: Branch, force: Boolean) {
        if (!guard()) return
        viewModelScope.launch {
            _uiState.update { it.copy(operationInProgress = true, errorMessage = null) }
            val result = gitRepository.deleteBranch(repository, branch.name, force)
            when (result) {
                is GitResult.Success -> {
                    _uiState.update { it.copy(message = "Branch ${branch.name} deleted") }
                    loadBranches()
                }
                is GitResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
            isProcessing = false
            _uiState.update { it.copy(operationInProgress = false) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun guard(): Boolean {
        if (isProcessing) return false
        isProcessing = true
        return true
    }

    class Factory(
        private val application: Application,
        private val repository: Repository,
        private val gitRepository: IGitRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return BranchesViewModel(application, repository, gitRepository) as T
        }
    }
}
