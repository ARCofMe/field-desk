package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.Assignment
import com.example.arcomtechapp.data.models.Job

class BlueFolderFieldOpsRepository(
    private val legacy: BlueFolderRepository = BlueFolderRepository()
) : FieldOpsRepository {

    override fun testPython(): String = legacy.testPython()

    override fun getAssignmentsForUser(techId: String): List<Assignment> =
        legacy.getAssignmentsForUser(techId)

    override fun checkConnection(baseUrl: String?, apiKey: String?): String =
        legacy.checkConnection(baseUrl, apiKey)

    override fun getTodayJobs(baseUrl: String?, apiKey: String?, techId: String?): List<Job> =
        legacy.getTodayJobs(baseUrl, apiKey, techId)

    override fun getAllJobs(
        baseUrl: String?,
        apiKey: String?,
        techId: String?,
        startDate: String?,
        endDate: String?,
        dateRangeType: String
    ): List<Job> = legacy.getAllJobs(baseUrl, apiKey, techId, startDate, endDate, dateRangeType)

    override fun submitJobNote(baseUrl: String?, apiKey: String?, jobId: String, note: String): TechnicianActionResult =
        TechnicianActionResult(false, "Direct BlueFolder note submit is not wired in the app yet")

    override fun updateJobStatus(baseUrl: String?, apiKey: String?, jobId: String, statusKey: String): TechnicianActionResult =
        TechnicianActionResult(false, "Direct BlueFolder status updates are not wired in the app yet")

    override fun createPartsRequest(baseUrl: String?, apiKey: String?, jobId: String, details: String): TechnicianActionResult =
        TechnicianActionResult(false, "Direct BlueFolder parts handoff is not wired in the app yet")

    override fun preparePhotoUpload(baseUrl: String?, apiKey: String?, jobId: String, photoLabel: String): TechnicianActionResult =
        TechnicianActionResult(false, "Direct BlueFolder photo upload handoff is not wired in the app yet")
}
