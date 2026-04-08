package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobCloseoutDraft
import com.example.arcomtechapp.data.models.JobCloseoutPreview
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry

data class TechnicianActionResult(
    val success: Boolean,
    val message: String
)

data class PhotoUploadRequest(
    val filename: String,
    val contentType: String?,
    val data: ByteArray,
    val label: String
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
    fun uploadJobPhoto(baseUrl: String?, apiKey: String?, jobId: String, request: PhotoUploadRequest): TechnicianActionResult =
        TechnicianActionResult(false, "Direct photo attachment is not available for this backend")
    fun getJob(baseUrl: String?, apiKey: String?, jobId: String): Job? = null
    fun getJobPartsCase(baseUrl: String?, apiKey: String?, jobId: String): JobPartsCase? = null
    fun getJobTimeline(baseUrl: String?, apiKey: String?, jobId: String): List<JobTimelineEntry> = emptyList()
    fun getJobPhotoStatus(baseUrl: String?, apiKey: String?, jobId: String): JobPhotoStatus? = null
    fun logCallAhead(baseUrl: String?, apiKey: String?, jobId: String, minutes: Int = 30): TechnicianActionResult =
        TechnicianActionResult(false, "Call-ahead flow is not available for this backend")
    fun reportQuoteNeeded(
        baseUrl: String?,
        apiKey: String?,
        jobId: String,
        details: String,
        subtype: String = "customer"
    ): TechnicianActionResult = TechnicianActionResult(false, "Quote-needed flow is not available for this backend")
    fun reportReschedule(baseUrl: String?, apiKey: String?, jobId: String, reason: String): TechnicianActionResult =
        TechnicianActionResult(false, "Reschedule flow is not available for this backend")
    fun evaluatePhotoCompliance(
        baseUrl: String?,
        apiKey: String?,
        jobId: String,
        statusOverride: String? = null,
        sendNotice: Boolean = false
    ): TechnicianActionResult = TechnicianActionResult(false, "Photo compliance evaluation is not available for this backend")
    fun logWorkStart(baseUrl: String?, apiKey: String?, jobId: String, details: String? = null): TechnicianActionResult =
        TechnicianActionResult(false, "Work-start flow is not available for this backend")
    fun reportNoAnswer(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        TechnicianActionResult(false, "No-answer flow is not available for this backend")
    fun reportNotHome(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        TechnicianActionResult(false, "Not-home flow is not available for this backend")
    fun reportUnableToComplete(baseUrl: String?, apiKey: String?, jobId: String, reason: String): TechnicianActionResult =
        TechnicianActionResult(false, "Unable-to-complete flow is not available for this backend")
    fun previewCloseout(baseUrl: String?, apiKey: String?, jobId: String, draft: JobCloseoutDraft): JobCloseoutPreview? = null
    fun submitCloseout(baseUrl: String?, apiKey: String?, jobId: String, draft: JobCloseoutDraft): TechnicianActionResult =
        TechnicianActionResult(false, "Closeout labor flow is not available for this backend")
}
