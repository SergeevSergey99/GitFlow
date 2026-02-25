package com.gitflow.android.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    application: Application,
    private val gitRepository: IGitRepository,
    private val settingsManager: AppSettingsManager
) : AndroidViewModel(application) {

    fun getGitRepository(): IGitRepository = gitRepository

    val repositoriesFlow: Flow<List<Repository>> = gitRepository.getRepositoriesFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

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

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectRepository(repo: Repository) {
        _selectedRepository.value = repo
        _selectedTab.value = 1
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

    fun updateSelectedRepositoryIfChanged(repositories: List<Repository>) {
        val selected = _selectedRepository.value
        if (selected == null) {
            // Ждём непустого списка — при первой эмиссии список может быть пустым
            if (repositories.isEmpty()) return
            val lastId = settingsManager.getLastRepositoryId() ?: return
            val repo = repositories.find { it.id == lastId }
            if (repo != null && isValidGitRepo(repo.path)) {
                _selectedRepository.value = repo
                _selectedTab.value = 1
            } else {
                // Репозиторий удалён, недоступен или повреждён — сбрасываем
                settingsManager.setLastRepositoryId(null)
            }
            _isRestoringSession.value = false
            return
        }
        val updated = repositories.find { it.id == selected.id }
        when {
            updated == null || !isValidGitRepo(updated.path) -> {
                _selectedRepository.value = null
                settingsManager.setLastRepositoryId(null)
                if (_selectedTab.value != 0) _selectedTab.value = 0
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
