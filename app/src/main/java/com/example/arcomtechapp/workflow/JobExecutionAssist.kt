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

    private fun statusContext(job: Job): String = buildString {
        append(job.status)
        job.nextAction?.takeIf { it.isNotBlank() }?.let { append(" $it") }
    }.lowercase()

    private fun isProofOfVisitJob(job: Job): Boolean {
        val statusText = statusContext(job)
        return statusText.contains("not home") || statusText.contains("no-answer") || statusText.contains("no answer")
    }

    fun noteTemplates(job: Job): List<NoteTemplate> {
        val equipment = job.equipment?.takeIf { it.isNotBlank() } ?: "unit"
        val proofOfVisit = isProofOfVisitJob(job)
        val quoteNeeded = statusContext(job).contains("quote")
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
                key = if (proofOfVisit) "proof" else "parts",
                label = if (proofOfVisit) "Proof of visit" else "Parts",
                body = if (proofOfVisit) {
                    "Proof of visit captured. Documented house or unit location, access condition, and what was observed on arrival."
                } else {
                    "Parts follow-up needed. Identified required components, documented fitment details, and flagged the job for return scheduling."
                }
            ),
            NoteTemplate(
                key = if (proofOfVisit) "dispatch_handoff" else if (quoteNeeded) "quote_handoff" else "closeout",
                label = if (proofOfVisit) "Dispatch handoff" else if (quoteNeeded) "Quote handoff" else "Closeout",
                body = if (proofOfVisit) {
                    "Dispatch follow-up needed. Customer was not available for service, proof photos were captured, and the stop needs office review."
                } else if (quoteNeeded) {
                    "Office quote follow-up needed. Captured the field findings, customer-facing pricing context, and what approval is required before return scheduling."
                } else {
                    "Work completed. Operation was verified with the customer, notes were reviewed, and supporting photos were captured."
                }
            )
        )
    }

    fun photoPrompts(job: Job): List<PhotoPrompt> {
        val equipment = job.equipment?.takeIf { it.isNotBlank() } ?: "equipment"
        val proofOfVisit = isProofOfVisitJob(job)
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

    fun noteGuidance(job: Job, finalOutcome: String? = null): String {
        val proofOfVisit = isProofOfVisitJob(job)
        val outcome = finalOutcome?.lowercase()
        return when {
            outcome == "unable_to_complete" -> "Focus the note on what blocked completion, what was verified on site, and what office or dispatch needs next."
            proofOfVisit -> "Write a proof-of-visit note: where you went, what access condition you found, and which photos support the missed stop."
            statusContext(job).contains("quote") -> "Write the note so office can quote or get approval without re-calling you for missing context."
            else -> "Write the note so the next person can understand the diagnosis, work performed, and any follow-up without reading comment soup."
        }
    }

    fun photoChecklist(job: Job, photoCount: Int, lastPhotoLabel: String? = null): List<String> {
        val prompts = photoPrompts(job).map { it.label }
        return buildList {
            add("Required set: ${prompts.joinToString(", ")}")
            if (isProofOfVisitJob(job)) {
                add("Proof-of-visit job: make sure the house or access condition is visible.")
            }
            add(
                if (photoCount > 0) {
                    "Captured so far: $photoCount${lastPhotoLabel?.let { " (last: $it)" } ?: ""}"
                } else {
                    "No required photos captured yet."
                }
            )
        }
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
