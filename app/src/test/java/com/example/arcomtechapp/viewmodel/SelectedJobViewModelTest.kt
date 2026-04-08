package com.example.arcomtechapp.viewmodel

import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.util.getOrAwaitValue
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectedJobViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Test
    fun select_defaults_to_detail_panel() {
        val viewModel = SelectedJobViewModel()
        val job = sampleJob("123")

        viewModel.select(job)

        val state = viewModel.selectedJob.getOrAwaitValue(time = 5)
        assertEquals(job, state?.job)
        assertEquals(WorkflowPanel.DETAIL, state?.workflowPanel)
    }

    @Test
    fun openWorkflowPanel_updates_existing_selection() {
        val viewModel = SelectedJobViewModel()
        val job = sampleJob("321")
        viewModel.select(job)

        viewModel.openWorkflowPanel(WorkflowPanel.PHOTOS)

        val state = viewModel.selectedJob.getOrAwaitValue(time = 5)
        assertEquals(job, state?.job)
        assertEquals(WorkflowPanel.PHOTOS, state?.workflowPanel)
    }

    private fun sampleJob(id: String) = Job(
        id = id,
        address = "123 Main St",
        appointmentWindow = "8-10",
        customerName = "Customer",
        customerPhone = "555-1212",
        status = "Scheduled",
        distanceMiles = null,
        equipment = "System"
    )
}
