package com.gitflow.android.data.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

class AppSettingsManager(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return preferences.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }

    fun isCommitFilesTreeViewEnabled(): Boolean {
        return preferences.getBoolean(KEY_COMMIT_FILES_TREE_VIEW, false)
    }

    fun setCommitFilesTreeViewEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_COMMIT_FILES_TREE_VIEW, enabled).apply()
    }

    fun getPreviewExtensions(): Set<String> {
        val stored = preferences.getStringSet(KEY_PREVIEW_EXTENSIONS, null)
        return if (stored.isNullOrEmpty()) {
            LinkedHashSet(DEFAULT_PREVIEW_EXTENSIONS)
        } else {
            stored.mapNotNull { sanitizeToken(it) }.toCollection(LinkedHashSet())
        }
    }

    fun setPreviewExtensions(extensions: Collection<String>) {
        val sanitized = extensions.mapNotNull { sanitizeToken(it) }
        preferences.edit()
            .putStringSet(KEY_PREVIEW_EXTENSIONS, LinkedHashSet(sanitized))
            .apply()
    }

    fun getPreviewFileNames(): Set<String> {
        val stored = preferences.getStringSet(KEY_PREVIEW_FILE_NAMES, null)
        return if (stored.isNullOrEmpty()) {
            LinkedHashSet(DEFAULT_PREVIEW_FILE_NAMES)
        } else {
            stored.mapNotNull { sanitizeToken(it, allowDots = true) }.toCollection(LinkedHashSet())
        }
    }

    fun setPreviewFileNames(fileNames: Collection<String>) {
        val sanitized = fileNames.mapNotNull { sanitizeToken(it, allowDots = true) }
        preferences.edit()
            .putStringSet(KEY_PREVIEW_FILE_NAMES, LinkedHashSet(sanitized))
            .apply()
    }

    fun addPreviewExtension(extension: String) {
        val normalized = sanitizeToken(extension) ?: return
        val current = getPreviewExtensions().toMutableSet()
        if (current.none { it.equals(normalized, ignoreCase = true) }) {
            current.add(normalized)
            setPreviewExtensions(current)
        }
    }

    fun removePreviewExtension(extension: String) {
        val normalized = sanitizeToken(extension) ?: return
        val current = getPreviewExtensions().toMutableSet()
        if (current.removeIf { it.equals(normalized, ignoreCase = true) }) {
            setPreviewExtensions(current)
        }
    }

    fun addPreviewFileName(name: String) {
        val normalized = sanitizeToken(name, allowDots = true) ?: return
        val current = getPreviewFileNames().toMutableSet()
        if (current.none { it.equals(normalized, ignoreCase = true) }) {
            current.add(normalized)
            setPreviewFileNames(current)
        }
    }

    fun removePreviewFileName(name: String) {
        val normalized = sanitizeToken(name, allowDots = true) ?: return
        val current = getPreviewFileNames().toMutableSet()
        if (current.removeIf { it.equals(normalized, ignoreCase = true) }) {
            setPreviewFileNames(current)
        }
    }

    fun registerPreviewSettingsListener(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PREVIEW_EXTENSIONS || key == KEY_PREVIEW_FILE_NAMES) {
                onChange()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterPreviewSettingsListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getLanguage(): String {
        return preferences.getString(KEY_LANGUAGE, LANGUAGE_RUSSIAN) ?: LANGUAGE_RUSSIAN
    }

    fun setLanguage(language: String) {
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getCustomStorageUri(): String? {
        return preferences.getString(KEY_CUSTOM_STORAGE_URI, null)
    }

    fun setCustomStorageUri(uri: String?) {
        preferences.edit().putString(KEY_CUSTOM_STORAGE_URI, uri).apply()
    }

    fun getRepositoriesBaseDir(context: Context): File {
        val uriString = getCustomStorageUri()
        if (uriString != null) {
            val resolved = resolveTreeUriToFile(context, Uri.parse(uriString))
            if (resolved != null && resolved.isDirectory && resolved.canWrite()) {
                ensureNomediaFile(resolved)
                return resolved
            }
        }
        val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, "repositories")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    private fun resolveTreeUriToFile(context: Context, treeUri: Uri): File? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val basePath = if (split[0] == "primary") {
                Environment.getExternalStorageDirectory()
            } else {
                File("/storage/${split[0]}")
            }
            File(basePath, split.getOrElse(1) { "" })
        } catch (e: Exception) {
            null
        }
    }

    fun ensureNomediaFile(dir: File) {
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try {
                dir.mkdirs()
                nomedia.createNewFile()
            } catch (_: Exception) {
            }
        }
    }

    private fun sanitizeToken(value: String?, allowDots: Boolean = false): String? {
        if (value.isNullOrBlank()) return null
        var token = value.trim()
        if (!allowDots) {
            token = token.removePrefix(".")
        }
        if (token.isEmpty()) return null
        return token.lowercase(Locale.ROOT)
    }

    companion object {
        private const val PREF_NAME = "app_settings"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads_enabled"
        private const val KEY_COMMIT_FILES_TREE_VIEW = "commit_files_tree_view_enabled"
        internal const val KEY_PREVIEW_EXTENSIONS = "preview_extensions"
        internal const val KEY_PREVIEW_FILE_NAMES = "preview_file_names"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_CUSTOM_STORAGE_URI = "custom_storage_uri"

        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_RUSSIAN = "ru"

        val DEFAULT_PREVIEW_EXTENSIONS: LinkedHashSet<String> = linkedSetOf(
            "c",
            "cpp",
            "h",
            "hpp",
            "cs",
            "go",
            "js",
            "jsx",
            "ts",
            "tsx",
            "py",
            "rb",
            "rs",
            "swift",
            "php",
            "html",
            "css",
            "scss",
            "sass",
            "less",
            "lua",
            "pl",
            "sh",
            "zsh",
            "bash",
            "fish",
            "ps1",
            "r",
            "dart",
            "ktm",
            "kt",
            "kts",
            "java",
            "xml",
            "json",
            "md",
            "gradle",
            "gradle.kts",
            "properties",
            "yml",
            "yaml",
            "txt",
            "gitignore",
            "gitattributes",
            "editorconfig",
            "cfg",
            "ini",
            "sh",
            "bat",
            "csv",
            "env"
        )

        val DEFAULT_PREVIEW_FILE_NAMES: LinkedHashSet<String> = linkedSetOf(
            "readme",
            "license",
            "changelog",
            "copying",
            "notice"
        )
    }
}
