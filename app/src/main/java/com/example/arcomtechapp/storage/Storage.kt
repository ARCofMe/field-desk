package com.example.arcomtechapp.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class Storage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("arcom_prefs", Context.MODE_PRIVATE)

    data class SettingsSnapshot(
        val apiKey: String?,
        val baseUrl: String?,
        val isAuthenticated: Boolean,
        val autoCompressPhotos: Boolean,
        val technicianName: String?,
        val technicianId: String?,
        val debugMode: Boolean,
        val lastSyncEpochMillis: Long,
        val notesDraft: String?,
        val themeMode: Int
    )

    fun saveApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, key)
        }.apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun saveBaseUrl(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, url)
        }.apply()
    }

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun setAuthenticated(isAuthenticated: Boolean) {
        prefs.edit().putBoolean(KEY_IS_AUTH, isAuthenticated).apply()
    }

    fun isAuthenticated(): Boolean = prefs.getBoolean(KEY_IS_AUTH, false)

    fun setAutoCompressPhotos(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COMPRESS, enabled).apply()
    }

    fun shouldAutoCompressPhotos(): Boolean =
        prefs.getBoolean(KEY_AUTO_COMPRESS, true)

    fun saveTechnician(name: String?, id: String?) {
        prefs.edit().apply {
            if (name.isNullOrBlank()) remove(KEY_TECH_NAME) else putString(KEY_TECH_NAME, name)
            if (id.isNullOrBlank()) remove(KEY_TECH_ID) else putString(KEY_TECH_ID, id)
        }.apply()
    }

    fun getTechnicianName(): String? = prefs.getString(KEY_TECH_NAME, null)

    fun getTechnicianId(): String? = prefs.getString(KEY_TECH_ID, null)

    fun setDebugMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun isDebugMode(): Boolean = prefs.getBoolean(KEY_DEBUG_MODE, false)

    fun setNotesDraft(draft: String) {
        prefs.edit().putString(KEY_NOTES_DRAFT, draft).apply()
    }

    fun getNotesDraft(): String? = prefs.getString(KEY_NOTES_DRAFT, null)

    fun clearNotesDraft() {
        prefs.edit().remove(KEY_NOTES_DRAFT).apply()
    }

    fun markSyncNow(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun lastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun getSnapshot(): SettingsSnapshot = SettingsSnapshot(
        apiKey = getApiKey(),
        baseUrl = getBaseUrl(),
        isAuthenticated = isAuthenticated(),
        autoCompressPhotos = shouldAutoCompressPhotos(),
        technicianName = getTechnicianName(),
        technicianId = getTechnicianId(),
        debugMode = isDebugMode(),
        lastSyncEpochMillis = lastSyncTime(),
        notesDraft = getNotesDraft(),
        themeMode = getThemeMode()
    )

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_BASE_URL = "base_url"
        const val KEY_IS_AUTH = "is_authenticated"
        const val KEY_AUTO_COMPRESS = "auto_compress"
        const val KEY_TECH_NAME = "tech_name"
        const val KEY_TECH_ID = "tech_id"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_NOTES_DRAFT = "notes_draft"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
