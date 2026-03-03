package com.gitflow.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gitflow.android.data.models.Repository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import timber.log.Timber

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "repositories")

class RepositoryDataStore(private val context: Context) {

    private val repositoriesKey = stringPreferencesKey("repositories_list")
    private val repositoriesCorruptedBackupKey = stringPreferencesKey("repositories_list_corrupted_backup")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val repositories: Flow<List<Repository>> = context.dataStore.data.map { preferences ->
        val repositoriesJson = preferences[repositoriesKey] ?: "[]"
        decodeRepositoriesOrNull(repositoriesJson) ?: run {
            Timber.e("Failed to decode repositories DataStore payload; falling back to empty list")
            emptyList()
        }
    }

    suspend fun saveRepositories(repositories: List<Repository>) {
        context.dataStore.edit { preferences ->
            val repositoriesJson = json.encodeToString(repositories)
            preferences[repositoriesKey] = repositoriesJson
        }
    }

    suspend fun addRepository(repository: Repository) {
        context.dataStore.edit { preferences ->
            val currentRepositoriesJson = preferences[repositoriesKey] ?: "[]"
            val currentRepositories = decodeRepositoriesOrNull(currentRepositoriesJson) ?: run {
                backupCorruptedPayload(preferences, currentRepositoriesJson)
                emptyList()
            }

            val updatedRepositories = currentRepositories + repository
            val updatedJson = json.encodeToString(updatedRepositories)
            preferences[repositoriesKey] = updatedJson
        }
    }

    suspend fun removeRepository(repositoryId: String) {
        context.dataStore.edit { preferences ->
            val currentRepositoriesJson = preferences[repositoriesKey] ?: "[]"
            val currentRepositories = decodeRepositoriesOrNull(currentRepositoriesJson) ?: run {
                backupCorruptedPayload(preferences, currentRepositoriesJson)
                emptyList()
            }

            val updatedRepositories = currentRepositories.filter { it.id != repositoryId }
            val updatedJson = json.encodeToString(updatedRepositories)
            preferences[repositoriesKey] = updatedJson
        }
    }

    suspend fun updateRepository(repository: Repository) {
        context.dataStore.edit { preferences ->
            val currentRepositoriesJson = preferences[repositoriesKey] ?: "[]"
            val currentRepositories = decodeRepositoriesOrNull(currentRepositoriesJson) ?: run {
                backupCorruptedPayload(preferences, currentRepositoriesJson)
                emptyList()
            }

            val updatedRepositories = currentRepositories.map { repo ->
                if (repo.id == repository.id) repository else repo
            }
            val updatedJson = json.encodeToString(updatedRepositories)
            preferences[repositoriesKey] = updatedJson
        }
    }

    private fun decodeRepositoriesOrNull(rawJson: String): List<Repository>? {
        return try {
            json.decodeFromString<List<Repository>>(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun backupCorruptedPayload(preferences: androidx.datastore.preferences.core.MutablePreferences, rawJson: String) {
        if (rawJson.isBlank() || rawJson == "[]") return
        if (preferences[repositoriesCorruptedBackupKey].isNullOrBlank()) {
            preferences[repositoriesCorruptedBackupKey] = rawJson
        }
        Timber.e("RepositoryDataStore payload was corrupted. Backed up and resetting to empty list.")
    }
}
