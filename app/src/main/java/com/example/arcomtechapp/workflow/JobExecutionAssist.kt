package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job

data class NoteTemplate(
    val key: String,
    val label: String,
    val body: String
)

data class PhotoPrompt(
    val key: String,
    val label: String,
    val helper: String
)

data class JobProgress(
    val noteDraftLength: Int = 0,
    val photoCount: Int = 0,
    val lastPhotoLabel: String? = null
)

data class JobCompletionSummary(
    val ready: Boolean,
    val headline: String,
    val blockers: List<String>
)

object JobExecutionAssist {

    fun noteTemplates(job: Job): List<NoteTemplate> {
        val equipment = job.equipment?.takeIf { it.isNotBlank() } ?: "unit"
        return listOf(
            NoteTemplate(
                key = "arrival",
                label = "Arrival",
                body = "Arrived on site for ${job.customerName}. Confirmed access to the $equipment and began inspection."
            ),
            NoteTemplate(
                key = "diagnosis",
                label = "Diagnosis",
                body = "Diagnosed the $equipment. Verified symptoms, inspected major components, and documented the current operating condition."
            ),
            NoteTemplate(
                key = "parts",
                label = "Parts",
                body = "Parts follow-up needed. Identified required components, documented fitment details, and flagged the job for return scheduling."
            ),
            NoteTemplate(
                key = "closeout",
                label = "Closeout",
                body = "Work completed. Operation was verified with the customer, notes were reviewed, and supporting photos were captured."
            )
        )
    }

    fun photoPrompts(job: Job): List<PhotoPrompt> {
        val equipment = job.equipment?.takeIf { it.isNotBlank() } ?: "equipment"
        return listOf(
            PhotoPrompt("mdlsn", "Model / serial", "Get the rating plate on the $equipment."),
            PhotoPrompt("overview", "Overview", "Capture a wide shot showing location and install condition."),
            PhotoPrompt("issue", "Issue / part", "Capture the failed area, damaged part, or point of concern.")
        )
    }

    fun completionSummary(job: Job, progress: JobProgress): JobCompletionSummary {
        val blockers = mutableListOf<String>()
        if (progress.photoCount <= 0) {
            blockers += "Capture at least one supporting photo."
        }
        if (progress.noteDraftLength < 40) {
            blockers += "Finish a structured service note."
        }
        if (!job.status.contains("complete", ignoreCase = true)) {
            blockers += "Record the final job outcome."
        }
        return if (blockers.isEmpty()) {
            JobCompletionSummary(
                ready = true,
                headline = "Ready for closeout",
                blockers = emptyList()
            )
        } else {
            JobCompletionSummary(
                ready = false,
                headline = "Closeout still blocked",
                blockers = blockers
            )
        }
    }
}
