package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobCloseoutDraft
import com.example.arcomtechapp.data.models.JobTimelineEntry
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.TechnicianActionResult
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.getOrAwaitValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JobDetailViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private fun session(): FieldDeskSession = FieldDeskSession(
        backendMode = Storage.BackendMode.OPS_HUB,
        technicianId = "t1",
        technicianName = "Tech",
        baseUrl = "https://ops.example.test",
        apiKey = "token",
        configComplete = true
    )

    private fun job(status: String = "Scheduled"): Job = Job(
        id = "100",
        address = "123 Main",
        appointmentWindow = "8-10",
        customerName = "Acme",
        customerPhone = "555-0100",
        status = status
    )

    @Test
    fun `reportNoAnswer emits action event with details and refreshes context`() {
        val repo = mockk<FieldOpsRepository>()
        val seedJob = job()
        every { repo.reportNoAnswer("https://ops.example.test", "token", "100", "No answer at door") } returns
            TechnicianActionResult(true, "No-answer reported")
        every { repo.getJob("https://ops.example.test", "token", "100") } returns seedJob.copy(status = "No answer")
        every { repo.getJobPartsCase(any(), any(), any()) } returns null
        every { repo.getJobPhotoStatus(any(), any(), any()) } returns null
        every { repo.getJobTimeline(any(), any(), any()) } returns listOf(
            JobTimelineEntry(
                occurredAt = "2026-04-07T10:00:00Z",
                source = "ops_hub.tech",
                eventType = "no_answer",
                summary = "Technician reported no answer."
            )
        )

        val viewModel = JobDetailViewModel(repo) { session() }
        viewModel.reportNoAnswer(seedJob, "No answer at door")

        val action = viewModel.actionEvents.getOrAwaitValue(time = 5)
        val context = viewModel.context.getOrAwaitValue(time = 5)

        assertEquals("no_answer", action.actionKey)
        assertEquals("No answer at door", action.details)
        assertEquals(true, action.result.success)
        assertEquals("No answer", context.job.status)
        assertEquals(1, context.timeline.size)
        verify(exactly = 1) { repo.reportNoAnswer("https://ops.example.test", "token", "100", "No answer at door") }
        verify(exactly = 1) { repo.getJob("https://ops.example.test", "token", "100") }
    }

    @Test
    fun `call ahead fills default success message when backend returns blank`() {
        val repo = mockk<FieldOpsRepository>()
        val seedJob = job()
        every { repo.logCallAhead("https://ops.example.test", "token", "100", 30) } returns
            TechnicianActionResult(true, "")
        every { repo.getJob("https://ops.example.test", "token", "100") } returns seedJob
        every { repo.getJobPartsCase(any(), any(), any()) } returns null
        every { repo.getJobPhotoStatus(any(), any(), any()) } returns null
        every { repo.getJobTimeline(any(), any(), any()) } returns emptyList()

        val viewModel = JobDetailViewModel(repo) { session() }
        viewModel.logCallAhead(seedJob)

        val action = viewModel.actionEvents.getOrAwaitValue(time = 5)

        assertEquals("call_ahead", action.actionKey)
        assertEquals("Call-ahead logged", action.result.message)
    }

    @Test
    fun `submitCloseout emits closeout action event`() {
        val repo = mockk<FieldOpsRepository>()
        val seedJob = job()
        val draft = JobCloseoutDraft(
            laborCode = "oow_hourly",
            workPerformed = "Replaced failed inlet valve and verified fill.",
            durationMinutes = 90,
            signedBy = "Pat Customer",
            customerApproved = true
        )
        every { repo.submitCloseout("https://ops.example.test", "token", "100", draft) } returns
            TechnicianActionResult(true, "Closeout submitted")
        every { repo.getJob("https://ops.example.test", "token", "100") } returns seedJob.copy(status = "Completed")
        every { repo.getJobPartsCase(any(), any(), any()) } returns null
        every { repo.getJobPhotoStatus(any(), any(), any()) } returns null
        every { repo.getJobTimeline(any(), any(), any()) } returns emptyList()

        val viewModel = JobDetailViewModel(repo) { session() }
        viewModel.submitCloseout(seedJob, draft)

        val action = viewModel.actionEvents.getOrAwaitValue(time = 5)
        val context = viewModel.context.getOrAwaitValue(time = 5)

        assertEquals("closeout_submit", action.actionKey)
        assertEquals(true, action.result.success)
        assertEquals("Completed", context.job.status)
        verify(exactly = 1) { repo.submitCloseout("https://ops.example.test", "token", "100", draft) }
    }
}
