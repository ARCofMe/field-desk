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
        val backendMode: BackendMode,
        val opsHubBaseUrl: String?,
        val opsHubApiKey: String?,
        val isAuthenticated: Boolean,
        val autoCompressPhotos: Boolean,
        val technicianName: String?,
        val technicianId: String?,
        val debugMode: Boolean,
        val lastSyncEpochMillis: Long,
        val notesDraft: String?,
        val themeMode: Int,
        val routeOrigin: String?,
        val routeDestination: String?,
        val lastJobAction: String?,
        val lastJobActionJobId: String?
    )

    data class LocalJobProgress(
        val noteDraft: String?,
        val photoCount: Int,
        val lastPhotoLabel: String?
    )

    enum class BackendMode {
        BLUEFOLDER_DIRECT,
        OPS_HUB
    }

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

    fun saveOpsHubBaseUrl(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_OPS_HUB_BASE_URL) else putString(KEY_OPS_HUB_BASE_URL, url)
        }.apply()
    }

    fun getOpsHubBaseUrl(): String? = prefs.getString(KEY_OPS_HUB_BASE_URL, null)

    fun saveOpsHubApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_OPS_HUB_API_KEY) else putString(KEY_OPS_HUB_API_KEY, key)
        }.apply()
    }

    fun getOpsHubApiKey(): String? = prefs.getString(KEY_OPS_HUB_API_KEY, null)

    fun setBackendMode(mode: BackendMode) {
        prefs.edit().putString(KEY_BACKEND_MODE, mode.name).apply()
    }

    fun getBackendMode(): BackendMode =
        runCatching { BackendMode.valueOf(prefs.getString(KEY_BACKEND_MODE, BackendMode.BLUEFOLDER_DIRECT.name)!!) }
            .getOrDefault(BackendMode.BLUEFOLDER_DIRECT)

    fun getActiveBaseUrl(): String? =
        when (getBackendMode()) {
            BackendMode.OPS_HUB -> getOpsHubBaseUrl()
            BackendMode.BLUEFOLDER_DIRECT -> getBaseUrl()
        }

    fun getActiveApiKey(): String? =
        when (getBackendMode()) {
            BackendMode.OPS_HUB -> getOpsHubApiKey()
            BackendMode.BLUEFOLDER_DIRECT -> getApiKey()
        }

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

    fun setJobNotesDraft(jobId: String?, draft: String?) {
        if (jobId.isNullOrBlank()) {
            if (draft.isNullOrBlank()) clearNotesDraft() else setNotesDraft(draft)
            return
        }
        prefs.edit().apply {
            if (draft.isNullOrBlank()) remove(jobNotesKey(jobId)) else putString(jobNotesKey(jobId), draft)
        }.apply()
    }

    fun getJobNotesDraft(jobId: String?): String? {
        if (jobId.isNullOrBlank()) return getNotesDraft()
        return prefs.getString(jobNotesKey(jobId), null)
    }

    fun clearJobNotesDraft(jobId: String?) {
        if (jobId.isNullOrBlank()) {
            clearNotesDraft()
            return
        }
        prefs.edit().remove(jobNotesKey(jobId)).apply()
    }

    fun markSyncNow(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun lastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun saveRouteOrigin(origin: String?) {
        prefs.edit().apply {
            if (origin.isNullOrBlank()) remove(KEY_ROUTE_ORIGIN) else putString(KEY_ROUTE_ORIGIN, origin)
        }.apply()
    }

    fun getRouteOrigin(): String? = prefs.getString(KEY_ROUTE_ORIGIN, null)

    fun saveRouteDestination(dest: String?) {
        prefs.edit().apply {
            if (dest.isNullOrBlank()) remove(KEY_ROUTE_DESTINATION) else putString(KEY_ROUTE_DESTINATION, dest)
        }.apply()
    }

    fun getRouteDestination(): String? = prefs.getString(KEY_ROUTE_DESTINATION, null)

    fun saveLastJobAction(jobId: String?, action: String?) {
        prefs.edit().apply {
            if (jobId.isNullOrBlank()) remove(KEY_LAST_JOB_ACTION_JOB_ID) else putString(KEY_LAST_JOB_ACTION_JOB_ID, jobId)
            if (action.isNullOrBlank()) remove(KEY_LAST_JOB_ACTION) else putString(KEY_LAST_JOB_ACTION, action)
        }.apply()
    }

    fun getLastJobAction(): String? = prefs.getString(KEY_LAST_JOB_ACTION, null)

    fun getLastJobActionJobId(): String? = prefs.getString(KEY_LAST_JOB_ACTION_JOB_ID, null)

    fun recordJobPhotoCapture(jobId: String?, label: String) {
        if (jobId.isNullOrBlank()) return
        val count = getJobPhotoCount(jobId) + 1
        prefs.edit()
            .putInt(jobPhotoCountKey(jobId), count)
            .putString(jobPhotoLabelKey(jobId), label)
            .apply()
    }

    fun getJobPhotoCount(jobId: String?): Int {
        if (jobId.isNullOrBlank()) return 0
        return prefs.getInt(jobPhotoCountKey(jobId), 0)
    }

    fun getJobLastPhotoLabel(jobId: String?): String? {
        if (jobId.isNullOrBlank()) return null
        return prefs.getString(jobPhotoLabelKey(jobId), null)
    }

    fun getLocalJobProgress(jobId: String?): LocalJobProgress = LocalJobProgress(
        noteDraft = getJobNotesDraft(jobId),
        photoCount = getJobPhotoCount(jobId),
        lastPhotoLabel = getJobLastPhotoLabel(jobId)
    )

    fun getSnapshot(): SettingsSnapshot = SettingsSnapshot(
        apiKey = getApiKey(),
        baseUrl = getBaseUrl(),
        backendMode = getBackendMode(),
        opsHubBaseUrl = getOpsHubBaseUrl(),
        opsHubApiKey = getOpsHubApiKey(),
        isAuthenticated = isAuthenticated(),
        autoCompressPhotos = shouldAutoCompressPhotos(),
        technicianName = getTechnicianName(),
        technicianId = getTechnicianId(),
        debugMode = isDebugMode(),
        lastSyncEpochMillis = lastSyncTime(),
        notesDraft = getNotesDraft(),
        themeMode = getThemeMode(),
        routeOrigin = getRouteOrigin(),
        routeDestination = getRouteDestination(),
        lastJobAction = getLastJobAction(),
        lastJobActionJobId = getLastJobActionJobId()
    )

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_BASE_URL = "base_url"
        const val KEY_BACKEND_MODE = "backend_mode"
        const val KEY_OPS_HUB_BASE_URL = "ops_hub_base_url"
        const val KEY_OPS_HUB_API_KEY = "ops_hub_api_key"
        const val KEY_IS_AUTH = "is_authenticated"
        const val KEY_AUTO_COMPRESS = "auto_compress"
        const val KEY_TECH_NAME = "tech_name"
        const val KEY_TECH_ID = "tech_id"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_NOTES_DRAFT = "notes_draft"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ROUTE_ORIGIN = "route_origin"
        const val KEY_ROUTE_DESTINATION = "route_destination"
        const val KEY_LAST_JOB_ACTION = "last_job_action"
        const val KEY_LAST_JOB_ACTION_JOB_ID = "last_job_action_job_id"
    }

    private fun jobNotesKey(jobId: String): String = "job_notes_$jobId"

    private fun jobPhotoCountKey(jobId: String): String = "job_photo_count_$jobId"

    private fun jobPhotoLabelKey(jobId: String): String = "job_photo_label_$jobId"
}
