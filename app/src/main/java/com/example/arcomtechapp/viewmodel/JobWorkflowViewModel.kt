package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobWorkflowState
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.LocalWorkflowStateRepository
import com.example.arcomtechapp.data.repo.PhotoUploadRequest
import com.example.arcomtechapp.runtime.FieldDeskAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JobWorkflowViewModel(
    private val repo: FieldOpsRepository,
    private val localStateRepository: LocalWorkflowStateRepository,
    private val sessionProvider: () -> FieldDeskSession
) : ViewModel() {

    private val _workflowState = MutableLiveData<JobWorkflowState>()
    val workflowState: LiveData<JobWorkflowState> = _workflowState

    private val _photoStatus = MutableLiveData<JobPhotoStatus?>()
    val photoStatus: LiveData<JobPhotoStatus?> = _photoStatus

    private val _actionMessage = MutableLiveData<String?>()
    val actionMessage: LiveData<String?> = _actionMessage

    fun load(job: Job) {
        _workflowState.value = localStateRepository.getJobWorkflowState(job.id)
        refreshPhotoStatus(job)
    }

    fun onNoteDraftChanged(job: Job, draft: String?) {
        localStateRepository.setNoteDraft(job.id, draft)
        if (!draft.isNullOrBlank()) {
            localStateRepository.setNoteSyncState(job.id, pending = true, message = "Draft changed locally. Sync note to Ops Hub.")
        }
        _workflowState.value = localStateRepository.getJobWorkflowState(job.id)
    }

    fun saveDraft(job: Job, draft: String?) {
        localStateRepository.setNoteDraft(job.id, draft)
        localStateRepository.saveLastAction(job.id, "Saved guided note draft")
        localStateRepository.setNoteSyncState(job.id, pending = true, message = "Draft saved locally. Sync note to Ops Hub.")
        _workflowState.value = localStateRepository.getJobWorkflowState(job.id)
        _actionMessage.value = "Draft saved locally"
    }

    fun clearDraft(job: Job) {
        localStateRepository.clearNoteDraft(job.id)
        localStateRepository.clearNoteSyncState(job.id)
        _workflowState.value = localStateRepository.getJobWorkflowState(job.id)
    }

    fun syncNote(job: Job, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionProvider()
            val result = try {
                repo.submitJobNote(session.baseUrl, session.apiKey, job.id, note)
            } catch (e: Exception) {
                com.example.arcomtechapp.data.repo.TechnicianActionResult(false, formatError(e.message))
            }
            localStateRepository.saveLastAction(job.id, result.message)
            localStateRepository.setNoteDraft(job.id, note)
            localStateRepository.setNoteSyncState(job.id, pending = !result.success, message = result.message)
            _workflowState.postValue(localStateRepository.getJobWorkflowState(job.id))
            _actionMessage.postValue(result.message)
        }
    }

    fun setAutoCompressPhotos(enabled: Boolean) {
        localStateRepository.setAutoCompressPhotos(enabled)
        _actionMessage.value = if (enabled) "Auto-compress enabled" else "Auto-compress disabled"
    }

    fun isAutoCompressEnabled(): Boolean = localStateRepository.shouldAutoCompressPhotos()

    fun recordPhotoCaptured(job: Job, label: String, statusMessage: String) {
        localStateRepository.recordPhotoCapture(job.id, label)
        localStateRepository.saveLastAction(job.id, "Captured ${label.lowercase()}")
        _workflowState.value = localStateRepository.getJobWorkflowState(job.id)
        _actionMessage.value = statusMessage
    }

    fun uploadPhoto(job: Job, request: PhotoUploadRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionProvider()
            val result = try {
                repo.uploadJobPhoto(session.baseUrl, session.apiKey, job.id, request)
            } catch (e: Exception) {
                com.example.arcomtechapp.data.repo.TechnicianActionResult(false, formatError(e.message))
            }
            localStateRepository.saveLastAction(job.id, result.message)
            _workflowState.postValue(localStateRepository.getJobWorkflowState(job.id))
            _actionMessage.postValue(
                if (result.success) {
                    "Photo attached to SR. Capture the next required shot when ready."
                } else {
                    result.message.ifBlank { "Photo was not attached. Keep this screen open and retry before leaving the stop." }
                }
            )
            refreshPhotoStatus(job)
        }
    }

    fun evaluatePhotoCompliance(job: Job) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionProvider()
            val result = try {
                repo.evaluatePhotoCompliance(session.baseUrl, session.apiKey, job.id, sendNotice = false)
            } catch (e: Exception) {
                com.example.arcomtechapp.data.repo.TechnicianActionResult(false, formatError(e.message))
            }
            _actionMessage.postValue(result.message.ifBlank { "Photo compliance checked" })
            refreshPhotoStatus(job)
        }
    }

    fun refreshPhotoStatus(job: Job) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionProvider()
            val status = repo.getJobPhotoStatus(session.baseUrl, session.apiKey, job.id)
            _photoStatus.postValue(status)
        }
    }

    private fun formatError(message: String?): String {
        if (message.isNullOrBlank()) return "Workflow action failed"
        return message.substringBefore("<!DOCTYPE").substringBefore("<html").replace("\n", " ").trim()
    }

    class Factory(
        private val container: FieldDeskAppContainer
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(JobWorkflowViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return JobWorkflowViewModel(
                    repo = container.repository(),
                    localStateRepository = container.localWorkflowStateRepository(),
                    sessionProvider = container::currentSession
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
