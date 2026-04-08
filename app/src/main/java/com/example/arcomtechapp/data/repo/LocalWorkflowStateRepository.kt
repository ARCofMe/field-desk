package com.example.arcomtechapp.data.repo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.arcomtechapp.data.models.JobWorkflowState
import com.example.arcomtechapp.storage.Storage

class LocalWorkflowStateRepository(
    private val storage: Storage
) {

    private val _updatedJobId = MutableLiveData<String?>()
    val updatedJobId: LiveData<String?> = _updatedJobId

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
        notifyUpdated(jobId)
    }

    fun setFinalOutcome(jobId: String?, outcome: String?, note: String? = null) {
        storage.setJobFinalOutcome(jobId, outcome, note)
        notifyUpdated(jobId)
    }

    fun setNoteDraft(jobId: String?, draft: String?) {
        storage.setJobNotesDraft(jobId, draft)
        notifyUpdated(jobId)
    }

    fun clearNoteDraft(jobId: String?) {
        storage.clearJobNotesDraft(jobId)
        notifyUpdated(jobId)
    }

    fun setNoteSyncState(jobId: String?, pending: Boolean, message: String? = null) {
        storage.setJobNoteSyncState(jobId, pending, message)
        notifyUpdated(jobId)
    }

    fun clearNoteSyncState(jobId: String?) {
        storage.clearJobNoteSyncState(jobId)
        notifyUpdated(jobId)
    }

    fun recordPhotoCapture(jobId: String?, label: String) {
        storage.recordJobPhotoCapture(jobId, label)
        notifyUpdated(jobId)
    }

    fun shouldAutoCompressPhotos(): Boolean = storage.shouldAutoCompressPhotos()

    fun setAutoCompressPhotos(enabled: Boolean) {
        storage.setAutoCompressPhotos(enabled)
    }

    private fun notifyUpdated(jobId: String?) {
        _updatedJobId.postValue(jobId)
    }
}
