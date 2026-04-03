package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoRecord
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OpsHubFieldOpsRepository : FieldOpsRepository {

    override fun testPython(): String = "Ops Hub backend selected"

    override fun getAssignmentsForUser(techId: String): List<Assignment> {
        val response = request("GET", null, null, "/tech/assignments?technician_id=${encode(techId)}")
        val items = JSONArray(response)
        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                add(Assignment(id = obj.optString("id"), title = obj.optString("title")))
            }
        }
    }

    override fun checkConnection(baseUrl: String?, apiKey: String?): String {
        return try {
            request("GET", baseUrl, apiKey, "/health")
            "Ops Hub reachable"
        } catch (e: Exception) {
            "Ops Hub connection failed: ${e.message}"
        }
    }

    override fun getTodayJobs(baseUrl: String?, apiKey: String?, techId: String?): List<Job> =
        fetchJobs(baseUrl, apiKey, buildPath("/tech/me/today", techId = techId))

    override fun getAllJobs(
        baseUrl: String?,
        apiKey: String?,
        techId: String?,
        startDate: String?,
        endDate: String?,
        dateRangeType: String
    ): List<Job> = fetchJobs(
        baseUrl,
        apiKey,
        buildPath("/tech/jobs", techId, startDate, endDate, dateRangeType)
    )

    override fun submitJobNote(baseUrl: String?, apiKey: String?, jobId: String, note: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/notes", JSONObject().put("note", note))

    override fun updateJobStatus(baseUrl: String?, apiKey: String?, jobId: String, statusKey: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/status", JSONObject().put("status", statusKey))

    override fun createPartsRequest(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/parts", JSONObject().put("details", details))

    override fun preparePhotoUpload(baseUrl: String?, apiKey: String?, jobId: String, photoLabel: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/photos/prepare", JSONObject().put("label", photoLabel))

    override fun uploadJobPhoto(baseUrl: String?, apiKey: String?, jobId: String, request: PhotoUploadRequest): TechnicianActionResult =
        postAction(
            baseUrl,
            apiKey,
            "/tech/jobs/$jobId/photos/upload",
            JSONObject()
                .put("label", request.label)
                .put("filename", request.filename)
                .put("contentType", request.contentType)
                .put("dataBase64", Base64.encodeToString(request.data, Base64.NO_WRAP))
        )

    override fun getJob(baseUrl: String?, apiKey: String?, jobId: String): Job? {
        return try {
            parseJob(JSONObject(request("GET", baseUrl, apiKey, "/tech/jobs/$jobId")))
        } catch (_: Exception) {
            null
        }
    }

    override fun getJobPartsCase(baseUrl: String?, apiKey: String?, jobId: String): JobPartsCase? {
        return try {
            val obj = JSONObject(request("GET", baseUrl, apiKey, "/tech/jobs/$jobId/parts"))
            JobPartsCase(
                reference = obj.optString("reference"),
                stage = obj.optString("stage"),
                stageLabel = obj.optString("stageLabel"),
                status = obj.optString("status"),
                openRequestIds = buildList {
                    val items = obj.optJSONArray("openRequestIds") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        if (!items.isNull(i)) add(items.optInt(i))
                    }
                },
                assignedPartsUserId = obj.optInt("assignedPartsUserId").takeIf { it > 0 },
                blocker = obj.optString("blocker").takeIf { it.isNotBlank() },
                latestStatusText = obj.optString("latestStatusText").takeIf { it.isNotBlank() },
                latestIssueText = obj.optString("latestIssueText").takeIf { it.isNotBlank() },
                nextAction = obj.optString("nextAction").takeIf { it.isNotBlank() },
                updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun getJobTimeline(baseUrl: String?, apiKey: String?, jobId: String): List<JobTimelineEntry> {
        return try {
            val items = JSONArray(request("GET", baseUrl, apiKey, "/tech/jobs/$jobId/timeline"))
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    add(
                        JobTimelineEntry(
                            occurredAt = obj.optString("occurredAt"),
                            source = obj.optString("source"),
                            eventType = obj.optString("eventType"),
                            summary = obj.optString("summary"),
                            details = obj.optString("details").takeIf { it.isNotBlank() },
                            actorLabel = obj.optString("actorLabel").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getJobPhotoStatus(baseUrl: String?, apiKey: String?, jobId: String): JobPhotoStatus? {
        return try {
            val obj = JSONObject(request("GET", baseUrl, apiKey, "/tech/jobs/$jobId/photos"))
            JobPhotoStatus(
                enabled = obj.optBoolean("enabled"),
                srId = obj.opt("srId")?.toString().orEmpty(),
                mailboxStatus = obj.optString("mailboxStatus"),
                message = obj.optString("message"),
                totalPhotos = obj.optInt("totalPhotos"),
                foundTags = jsonArrayStrings(obj.optJSONArray("foundTags")),
                missingTags = jsonArrayStrings(obj.optJSONArray("missingTags")),
                records = buildList {
                    val items = obj.optJSONArray("records") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val record = items.getJSONObject(i)
                        add(
                            JobPhotoRecord(
                                subject = record.optString("subject"),
                                fromEmail = record.optString("fromEmail"),
                                receivedAt = record.optString("receivedAt"),
                                attachmentCount = record.optInt("attachmentCount"),
                                attachmentNames = jsonArrayStrings(record.optJSONArray("attachmentNames"))
                            )
                        )
                    }
                },
                shouldNotify = obj.optBoolean("shouldNotify"),
                reason = obj.optString("reason")
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun logCallAhead(baseUrl: String?, apiKey: String?, jobId: String, minutes: Int): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/call_ahead", JSONObject().put("minutes", minutes))

    override fun reportQuoteNeeded(
        baseUrl: String?,
        apiKey: String?,
        jobId: String,
        details: String,
        subtype: String
    ): TechnicianActionResult = postAction(
        baseUrl,
        apiKey,
        "/tech/jobs/$jobId/quote_needed",
        JSONObject().put("details", details).put("subtype", subtype)
    )

    override fun reportReschedule(baseUrl: String?, apiKey: String?, jobId: String, reason: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/reschedule", JSONObject().put("reason", reason))

    override fun evaluatePhotoCompliance(
        baseUrl: String?,
        apiKey: String?,
        jobId: String,
        statusOverride: String?,
        sendNotice: Boolean
    ): TechnicianActionResult {
        val payload = JSONObject().put("sendNotice", sendNotice)
        statusOverride?.takeIf { it.isNotBlank() }?.let { payload.put("statusOverride", it) }
        return postAction(baseUrl, apiKey, "/tech/jobs/$jobId/photo_compliance", payload)
    }

    override fun logWorkStart(baseUrl: String?, apiKey: String?, jobId: String, details: String?): TechnicianActionResult {
        val payload = JSONObject()
        details?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("details", it) }
        return postAction(baseUrl, apiKey, "/tech/jobs/$jobId/start", payload)
    }

    override fun reportNoAnswer(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/no_answer", JSONObject().put("details", details))

    override fun reportNotHome(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/not_home", JSONObject().put("details", details))

    override fun reportUnableToComplete(baseUrl: String?, apiKey: String?, jobId: String, reason: String): TechnicianActionResult =
        postAction(baseUrl, apiKey, "/tech/jobs/$jobId/unable_to_complete", JSONObject().put("reason", reason))

    private fun buildPath(
        basePath: String,
        techId: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        dateRangeType: String? = null
    ): String {
        val query = buildList {
            techId?.takeIf { it.isNotBlank() }?.let { add("technician_id=${encode(it)}") }
            startDate?.takeIf { it.isNotBlank() }?.let { add("start=${encode(it)}") }
            endDate?.takeIf { it.isNotBlank() }?.let { add("end=${encode(it)}") }
            dateRangeType?.takeIf { it.isNotBlank() }?.let { add("type=${encode(it)}") }
        }.joinToString("&")
        return if (query.isBlank()) basePath else "$basePath?$query"
    }

    private fun fetchJobs(baseUrl: String?, apiKey: String?, path: String): List<Job> {
        val body = request("GET", baseUrl, apiKey, path)
        val items = JSONArray(body)
        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                add(
                    parseJob(obj)
                )
            }
        }
    }

    private fun parseJob(obj: JSONObject): Job {
        return Job(
            id = obj.optString("id"),
            address = obj.optString("address"),
            appointmentWindow = obj.optString("appointmentWindow"),
            customerName = obj.optString("customerName"),
            customerPhone = obj.optString("customerPhone"),
            status = obj.optString("status"),
            distanceMiles = obj.takeIf { !it.isNull("distanceMiles") }?.optDouble("distanceMiles"),
            equipment = obj.takeIf { it.has("equipment") && !it.isNull("equipment") }?.optString("equipment"),
            partsStage = obj.optString("partsStage").takeIf { it.isNotBlank() },
            nextAction = obj.optString("nextAction").takeIf { it.isNotBlank() }
        )
    }

    private fun jsonArrayStrings(items: JSONArray?): List<String> = buildList {
        val safeItems = items ?: JSONArray()
        for (i in 0 until safeItems.length()) {
            val value = safeItems.optString(i)
            if (value.isNotBlank()) add(value)
        }
    }

    private fun postAction(
        baseUrl: String?,
        apiKey: String?,
        path: String,
        payload: JSONObject
    ): TechnicianActionResult {
        return try {
            val response = request("POST", baseUrl, apiKey, path, payload.toString())
            val json = runCatching { JSONObject(response) }.getOrNull()
            TechnicianActionResult(
                success = json?.optBoolean("success") ?: true,
                message = json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Action submitted to Ops Hub"
            )
        } catch (e: Exception) {
            TechnicianActionResult(false, "Ops Hub request failed: ${e.message}")
        }
    }

    private fun request(
        method: String,
        baseUrl: String?,
        apiKey: String?,
        path: String,
        body: String? = null
    ): String {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            ?: throw IllegalStateException("Configure Ops Hub base URL first")
        val token = apiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Configure Ops Hub API key first")
        val connection = (URL("$normalizedBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        body?.let {
            OutputStreamWriter(connection.outputStream).use { writer -> writer.write(it) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}: ${response.take(200)}")
        }
        return response
    }

    private fun normalizeBaseUrl(baseUrl: String?): String? {
        if (baseUrl.isNullOrBlank()) return null
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
