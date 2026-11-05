package com.gitflow.android.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.repository.RepositorySession
import com.gitflow.android.data.repository.RepositorySnapshot
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class MainTab { REPOSITORIES, GRAPH, CHANGES, SETTINGS }

class MainViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val settingsManager: AppSettingsManager,
    private val session: RepositorySession
) : AndroidViewModel(application) {

    fun getGitRepository(): IGitRepository = gitRepository

    val repositoriesFlow: Flow<List<Repository>> = gitRepository.getRepositoriesFlow()

    val snapshot: StateFlow<RepositorySnapshot?> = session.snapshot
    val isRestoringSession: StateFlow<Boolean> = session.isRestoring

    private val _selectedTab = MutableStateFlow(
        if (session.isRestoring.value || session.snapshot.value != null) MainTab.GRAPH else MainTab.REPOSITORIES
    )
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedGraphPreset = MutableStateFlow(settingsManager.getGraphPreset())
    val selectedGraphPreset: StateFlow<String> = _selectedGraphPreset.asStateFlow()

    val selectedColorTheme: StateFlow<String> = settingsManager.colorThemeFlow
    val selectedDarkMode: StateFlow<String> = settingsManager.darkModeFlow

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // The selection can vanish on its own (repository deleted, folder gone); the
        // repo-specific tabs have nothing to show then.
        viewModelScope.launch {
            combine(session.snapshot, session.isRestoring) { snapshot, restoring -> snapshot == null && !restoring }
                .collect { selectionLost -> if (selectionLost) _selectedTab.value = MainTab.REPOSITORIES }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectRepository(repo: Repository) {
        session.select(repo)
        _selectedTab.value = MainTab.GRAPH
    }

    fun changeGraphPreset(preset: String) {
        _selectedGraphPreset.value = preset
        settingsManager.setGraphPreset(preset)
    }

    fun changeColorTheme(theme: String) = settingsManager.setColorTheme(theme)

    fun changeDarkMode(mode: String) = settingsManager.setDarkMode(mode)

    fun refreshRepositories() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                gitRepository.getRepositories().forEach { repository -> gitRepository.refreshRepository(repository) }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Aborts the in-progress merge or rebase (from the conflict header). */
    fun abortMergeOrRebase() {
        val current = session.snapshot.value ?: return
        viewModelScope.launch {
            when (current.conflictInfo.operation) {
                RepoOperationState.REBASING -> gitRepository.rebaseAbort(current.repository)
                else -> gitRepository.abortMerge(current.repository)
            }
        }
    }

    /** Continues a conflict-paused rebase (from the conflict header) once files are resolved. */
    fun continueRebase() {
        val current = session.snapshot.value ?: return
        viewModelScope.launch {
            gitRepository.rebaseContinue(current.repository)
        }
    }
}
