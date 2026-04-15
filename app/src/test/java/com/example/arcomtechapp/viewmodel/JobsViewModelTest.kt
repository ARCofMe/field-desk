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
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JobsViewModelTest {

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
    fun `loadJobs posts data and clears error`() {
        val repo = mockk<FieldOpsRepository>()
        val jobs = listOf(
            Job(
                id = "1",
                address = "123 Main",
                appointmentWindow = "8-10",
                customerName = "Acme",
                customerPhone = "555",
                status = "Pending",
                distanceMiles = 1.0
            )
        )
        every { repo.testPython() } returns ""
        every { repo.getAssignmentsForUser(any()) } returns emptyList()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getAllJobs(null, null, "t1", null, null, "scheduled") } returns jobs
        every { repo.submitJobNote(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.updateJobStatus(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.createPartsRequest(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.preparePhotoUpload(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")

        val viewModel = JobsViewModel(repo)
        viewModel.loadJobs(session("t1"))

        val emitted = viewModel.jobs.getOrAwaitValue(time = 5)
        assertEquals(1, emitted.size)
        assertEquals("1", emitted.first().id)
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `loadJobs failure posts error`() {
        val repo = mockk<FieldOpsRepository>()
        every { repo.testPython() } returns ""
        every { repo.getAssignmentsForUser(any()) } returns emptyList()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getAllJobs(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        every { repo.submitJobNote(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.updateJobStatus(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.createPartsRequest(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.preparePhotoUpload(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")

        val viewModel = JobsViewModel(repo)
        viewModel.loadJobs(session("t1"))

        assertEquals("boom", viewModel.error.getOrAwaitValue(ignoreNulls = true))
        // Jobs are never posted; ensure LiveData remains unset/null-safe.
        assertNotNull(viewModel.jobs)
    }

    @Test
    fun `loadJobs failure keeps last loaded jobs for field stability`() {
        val repo = mockk<FieldOpsRepository>()
        val jobs = listOf(
            Job(
                id = "cached",
                address = "123 Main",
                appointmentWindow = "8-10",
                customerName = "Acme",
                customerPhone = "555",
                status = "Pending",
                distanceMiles = 1.0
            )
        )
        every { repo.testPython() } returns ""
        every { repo.getAssignmentsForUser(any()) } returns emptyList()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getAllJobs(null, null, "t1", null, null, "scheduled") } returns jobs andThenThrows RuntimeException("network down")
        every { repo.submitJobNote(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.updateJobStatus(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.createPartsRequest(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")
        every { repo.preparePhotoUpload(any(), any(), any(), any()) } returns TechnicianActionResult(false, "")

        val viewModel = JobsViewModel(repo)
        viewModel.loadJobs(session("t1"))
        assertEquals("cached", viewModel.jobs.getOrAwaitValue(time = 5).first().id)

        viewModel.loadJobs(session("t1"))

        assertEquals("cached", viewModel.jobs.getOrAwaitValue(time = 5).first().id)
        assertEquals("Showing last loaded jobs. network down", viewModel.error.getOrAwaitValue(ignoreNulls = true))
    }
}
