package com.gitflow.android.ui.screens.main

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitUser
import com.gitflow.android.data.settings.AppSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val githubUser: GitUser? = null,
    val gitlabUser: GitUser? = null,
    val wifiOnlyDownloads: Boolean = false,
    val currentLanguage: String = AppSettingsManager.LANGUAGE_SYSTEM,
    val customStoragePath: String? = null,
    val repositoriesBaseDir: String? = null,
    val previewExtensions: List<String> = emptyList(),
    val previewFileNames: List<String> = emptyList(),
    val showPreviewSettings: Boolean = false,
    // One-shot flag: Composable calls activity.recreate() then onRecreateConsumed()
    val recreateActivity: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)
    private val settingsManager = AppSettingsManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val previewListener: SharedPreferences.OnSharedPreferenceChangeListener

    init {
        loadInitialState()
        previewListener = settingsManager.registerPreviewSettingsListener {
            _uiState.update {
                it.copy(
                    previewExtensions = settingsManager.getPreviewExtensions().toList(),
                    previewFileNames = settingsManager.getPreviewFileNames().toList()
                )
            }
        }
    }

    private fun loadInitialState() {
        val app = getApplication<Application>()
        val customUri = settingsManager.getCustomStorageUri()
        _uiState.value = SettingsUiState(
            githubUser = authManager.getCurrentUser(GitProvider.GITHUB),
            gitlabUser = authManager.getCurrentUser(GitProvider.GITLAB),
            wifiOnlyDownloads = settingsManager.isWifiOnlyDownloadsEnabled(),
            currentLanguage = settingsManager.getLanguage(),
            customStoragePath = customUri,
            repositoriesBaseDir = if (customUri != null) {
                settingsManager.getRepositoriesBaseDir(app).absolutePath
            } else null,
            previewExtensions = settingsManager.getPreviewExtensions().toList(),
            previewFileNames = settingsManager.getPreviewFileNames().toList()
        )
    }

    /** Re-read auth users — called on screen resume in case the auth screen changed them. */
    fun refreshUsers() {
        _uiState.update {
            it.copy(
                githubUser = authManager.getCurrentUser(GitProvider.GITHUB),
                gitlabUser = authManager.getCurrentUser(GitProvider.GITLAB)
            )
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        settingsManager.setWifiOnlyDownloadsEnabled(enabled)
        _uiState.update { it.copy(wifiOnlyDownloads = enabled) }
    }

    fun setLanguage(language: String) {
        val previous = _uiState.value.currentLanguage
        settingsManager.setLanguage(language)
        _uiState.update {
            it.copy(
                currentLanguage = language,
                recreateActivity = language != previous
            )
        }
    }

    fun onRecreateConsumed() {
        _uiState.update { it.copy(recreateActivity = false) }
    }

    fun onStorageFolderSelected(uri: Uri) {
        val app = getApplication<Application>()
        settingsManager.setCustomStorageUri(uri.toString())
        val baseDir = settingsManager.getRepositoriesBaseDir(app)
        settingsManager.ensureNomediaFile(baseDir)
        _uiState.update {
            it.copy(
                customStoragePath = uri.toString(),
                repositoriesBaseDir = baseDir.absolutePath
            )
        }
    }

    fun resetStoragePath() {
        settingsManager.setCustomStorageUri(null)
        _uiState.update { it.copy(customStoragePath = null, repositoriesBaseDir = null) }
    }

    fun setShowPreviewSettings(show: Boolean) {
        _uiState.update { it.copy(showPreviewSettings = show) }
    }

    fun addPreviewExtension(value: String) {
        settingsManager.addPreviewExtension(value)
        _uiState.update { it.copy(previewExtensions = settingsManager.getPreviewExtensions().toList()) }
    }

    fun removePreviewExtension(value: String) {
        settingsManager.removePreviewExtension(value)
        _uiState.update { it.copy(previewExtensions = settingsManager.getPreviewExtensions().toList()) }
    }

    fun addPreviewFileName(value: String) {
        settingsManager.addPreviewFileName(value)
        _uiState.update { it.copy(previewFileNames = settingsManager.getPreviewFileNames().toList()) }
    }

    fun removePreviewFileName(value: String) {
        settingsManager.removePreviewFileName(value)
        _uiState.update { it.copy(previewFileNames = settingsManager.getPreviewFileNames().toList()) }
    }

    override fun onCleared() {
        super.onCleared()
        settingsManager.unregisterPreviewSettingsListener(previewListener)
    }
}
