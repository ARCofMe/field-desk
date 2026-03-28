package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.TechnicianActionResult
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

    @Test
    fun `loadDashboard posts jobs and summary`() {
        val repo = mockk<FieldOpsRepository>()
        val jobs = listOf(
            Job("1", "A", "8-10", "Cust1", "555", "Completed", 1.0),
            Job("2", "B", "10-12", "Cust2", "555", "Pending", 2.0),
            Job("3", "C", "1-3", "Cust3", "555", "pending", 3.0)
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
        viewModel.loadDashboard("t1")

        val emitted = viewModel.todayJobs.getOrAwaitValue(time = 5)
        assertEquals(3, emitted.size)
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
        viewModel.loadDashboard("t1")

        assertEquals("dash fail", viewModel.error.getOrAwaitValue(ignoreNulls = true))
    }
}
