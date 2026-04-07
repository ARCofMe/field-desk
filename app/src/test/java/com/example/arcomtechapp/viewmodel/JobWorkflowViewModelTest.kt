package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.LocalWorkflowStateRepository
import com.example.arcomtechapp.data.repo.PhotoUploadRequest
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
class JobWorkflowViewModelTest {

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

    private fun job(): Job = Job(
        id = "100",
        address = "123 Main",
        appointmentWindow = "8-10",
        customerName = "Acme",
        customerPhone = "555-0100",
        status = "Scheduled"
    )

    @Test
    fun `saveDraft updates workflow state and emits saved message`() {
        val repo = mockk<FieldOpsRepository>(relaxed = true)
        val localRepo = mockk<LocalWorkflowStateRepository>(relaxed = true)
        val state = com.example.arcomtechapp.data.models.JobWorkflowState(
            jobId = "100",
            noteDraft = "Draft note",
            notePendingSync = true,
            noteLastSyncMessage = "Draft saved locally. Sync note to Ops Hub.",
            photoCount = 0,
            lastPhotoLabel = null,
            finalOutcome = null,
            finalOutcomeNote = null,
            lastAction = "Saved guided note draft",
            lastActionJobId = "100"
        )
        every { localRepo.getJobWorkflowState("100") } returns state

        val viewModel = JobWorkflowViewModel(repo, localRepo) { session() }
        viewModel.saveDraft(job(), "Draft note")

        assertEquals("Draft note", viewModel.workflowState.getOrAwaitValue().noteDraft)
        assertEquals("Draft saved locally", viewModel.actionMessage.getOrAwaitValue(ignoreNulls = true))
        verify { localRepo.setNoteDraft("100", "Draft note") }
        verify { localRepo.setNoteSyncState("100", true, "Draft saved locally. Sync note to Ops Hub.") }
    }

    @Test
    fun `uploadPhoto posts success message and refreshes status`() {
        val repo = mockk<FieldOpsRepository>(relaxed = true)
        val localRepo = mockk<LocalWorkflowStateRepository>(relaxed = true)
        every { repo.uploadJobPhoto(any(), any(), any(), any()) } returns TechnicianActionResult(true, "Uploaded")
        every { repo.getJobPhotoStatus(any(), any(), any()) } returns null
        every { localRepo.getJobWorkflowState("100") } returns com.example.arcomtechapp.data.models.JobWorkflowState(
            jobId = "100",
            noteDraft = null,
            notePendingSync = false,
            noteLastSyncMessage = null,
            photoCount = 1,
            lastPhotoLabel = "Overview",
            finalOutcome = null,
            finalOutcomeNote = null,
            lastAction = "Uploaded",
            lastActionJobId = "100"
        )

        val request = PhotoUploadRequest("a.jpg", "image/jpeg", byteArrayOf(1, 2, 3), "Overview")
        val viewModel = JobWorkflowViewModel(repo, localRepo) { session() }
        viewModel.uploadPhoto(job(), request)

        assertEquals(
            "Photo attached to SR. Capture the next required shot when ready.",
            viewModel.actionMessage.getOrAwaitValue(ignoreNulls = true)
        )
        verify { repo.uploadJobPhoto("https://ops.example.test", "token", "100", request) }
    }
}
