package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.python.PythonBridge

open class BlueFolderRepository(
    private val bridge: PythonBridge = PythonBridge()
) {

    open fun testPython(): String {
        return bridge.hello()
    }

    open fun getAssignmentsForUser(techId: String): List<Assignment> {
        val raw = bridge.getAssignmentsForUser(techId)
        return listOf(
            Assignment(
                id = "dummy-1",
                title = raw
            )
        )
    }

    open fun checkConnection(baseUrl: String?, apiKey: String?): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        if (normalizedUrl == null || apiKey.isNullOrBlank()) {
            return "Missing base URL or API key"
        }
        return bridge.checkConnection(normalizedUrl, apiKey)
    }

    open fun getTodayJobs(baseUrl: String?, apiKey: String?, techId: String?): List<Job> {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        if (normalizedUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            throw IllegalStateException("Configure base URL and API key first")
        }
        return bridge.getJobs(normalizedUrl, apiKey, techId, null, null, "scheduled")
    }

    open fun getAllJobs(
        baseUrl: String?,
        apiKey: String?,
        techId: String?,
        startDate: String?,
        endDate: String?,
        dateRangeType: String = "scheduled"
    ): List<Job> {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        if (normalizedUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            throw IllegalStateException("Configure base URL and API key first")
        }
        return bridge.getJobs(normalizedUrl, apiKey, techId, startDate, endDate, dateRangeType)
    }

    private fun normalizeBaseUrl(baseUrl: String?): String? {
        if (baseUrl.isNullOrBlank()) return null
        val trimmed = baseUrl.trim()
        return if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
    }
}
