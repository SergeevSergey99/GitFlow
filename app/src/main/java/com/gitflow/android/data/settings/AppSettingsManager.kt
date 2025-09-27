package com.gitflow.android.data.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettingsManager(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return preferences.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }

    companion object {
        private const val PREF_NAME = "app_settings"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads_enabled"
    }
}
