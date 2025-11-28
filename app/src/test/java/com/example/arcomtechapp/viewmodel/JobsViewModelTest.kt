package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.BlueFolderRepository
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

    @Test
    fun `loadJobs posts data and clears error`() {
        val repo = mockk<BlueFolderRepository>()
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
        every { repo.getAllJobs(null, null, "t1") } returns jobs

        val viewModel = JobsViewModel(repo)
        viewModel.loadJobs("t1")

        val emitted = viewModel.jobs.getOrAwaitValue(time = 5)
        assertEquals(1, emitted.size)
        assertEquals("1", emitted.first().id)
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `loadJobs failure posts error`() {
        val repo = mockk<BlueFolderRepository>()
        every { repo.getAllJobs(any(), any(), any()) } throws IllegalStateException("boom")

        val viewModel = JobsViewModel(repo)
        viewModel.loadJobs("t1")

        assertEquals("boom", viewModel.error.getOrAwaitValue())
        // Jobs are never posted; ensure LiveData remains unset/null-safe.
        assertNotNull(viewModel.jobs)
    }
}
