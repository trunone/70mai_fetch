package com.mai70.downloader.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("70mai_downloader_prefs", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, "192.168.0.1") ?: "192.168.0.1"
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 80)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var autoDeleteAfterDownload: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DELETE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DELETE, value).apply()

    var periodicCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERIODIC_CHECK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERIODIC_CHECK_ENABLED, value).apply()

    var pollIntervalMinutes: Long
        get() = prefs.getLong(KEY_POLL_INTERVAL_MINUTES, 15L)
        set(value) = prefs.edit().putLong(KEY_POLL_INTERVAL_MINUTES, value).apply()

    var downloadDirPath: String
        get() = prefs.getString(KEY_DOWNLOAD_DIR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_DIR, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    companion object {
        private const val KEY_HOST = "key_host"
        private const val KEY_PORT = "key_port"
        private const val KEY_AUTO_DELETE = "key_auto_delete"
        private const val KEY_PERIODIC_CHECK_ENABLED = "key_periodic_check_enabled"
        private const val KEY_POLL_INTERVAL_MINUTES = "key_poll_interval_minutes"
        private const val KEY_DOWNLOAD_DIR = "key_download_dir"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
    }
}
