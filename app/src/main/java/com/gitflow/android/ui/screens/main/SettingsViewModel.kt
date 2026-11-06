package com.gitflow.android.ui.screens.main

import android.app.Application
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectedAccount(
    val user: GitUser,
    val instanceUrl: String? = null
)

data class SettingsUiState(
    val connectedAccounts: List<ConnectedAccount> = emptyList(),
    val localAuthorName: String = "",
    val localAuthorEmail: String = "",
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

class SettingsViewModel(
    application: Application,
    private val authManager: AuthManager,
    private val settingsManager: AppSettingsManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        viewModelScope.launch {
            combine(settingsManager.previewExtensionsFlow, settingsManager.previewFileNamesFlow) { extensions, fileNames ->
                extensions.toList() to fileNames.toList()
            }.collect { (extensions, fileNames) ->
                _uiState.update { it.copy(previewExtensions = extensions, previewFileNames = fileNames) }
            }
        }
    }

    private fun buildConnectedAccounts(): List<ConnectedAccount> =
        GitProvider.entries.mapNotNull { provider ->
            if (!authManager.isAuthenticated(provider)) return@mapNotNull null
            val user = authManager.getCurrentUser(provider) ?: return@mapNotNull null
            ConnectedAccount(user = user, instanceUrl = authManager.getInstanceUrl(provider))
        }

    private fun loadInitialState() {
        val app = getApplication<Application>()
        val customUri = settingsManager.getCustomStorageUri()
        _uiState.value = SettingsUiState(
            connectedAccounts = buildConnectedAccounts(),
            localAuthorName = settingsManager.getLocalAuthorName(),
            localAuthorEmail = settingsManager.getLocalAuthorEmail(),
            wifiOnlyDownloads = settingsManager.isWifiOnlyDownloadsEnabled(),
            currentLanguage = settingsManager.getLanguage(),
            customStoragePath = customUri,
            repositoriesBaseDir = if (customUri != null) {
                settingsManager.getRepositoriesBaseDir(app).absolutePath
            } else null
        )
    }

    /** Re-read auth users — called on screen resume in case the auth screen changed them. */
    fun refreshUsers() {
        _uiState.update {
            it.copy(
                connectedAccounts = buildConnectedAccounts(),
                localAuthorName = settingsManager.getLocalAuthorName(),
                localAuthorEmail = settingsManager.getLocalAuthorEmail()
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

    fun addPreviewExtension(value: String) = settingsManager.addPreviewExtension(value)

    fun removePreviewExtension(value: String) = settingsManager.removePreviewExtension(value)

    fun addPreviewFileName(value: String) = settingsManager.addPreviewFileName(value)

    fun removePreviewFileName(value: String) = settingsManager.removePreviewFileName(value)
}
