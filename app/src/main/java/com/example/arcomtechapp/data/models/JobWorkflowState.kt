package com.example.arcomtechapp.data.models

import com.example.arcomtechapp.workflow.JobProgress

data class JobWorkflowState(
    val jobId: String,
    val noteDraft: String?,
    val notePendingSync: Boolean,
    val noteLastSyncMessage: String?,
    val photoCount: Int,
    val lastPhotoLabel: String?,
    val finalOutcome: String?,
    val finalOutcomeNote: String?,
    val lastAction: String?,
    val lastActionJobId: String?
) {
    fun asJobProgress(): JobProgress = JobProgress(
        noteDraftLength = noteDraft?.length ?: 0,
        notePendingSync = notePendingSync,
        photoCount = photoCount,
        lastPhotoLabel = lastPhotoLabel,
        finalOutcome = finalOutcome,
        finalOutcomeNote = finalOutcomeNote
    )

    val hasDraft: Boolean
        get() = !noteDraft.isNullOrBlank()

    val appliesLastActionToThisJob: Boolean
        get() = lastActionJobId == jobId && !lastAction.isNullOrBlank()
}
