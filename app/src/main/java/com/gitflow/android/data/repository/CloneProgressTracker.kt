package com.gitflow.android.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AUTO_DISMISS_DELAY_MS = 12_000L

enum class CloneStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class CloneTaskState(
    val key: String,
    val repoName: String,
    val repoFullName: String,
    val progress: CloneProgress = CloneProgress(),
    val status: CloneStatus = CloneStatus.RUNNING,
    val errorMessage: String? = null,
    val approximateSize: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object CloneProgressTracker {
    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeClones = MutableStateFlow<Map<String, CloneTaskState>>(emptyMap())
    val activeClones: StateFlow<Map<String, CloneTaskState>> = _activeClones.asStateFlow()

    fun registerClone(key: String, repoName: String, repoFullName: String, approximateSize: Long? = null) {
        _activeClones.update { clones ->
            val existing = clones[key]
            val updated = (existing ?: CloneTaskState(
                key = key,
                repoName = repoName,
                repoFullName = repoFullName,
                approximateSize = approximateSize
            )).copy(
                repoName = repoName,
                repoFullName = repoFullName,
                approximateSize = approximateSize,
                status = CloneStatus.RUNNING,
                completedAt = null
            )
            clones + (key to updated)
        }
    }

    fun updateProgress(key: String, progress: CloneProgress) {
        _activeClones.update { clones ->
            val existing = clones[key] ?: return@update clones
            clones + (key to existing.copy(progress = progress))
        }
    }

    fun markCompleted(key: String) {
        updateStatus(key, CloneStatus.SUCCESS)
    }

    fun markFailed(key: String, errorMessage: String?) {
        updateStatus(key, CloneStatus.FAILED, errorMessage)
    }

    fun markCancelled(key: String, errorMessage: String? = null) {
        updateStatus(key, CloneStatus.CANCELLED, errorMessage)
    }

    fun dismiss(key: String) {
        _activeClones.update { clones ->
            clones - key
        }
    }

    private fun updateStatus(key: String, status: CloneStatus, errorMessage: String? = null) {
        var updatedState: CloneTaskState? = null
        _activeClones.update { clones ->
            val existing = clones[key] ?: return@update clones
            val updated = existing.copy(
                status = status,
                errorMessage = errorMessage,
                completedAt = System.currentTimeMillis()
            )
            updatedState = updated
            clones + (key to updated)
        }
        if (updatedState != null && updatedState?.status != CloneStatus.RUNNING) {
            scheduleAutoDismiss(key)
        }
    }

    private fun scheduleAutoDismiss(key: String) {
        trackerScope.launch {
            delay(AUTO_DISMISS_DELAY_MS)
            _activeClones.update { clones ->
                val state = clones[key]
                if (state != null && state.status != CloneStatus.RUNNING) {
                    clones - key
                } else {
                    clones
                }
            }
        }
    }
}
