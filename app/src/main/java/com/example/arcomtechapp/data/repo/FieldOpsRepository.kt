package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job

data class TechnicianActionResult(
    val success: Boolean,
    val message: String
)

interface FieldOpsRepository {
    fun testPython(): String
    fun getAssignmentsForUser(techId: String): List<Assignment>
    fun checkConnection(baseUrl: String?, apiKey: String?): String
    fun getTodayJobs(baseUrl: String?, apiKey: String?, techId: String?): List<Job>
    fun getAllJobs(
        baseUrl: String?,
        apiKey: String?,
        techId: String?,
        startDate: String?,
        endDate: String?,
        dateRangeType: String = "scheduled"
    ): List<Job>
    fun submitJobNote(baseUrl: String?, apiKey: String?, jobId: String, note: String): TechnicianActionResult
    fun updateJobStatus(baseUrl: String?, apiKey: String?, jobId: String, statusKey: String): TechnicianActionResult
    fun createPartsRequest(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult
    fun preparePhotoUpload(baseUrl: String?, apiKey: String?, jobId: String, photoLabel: String): TechnicianActionResult
}
