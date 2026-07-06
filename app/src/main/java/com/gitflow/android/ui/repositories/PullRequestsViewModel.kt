package com.gitflow.android.ui.repositories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitPullRequest
import com.gitflow.android.data.models.GitRemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PullRequestsUiState(
    val pullRequests: List<GitPullRequest> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/** Loads open pull/merge requests for one remote repository (all providers). */
class PullRequestsViewModel(
    private val authManager: AuthManager,
    private val repository: GitRemoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PullRequestsUiState())
    val uiState: StateFlow<PullRequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val prs = authManager.getPullRequests(repository)
                _uiState.update { it.copy(pullRequests = prs, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
