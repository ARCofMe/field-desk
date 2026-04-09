package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.TechnicianActionResult
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.getOrAwaitValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TechnicianDashboardViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private fun session(technicianId: String = "t1"): FieldDeskSession = FieldDeskSession(
        backendMode = Storage.BackendMode.BLUEFOLDER_DIRECT,
        technicianId = technicianId,
        technicianName = "Tech",
        baseUrl = null,
        apiKey = null,
        configComplete = true
    )

    @Test
    fun `loadDashboard posts jobs and summary`() {
        val repo = mockk<FieldOpsRepository>()
        val jobs = listOf(
            Job(id = "3", address = "C", appointmentWindow = "1-3", customerName = "Cust3", customerPhone = "555", status = "pending", distanceMiles = 3.0),
            Job(id = "2", address = "B", appointmentWindow = "10-12", customerName = "Cust2", customerPhone = "555", status = "Pending", distanceMiles = 2.0),
            Job(id = "1", address = "A", appointmentWindow = "8-10", customerName = "Cust1", customerPhone = "555", status = "Completed", distanceMiles = 1.0)
        )
        every { repo.testPython() } returns ""
        every { repo.getAssignmentsForUser(any()) } returns emptyList()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getTodayJobs(any(), any(), "t1") } returns jobs
        every { repo.getAllJobs(any(), any(), any(), any(), any(), any()) } returns emptyList()
        every { repo.submitJobNote(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.updateJobStatus(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.createPartsRequest(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.preparePhotoUpload(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")

        val viewModel = TechnicianDashboardViewModel(repo)
        viewModel.loadDashboard(session("t1"))

        val emitted = viewModel.todayJobs.getOrAwaitValue(time = 5)
        assertEquals(3, emitted.size)
        assertEquals(listOf("1", "2", "3"), emitted.map { it.id })
        val summary = viewModel.summary.getOrAwaitValue(time = 5)
        assertEquals(1, summary.completed)
        assertEquals(2, summary.pending)
    }

    @Test
    fun `loadDashboard failure surfaces error`() {
        val repo = mockk<FieldOpsRepository>()
        every { repo.testPython() } returns ""
        every { repo.getAssignmentsForUser(any()) } returns emptyList()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getTodayJobs(any(), any(), any()) } throws RuntimeException("dash fail")
        every { repo.getAllJobs(any(), any(), any(), any(), any(), any()) } returns emptyList()
        every { repo.submitJobNote(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.updateJobStatus(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.createPartsRequest(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.preparePhotoUpload(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")

        val viewModel = TechnicianDashboardViewModel(repo)
        viewModel.loadDashboard(session("t1"))

        assertEquals("dash fail", viewModel.error.getOrAwaitValue(ignoreNulls = true))
    }
}
