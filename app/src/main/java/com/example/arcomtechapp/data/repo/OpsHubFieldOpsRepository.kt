package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job
import org.json.JSONArray
import org.json.JSONObject
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
                    Job(
                        id = obj.optString("id"),
                        address = obj.optString("address"),
                        appointmentWindow = obj.optString("appointmentWindow"),
                        customerName = obj.optString("customerName"),
                        customerPhone = obj.optString("customerPhone"),
                        status = obj.optString("status"),
                        distanceMiles = obj.takeIf { !it.isNull("distanceMiles") }?.optDouble("distanceMiles"),
                        equipment = obj.takeIf { it.has("equipment") && !it.isNull("equipment") }?.optString("equipment")
                    )
                )
            }
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
