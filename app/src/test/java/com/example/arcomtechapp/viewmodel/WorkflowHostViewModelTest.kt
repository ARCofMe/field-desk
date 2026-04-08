package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.LocalWorkflowStateRepository
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.storage.Storage.LocalJobProgress
import com.example.arcomtechapp.util.getOrAwaitValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkflowHostViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Test
    fun `load builds readiness summary from local workflow state`() {
        val storage = mockk<Storage>()
        every { storage.getLocalJobProgress("123") } returns LocalJobProgress(
            noteDraft = "Technician completed diagnosis and documented parts needed.",
            notePendingSync = true,
            noteLastSyncMessage = "Pending",
            photoCount = 2,
            lastPhotoLabel = "Issue / part",
            finalOutcome = "unable_to_complete",
            finalOutcomeNote = "Waiting on OEM board approval before returning.",
            workStartedAtEpochMillis = null
        )
        every { storage.getLastJobAction() } returns "Captured issue photo"
        every { storage.getLastJobActionJobId() } returns "123"

        val viewModel = WorkflowHostViewModel(LocalWorkflowStateRepository(storage))
        viewModel.load(sampleJob())

        val summary = viewModel.summary.getOrAwaitValue(time = 5)
        assertEquals("Parts follow-up", summary.workflowHeadline)
        assertEquals("Closeout still blocked", summary.readinessHeadline)
        assertTrue(summary.blockersText.contains("Sync the saved service note"))
        assertTrue(summary.localProgressText.contains("Photos: 2"))
        assertTrue(summary.localProgressText.contains("Last action: Captured issue photo"))
    }

    private fun sampleJob() = Job(
        id = "123",
        address = "123 Main St",
        appointmentWindow = "8-10",
        customerName = "Acme",
        customerPhone = "555-0100",
        status = "Need Parts",
        equipment = "System board"
    )
}
