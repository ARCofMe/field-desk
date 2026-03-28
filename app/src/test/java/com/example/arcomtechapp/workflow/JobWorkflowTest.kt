package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobWorkflowTest {
    @Test
    fun activeJob_prefers_onsite_over_pending_and_completed() {
        val jobs = listOf(
            job("1", "Completed"),
            job("2", "Pending"),
            job("3", "Started")
        )

        val active = JobWorkflow.activeJob(jobs)

        assertEquals("3", active?.id)
    }

    @Test
    fun summarize_flags_missing_job_inputs() {
        val summary = JobWorkflow.summarize(
            Job(
                id = "1",
                address = "Address not provided",
                appointmentWindow = "AM",
                customerName = "Jane",
                customerPhone = "",
                status = "Pending",
                equipment = null,
            )
        )

        assertEquals("Ready to start", summary.headline)
        assertTrue(summary.checklist.any { it.label == "Address ready" && !it.done })
        assertTrue(summary.quickActions.any { it.key == "enroute" })
    }

    private fun job(id: String, status: String): Job {
        return Job(
            id = id,
            address = "123 Main St",
            appointmentWindow = "AM",
            customerName = "Customer $id",
            customerPhone = "555-000$id",
            status = status,
            equipment = "WM"
        )
    }
}
