package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobExecutionAssistTest {

    private val sampleJob = Job(
        id = "1001",
        address = "123 Main St",
        appointmentWindow = "8-10",
        customerName = "Pat Lee",
        customerPhone = "555-0100",
        status = "In Progress",
        distanceMiles = 2.5,
        equipment = "Washer"
    )

    @Test
    fun `completion summary blocks closeout when note and photos are missing`() {
        val summary = JobExecutionAssist.completionSummary(sampleJob, JobProgress())

        assertFalse(summary.ready)
        assertEquals("Closeout still blocked", summary.headline)
        assertEquals(3, summary.blockers.size)
    }

    @Test
    fun `completion summary becomes ready for completed job with note and photo`() {
        val summary = JobExecutionAssist.completionSummary(
            sampleJob,
            JobProgress(
                noteDraftLength = 80,
                photoCount = 2,
                lastPhotoLabel = "Overview",
                finalOutcome = "completed",
                finalOutcomeNote = "Operation verified."
            )
        )

        assertTrue(summary.ready)
        assertTrue(summary.blockers.isEmpty())
    }

    @Test
    fun `completion summary requires reason when unable to complete`() {
        val summary = JobExecutionAssist.completionSummary(
            sampleJob,
            JobProgress(
                noteDraftLength = 80,
                photoCount = 2,
                finalOutcome = "unable_to_complete",
                finalOutcomeNote = "short"
            )
        )

        assertFalse(summary.ready)
        assertTrue(summary.blockers.any { it.contains("could not be completed") })
    }

    @Test
    fun `photo prompts pivot to proof of visit context for not home jobs`() {
        val prompts = JobExecutionAssist.photoPrompts(sampleJob.copy(status = "Not home"))

        assertEquals("House / location", prompts[1].label)
        assertEquals("Door / access", prompts[2].label)
    }

    @Test
    fun `note templates stay technician oriented`() {
        val templates = JobExecutionAssist.noteTemplates(sampleJob)

        assertEquals(4, templates.size)
        assertTrue(templates.first().body.contains("Arrived on site"))
    }
}
