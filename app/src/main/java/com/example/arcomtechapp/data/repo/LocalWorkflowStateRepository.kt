package com.example.arcomtechapp.data.repo

import com.example.arcomtechapp.data.models.JobWorkflowState
import com.example.arcomtechapp.storage.Storage

class LocalWorkflowStateRepository(
    private val storage: Storage
) {

    fun getJobWorkflowState(jobId: String): JobWorkflowState {
        val progress = storage.getLocalJobProgress(jobId)
        return JobWorkflowState(
            jobId = jobId,
            noteDraft = progress.noteDraft,
            notePendingSync = progress.notePendingSync,
            noteLastSyncMessage = progress.noteLastSyncMessage,
            photoCount = progress.photoCount,
            lastPhotoLabel = progress.lastPhotoLabel,
            finalOutcome = progress.finalOutcome,
            finalOutcomeNote = progress.finalOutcomeNote,
            lastAction = storage.getLastJobAction(),
            lastActionJobId = storage.getLastJobActionJobId()
        )
    }

    fun saveLastAction(jobId: String?, action: String?) {
        storage.saveLastJobAction(jobId, action)
    }

    fun setFinalOutcome(jobId: String?, outcome: String?, note: String? = null) {
        storage.setJobFinalOutcome(jobId, outcome, note)
    }
}
