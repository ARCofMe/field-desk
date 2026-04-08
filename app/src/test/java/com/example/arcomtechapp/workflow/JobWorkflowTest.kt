package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobStatusMeta
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

    @Test
    fun summarize_prefers_ops_hub_next_action_and_quote_flow() {
        val summary = JobWorkflow.summarize(
            Job(
                id = "42",
                address = "123 Main St",
                appointmentWindow = "PM",
                customerName = "Jane",
                customerPhone = "555-0000",
                status = "Quote Needed",
                equipment = "Dryer",
                nextAction = "Office needs landlord approval before scheduling return visit."
            )
        )

        assertEquals("Quote follow-up", summary.headline)
        assertEquals("Office needs landlord approval before scheduling return visit.", summary.nextStep)
        assertTrue(summary.quickActions.any { it.key == "quote_needed" })
        assertTrue(summary.checklist.any { it.label == "Ops Hub next step" && it.done })
    }

    @Test
    fun summarize_uses_status_meta_for_waiting_customer_flow() {
        val summary = JobWorkflow.summarize(
            Job(
                id = "7",
                address = "123 Main St",
                appointmentWindow = "PM",
                customerName = "Jane",
                customerPhone = "555-0000",
                status = "Waiting on CX",
                statusMeta = JobStatusMeta(
                    raw = "Waiting on CX",
                    category = "waiting_customer",
                    categoryLabel = "Waiting Customer",
                    isWaitingCustomer = true,
                    isOpen = true,
                    knownInTenantCatalog = true
                ),
                equipment = "Dryer"
            )
        )

        assertEquals("Customer follow-up", summary.headline)
        assertTrue(summary.quickActions.any { it.key == "call" })
        assertTrue(summary.nextStep.contains("customer-side follow-up"))
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
