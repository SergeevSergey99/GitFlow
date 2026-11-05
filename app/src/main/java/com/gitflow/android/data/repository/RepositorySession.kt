package com.gitflow.android.data.repository

import com.gitflow.android.data.models.ConflictInfo
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class RepositorySnapshot(
    val repository: Repository,
    val conflictInfo: ConflictInfo,
    val pendingPullCommits: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class RepositorySession(
    private val gitRepository: IGitRepository,
    private val settingsManager: AppSettingsManager,
    scope: CoroutineScope
) {
    private val selectedId = MutableStateFlow(settingsManager.getLastRepositoryId())

    private val _isRestoring = MutableStateFlow(selectedId.value != null)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    val snapshot: StateFlow<RepositorySnapshot?> = selectedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else snapshotsOf(id) }
        .onEach { _isRestoring.value = false }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun select(repository: Repository) {
        settingsManager.setLastRepositoryId(repository.id)
        selectedId.value = repository.id
    }

    fun clearSelection() {
        settingsManager.setLastRepositoryId(null)
        selectedId.value = null
    }

    private fun snapshotsOf(id: String): Flow<RepositorySnapshot?> =
        gitRepository.getRepositoriesFlow()
            .map { repositories -> repositories.find { it.id == id } }
            .distinctUntilChanged()
            .flatMapLatest { repository ->
                if (repository == null || !isGitRepository(repository.path)) {
                    clearSelection()
                    flowOf(null)
                } else {
                    gitRepository.repositoryChanges
                        .filter { it == id }
                        .onStart { emit(id) }
                        .mapLatest { loadSnapshot(repository) }
                }
            }

    private suspend fun loadSnapshot(repository: Repository): RepositorySnapshot {
        val conflictInfo = gitRepository.getConflictInfo(repository)
        val pendingPullCommits = gitRepository.getBranches(repository)
            .firstOrNull { it.isLocal && it.name == repository.currentBranch }
            ?.behind ?: 0
        return RepositorySnapshot(repository, conflictInfo, pendingPullCommits)
    }

    private fun isGitRepository(path: String): Boolean {
        val directory = File(path)
        if (!directory.isDirectory) return false
        if (File(directory, ".git").exists()) return true
        return File(directory, "HEAD").exists() && File(directory, "objects").isDirectory
    }
}
