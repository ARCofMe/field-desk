package com.example.arcomtechapp.python

import com.chaquo.python.Python
import com.example.arcomtechapp.data.models.Job
import org.json.JSONArray

class PythonBridge {

    private val py: Python by lazy { Python.getInstance() }
    private val module by lazy { py.getModule("bluefolder_client") }

    fun hello(): String {
        return module.callAttr("hello_from_python").toString()
    }

    fun getAssignmentsForUser(techId: String): String {
        return module.callAttr("get_assignments_for_user", techId).toString()
    }

    fun checkConnection(baseUrl: String, apiKey: String): String {
        return module.callAttr("check_api_connection", baseUrl, apiKey).toString()
    }

    fun getJobs(
        baseUrl: String,
        apiKey: String,
        techId: String?,
        startDate: String?,
        endDate: String?,
        dateRangeType: String?
    ): List<Job> {
        val json = module.callAttr(
            "get_jobs_json",
            baseUrl,
            apiKey,
            techId ?: "",
            startDate,
            endDate,
            dateRangeType ?: "scheduled"
        ).toString()
        val arr = JSONArray(json)
        val jobs = mutableListOf<Job>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val distanceRaw = if (obj.isNull("distanceMiles")) null else obj.optDouble("distanceMiles")
            val distanceMiles = distanceRaw?.let { if (it.isNaN()) null else it }
            jobs.add(
                Job(
                    id = obj.optString("id"),
                    address = obj.optString("address"),
                    appointmentWindow = obj.optString("appointmentWindow"),
                    customerName = obj.optString("customerName"),
                    customerPhone = obj.optString("customerPhone"),
                    status = obj.optString("status"),
                    distanceMiles = distanceMiles,
                    equipment = obj.optString("equipment", null)
                )
            )
        }
        return jobs
    }
}
