package com.gitflow.android.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.ConflictInfo
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class MainTab { REPOSITORIES, GRAPH, CHANGES, SETTINGS }

class MainViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val settingsManager: AppSettingsManager
) : AndroidViewModel(application) {

    fun getGitRepository(): IGitRepository = gitRepository

    val repositoriesFlow: Flow<List<Repository>> = gitRepository.getRepositoriesFlow()

    private val _selectedTab = MutableStateFlow(MainTab.REPOSITORIES)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedRepository = MutableStateFlow<Repository?>(null)
    val selectedRepository: StateFlow<Repository?> = _selectedRepository.asStateFlow()

    private val _selectedGraphPreset = MutableStateFlow(settingsManager.getGraphPreset())
    val selectedGraphPreset: StateFlow<String> = _selectedGraphPreset.asStateFlow()

    private val _selectedColorTheme = MutableStateFlow(settingsManager.getColorTheme())
    val selectedColorTheme: StateFlow<String> = _selectedColorTheme.asStateFlow()

    private val _selectedDarkMode = MutableStateFlow(settingsManager.getDarkMode())
    val selectedDarkMode: StateFlow<String> = _selectedDarkMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // true пока не завершена первая попытка восстановить сессию
    private val _isRestoringSession = MutableStateFlow(settingsManager.getLastRepositoryId() != null)
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()

    // In-progress merge/rebase for the selected repo — drives the conflict header + popup.
    private val _conflictInfo = MutableStateFlow(ConflictInfo())
    val conflictInfo: StateFlow<ConflictInfo> = _conflictInfo.asStateFlow()

    // Bumped after a header-initiated abort/continue so the Changes tab reloads its state.
    private val _operationSignal = MutableStateFlow(0)
    val operationSignal: StateFlow<Int> = _operationSignal.asStateFlow()

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectRepository(repo: Repository) {
        _selectedRepository.value = repo
        _selectedTab.value = MainTab.GRAPH
        settingsManager.setLastRepositoryId(repo.id)
    }

    fun changeGraphPreset(preset: String) {
        _selectedGraphPreset.value = preset
        settingsManager.setGraphPreset(preset)
    }

    fun changeColorTheme(theme: String) {
        _selectedColorTheme.value = theme
        settingsManager.setColorTheme(theme)
    }

    fun changeDarkMode(mode: String) {
        _selectedDarkMode.value = mode
        settingsManager.setDarkMode(mode)
    }

    fun refreshRepositories() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val repos = gitRepository.getRepositories()
                repos.forEach { repo ->
                    val updated = gitRepository.refreshRepository(repo)
                    if (updated != null) {
                        gitRepository.updateRepository(updated)
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Re-reads the merge/rebase context (source/target, commits, conflicts) for the selected repo. */
    fun refreshOperationState() {
        val repo = _selectedRepository.value
        if (repo == null) {
            _conflictInfo.value = ConflictInfo()
            return
        }
        viewModelScope.launch {
            _conflictInfo.value = gitRepository.getConflictInfo(repo)
        }
    }

    /** Aborts the in-progress merge or rebase (from the conflict header). */
    fun abortMergeOrRebase() {
        val repo = _selectedRepository.value ?: return
        viewModelScope.launch {
            when (_conflictInfo.value.operation) {
                RepoOperationState.REBASING -> gitRepository.rebaseAbort(repo)
                else -> gitRepository.abortMerge(repo)
            }
            _operationSignal.value += 1
            refreshRepositories()
            refreshOperationState()
        }
    }

    /** Continues a conflict-paused rebase (from the conflict header) once files are resolved. */
    fun continueRebase() {
        val repo = _selectedRepository.value ?: return
        viewModelScope.launch {
            gitRepository.rebaseContinue(repo)
            _operationSignal.value += 1
            refreshRepositories()
            refreshOperationState()
        }
    }

    fun updateSelectedRepositoryIfChanged(repositories: List<Repository>) {
        val selected = _selectedRepository.value
        if (selected == null) {
            // Ждём непустого списка — при первой эмиссии список может быть пустым
            if (repositories.isEmpty()) return
            val lastId = settingsManager.getLastRepositoryId() ?: return
            val repo = repositories.find { it.id == lastId }
            if (repo != null && isValidGitRepo(repo.path)) {
                _selectedRepository.value = repo
                _selectedTab.value = MainTab.GRAPH
            } else {
                // Репозиторий удалён, недоступен или повреждён — сбрасываем
                settingsManager.setLastRepositoryId(null)
            }
            _isRestoringSession.value = false
            return
        }
        // Пустой список — временная эмиссия при выходе/возврате из экрана, игнорируем
        if (repositories.isEmpty()) return
        val updated = repositories.find { it.id == selected.id }
        when {
            updated == null || !isValidGitRepo(updated.path) -> {
                _selectedRepository.value = null
                settingsManager.setLastRepositoryId(null)
                if (_selectedTab.value != MainTab.REPOSITORIES) _selectedTab.value = MainTab.REPOSITORIES
            }
            updated != selected -> {
                _selectedRepository.value = updated
            }
        }
    }

    private fun isValidGitRepo(path: String): Boolean {
        val dir = File(path)
        if (!dir.isDirectory) return false
        // Обычный репозиторий (.git папка или файл для worktree)
        if (File(dir, ".git").exists()) return true
        // Bare-репозиторий (HEAD лежит прямо в корне)
        if (File(dir, "HEAD").exists() && File(dir, "objects").isDirectory) return true
        return false
    }
}
