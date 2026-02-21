package com.gitflow.android.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.data.repository.IGitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gitRepository: IGitRepository = GitRepository(application)

    fun getGitRepository(): IGitRepository = gitRepository

    val repositoriesFlow: Flow<List<Repository>> = gitRepository.getRepositoriesFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedRepository = MutableStateFlow<Repository?>(null)
    val selectedRepository: StateFlow<Repository?> = _selectedRepository.asStateFlow()

    private val _selectedGraphPreset = MutableStateFlow("Default")
    val selectedGraphPreset: StateFlow<String> = _selectedGraphPreset.asStateFlow()

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectRepository(repo: Repository) {
        _selectedRepository.value = repo
        _selectedTab.value = 1 // Switch to graph view
    }

    fun changeGraphPreset(preset: String) {
        _selectedGraphPreset.value = preset
    }

    fun updateSelectedRepositoryIfChanged(repositories: List<Repository>) {
        val selected = _selectedRepository.value ?: return
        val updated = repositories.find { it.id == selected.id }
        if (updated != null && updated != selected) {
            _selectedRepository.value = updated
        }
    }
}
