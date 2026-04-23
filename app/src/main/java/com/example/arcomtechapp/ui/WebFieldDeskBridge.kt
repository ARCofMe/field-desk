package com.example.arcomtechapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import com.example.arcomtechapp.storage.Storage

class WebFieldDeskBridge(
    private val context: Context,
    private val storage: Storage
) {

    @JavascriptInterface
    fun getHostConfig(): String {
        val snapshot = storage.getSnapshot()
        return jsonObject(
            "apiBase" to (snapshot.opsHubBaseUrl ?: ""),
            "apiToken" to (snapshot.opsHubApiKey ?: ""),
            "technicianSubject" to subjectFor(snapshot.technicianId),
            "themeMode" to when (snapshot.themeMode) {
                1 -> "light"
                2 -> "dark"
                else -> "dark"
            },
            "preferWebFieldDesk" to snapshot.preferWebFieldDesk,
            "fieldDeskWebUrl" to (snapshot.fieldDeskWebUrl ?: "")
        )
    }

    @JavascriptInterface
    fun getOfflineQueueState(): String {
        val items = storage.getOfflineActions()
        return jsonObject(
            "count" to items.size,
            "available" to true,
            "items" to items.joinToString(prefix = "[", postfix = "]") { item ->
                jsonObject(
                    "id" to item.id,
                    "actionType" to item.actionType,
                    "createdAtEpochMillis" to item.createdAtEpochMillis,
                    "payload" to item.payload
                )
            }
        )
    }

    @JavascriptInterface
    fun enqueueOfflineAction(actionType: String, payload: String): String {
        val record = storage.enqueueOfflineAction(actionType.trim().ifBlank { "unknown" }, payload)
        return jsonObject(
            "success" to true,
            "id" to record.id,
            "message" to "Queued ${record.actionType} for later sync."
        )
    }

    @JavascriptInterface
    fun capturePhoto(label: String, srId: String): String {
        storage.enqueueOfflineAction(
            "capture_photo_request",
            "label=${label.trim()}&srId=${srId.trim()}"
        )
        return jsonObject(
            "success" to false,
            "bridgeStatus" to "scaffolded",
            "message" to "Native photo capture bridge scaffold is in place, but camera capture is not wired yet."
        )
    }

    @JavascriptInterface
    fun requestPushRegistration(): String {
        return jsonObject(
            "success" to false,
            "bridgeStatus" to "scaffolded",
            "message" to "Push registration bridge scaffold is in place, but provider wiring is not configured yet."
        )
    }

    @JavascriptInterface
    fun getDeviceLocation(): String {
        return jsonObject(
            "success" to false,
            "bridgeStatus" to "scaffolded",
            "message" to "Location bridge scaffold is in place, but live location permission and capture are not wired yet."
        )
    }

    @JavascriptInterface
    fun openExternalNavigation(address: String): String {
        val cleaned = address.trim()
        if (cleaned.isBlank()) {
            return jsonObject("success" to false, "message" to "An address is required.")
        }
        val uri = Uri.parse("google.navigation:q=${Uri.encode(cleaned)}")
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            jsonObject("success" to true, "message" to "Opened external navigation.")
        }.getOrElse {
            jsonObject("success" to false, "message" to "Could not open external navigation.")
        }
    }

    private fun subjectFor(rawId: String?): String =
        rawId?.trim()?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("bf:")) it else "bf:$it" } ?: ""

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encodedValue = when (value) {
                null -> "null"
                is Boolean, is Number -> value.toString()
                else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
            }
            "\"$key\":$encodedValue"
        }
}
