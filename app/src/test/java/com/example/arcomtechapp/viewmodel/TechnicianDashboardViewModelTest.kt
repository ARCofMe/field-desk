package com.example.arcomtechapp.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.BlueFolderRepository
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
        val repo = mockk<BlueFolderRepository>()
        val jobs = listOf(
            Job("1", "A", "8-10", "Cust1", "555", "Completed", 1.0),
            Job("2", "B", "10-12", "Cust2", "555", "Pending", 2.0),
            Job("3", "C", "1-3", "Cust3", "555", "pending", 3.0)
        )
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getTodayJobs(any(), any(), "t1") } returns jobs

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
        val repo = mockk<BlueFolderRepository>()
        every { repo.checkConnection(any(), any()) } returns "ok"
        every { repo.getTodayJobs(any(), any(), any()) } throws RuntimeException("dash fail")

        val viewModel = TechnicianDashboardViewModel(repo)
        viewModel.loadDashboard("t1")

        assertEquals("dash fail", viewModel.error.getOrAwaitValue())
    }
}
