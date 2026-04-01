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
    val lastPhotoLabel: String? = null,
    val finalOutcome: String? = null,
    val finalOutcomeNote: String? = null
)

data class JobCompletionSummary(
    val ready: Boolean,
    val headline: String,
    val blockers: List<String>,
    val requiredPhotoLabels: List<String>
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
        val statusText = buildString {
            append(job.status)
            job.nextAction?.takeIf { it.isNotBlank() }?.let { append(" $it") }
        }.lowercase()
        val equipment = job.equipment?.takeIf { it.isNotBlank() } ?: "equipment"
        val proofOfVisit = statusText.contains("not home") || statusText.contains("no-answer") || statusText.contains("no answer")
        return listOf(
            PhotoPrompt("mdlsn", "Model / serial", "Get the rating plate on the $equipment."),
            PhotoPrompt(
                "overview",
                if (proofOfVisit) "House / location" else "Overview",
                if (proofOfVisit) {
                    "Capture the house, unit location, or other proof you were at the correct stop."
                } else {
                    "Capture a wide shot showing location and install condition."
                }
            ),
            PhotoPrompt(
                "issue",
                if (proofOfVisit) "Door / access" else "Issue / part",
                if (proofOfVisit) {
                    "Capture any closed-door, access, or on-site proof that explains the missed visit."
                } else {
                    "Capture the failed area, damaged part, or point of concern."
                }
            )
        )
    }

    fun completionSummary(job: Job, progress: JobProgress): JobCompletionSummary {
        val requiredPhotoLabels = photoPrompts(job).map { it.label }
        val blockers = mutableListOf<String>()
        if (progress.photoCount <= 0) {
            blockers += "Capture the required photo set: ${requiredPhotoLabels.joinToString(", ")}."
        }
        if (progress.noteDraftLength < 40) {
            blockers += "Finish a structured service note."
        }
        if (progress.finalOutcome.isNullOrBlank()) {
            blockers += "Choose a final outcome before closing the job."
        }
        if (progress.finalOutcome.equals("unable_to_complete", ignoreCase = true) &&
            (progress.finalOutcomeNote?.length ?: 0) < 20
        ) {
            blockers += "Explain why the job could not be completed."
        }
        return if (blockers.isEmpty()) {
            JobCompletionSummary(
                ready = true,
                headline = "Ready for closeout",
                blockers = emptyList(),
                requiredPhotoLabels = requiredPhotoLabels
            )
        } else {
            JobCompletionSummary(
                ready = false,
                headline = "Closeout still blocked",
                blockers = blockers,
                requiredPhotoLabels = requiredPhotoLabels
            )
        }
    }
}
