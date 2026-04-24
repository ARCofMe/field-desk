package com.example.arcomtechapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import com.example.arcomtechapp.storage.Storage
import org.json.JSONArray
import org.json.JSONObject

class WebFieldDeskBridge(
    private val context: Context,
    private val storage: Storage,
    private val onCapturePhotoRequest: ((label: String, srId: String) -> Unit)? = null
) {

    @JavascriptInterface
    fun getHostConfig(): String {
        val snapshot = storage.getSnapshot()
        return JSONObject()
            .put("apiBase", snapshot.opsHubBaseUrl ?: "")
            .put("apiToken", snapshot.opsHubApiKey ?: "")
            .put("technicianSubject", subjectFor(snapshot.technicianId))
            .put("themeMode", when (snapshot.themeMode) {
                1 -> "light"
                2 -> "dark"
                else -> "dark"
            })
            .put("preferWebFieldDesk", snapshot.preferWebFieldDesk)
            .put("fieldDeskWebUrl", snapshot.fieldDeskWebUrl ?: "")
            .put("opsHubUrl", snapshot.opsHubUrl ?: snapshot.opsHubBaseUrl ?: "")
            .put("routeDeskUrl", snapshot.routeDeskUrl ?: "")
            .put("partsDeskUrl", snapshot.partsDeskUrl ?: "")
            .toString()
    }

    @JavascriptInterface
    fun getOfflineQueueState(): String {
        val items = storage.getOfflineActions()
        val payload = JSONArray()
        items.forEach { item ->
            payload.put(
                JSONObject()
                    .put("id", item.id)
                    .put("actionType", item.actionType)
                    .put("createdAtEpochMillis", item.createdAtEpochMillis)
                    .put("payload", item.payload)
            )
        }
        return JSONObject()
            .put("count", items.size)
            .put("available", true)
            .put("items", payload)
            .toString()
    }

    @JavascriptInterface
    fun enqueueOfflineAction(actionType: String, payload: String): String {
        val record = storage.enqueueOfflineAction(actionType.trim().ifBlank { "unknown" }, payload)
        return JSONObject()
            .put("success", true)
            .put("id", record.id)
            .put("message", "Queued ${record.actionType} for later sync.")
            .toString()
    }

    @JavascriptInterface
    fun removeOfflineAction(id: String): String {
        val removed = storage.removeOfflineAction(id.trim())
        return JSONObject()
            .put("success", removed)
            .put("message", if (removed) "Removed queued action." else "Queued action was not found.")
            .toString()
    }

    @JavascriptInterface
    fun clearOfflineActions(): String {
        storage.clearOfflineActions()
        return JSONObject()
            .put("success", true)
            .put("message", "Cleared offline action queue.")
            .toString()
    }

    @JavascriptInterface
    fun capturePhoto(label: String, srId: String): String {
        val cleanLabel = label.trim().ifBlank { "unlabeled" }
        val cleanSrId = srId.trim()
        onCapturePhotoRequest?.invoke(cleanLabel, cleanSrId)
        return JSONObject()
            .put("success", true)
            .put("bridgeStatus", "camera")
            .put("message", "Opening native camera capture.")
            .toString()
    }

    @JavascriptInterface
    fun requestPushRegistration(): String =
        JSONObject()
            .put("success", false)
            .put("bridgeStatus", "scaffolded")
            .put("message", "Push registration bridge scaffold is in place, but provider wiring is not configured yet.")
            .toString()

    @JavascriptInterface
    fun getDeviceLocation(): String =
        JSONObject()
            .put("success", false)
            .put("bridgeStatus", "scaffolded")
            .put("message", "Location bridge scaffold is in place, but live location permission and capture are not wired yet.")
            .toString()

    @JavascriptInterface
    fun openExternalNavigation(address: String): String {
        val cleaned = address.trim()
        if (cleaned.isBlank()) {
            return JSONObject().put("success", false).put("message", "An address is required.").toString()
        }
        val uri = Uri.parse("google.navigation:q=${Uri.encode(cleaned)}")
        return launchIntent(uri, "Opened external navigation.", "Could not open external navigation.")
    }

    @JavascriptInterface
    fun openExternalUrl(url: String): String {
        val cleaned = url.trim()
        val parsed = runCatching { Uri.parse(cleaned) }.getOrNull()
        if (cleaned.isBlank() || parsed?.scheme !in setOf("http", "https")) {
            return JSONObject().put("success", false).put("message", "A valid http/https URL is required.").toString()
        }
        return launchIntent(parsed ?: return JSONObject().put("success", false).put("message", "A valid http/https URL is required.").toString(), "Opened external workspace.", "Could not open external workspace.")
    }

    private fun launchIntent(uri: Uri, successMessage: String, failureMessage: String): String =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().put("success", true).put("message", successMessage).toString()
        }.getOrElse {
            JSONObject().put("success", false).put("message", failureMessage).toString()
        }

    private fun subjectFor(rawId: String?): String =
        rawId?.trim()?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("bf:")) it else "bf:$it" } ?: ""
}
