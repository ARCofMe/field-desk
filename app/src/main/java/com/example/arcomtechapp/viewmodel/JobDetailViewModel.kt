package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobCloseoutDraft
import com.example.arcomtechapp.data.models.JobCloseoutPreview
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.TechnicianActionResult
import com.example.arcomtechapp.runtime.FieldDeskAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class JobDetailContext(
    val job: Job,
    val partsCase: JobPartsCase?,
    val photoStatus: JobPhotoStatus?,
    val timeline: List<JobTimelineEntry>
)

data class JobActionEvent(
    val eventId: Long,
    val job: Job,
    val actionKey: String,
    val details: String? = null,
    val result: TechnicianActionResult
)

class JobDetailViewModel(
    private val repo: FieldOpsRepository,
    private val sessionProvider: () -> FieldDeskSession
) : ViewModel() {

    private val _context = MutableLiveData<JobDetailContext>()
    val context: LiveData<JobDetailContext> = _context

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _actionEvents = MutableLiveData<JobActionEvent>()
    val actionEvents: LiveData<JobActionEvent> = _actionEvents

    private val _closeoutPreview = MutableLiveData<JobCloseoutPreview?>()
    val closeoutPreview: LiveData<JobCloseoutPreview?> = _closeoutPreview

    fun loadJobContext(seedJob: Job) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionProvider()
                val refreshedJob = repo.getJob(session.baseUrl, session.apiKey, seedJob.id) ?: seedJob
                val refreshedPartsCase = repo.getJobPartsCase(session.baseUrl, session.apiKey, seedJob.id)
                val refreshedPhotoStatus = repo.getJobPhotoStatus(session.baseUrl, session.apiKey, seedJob.id)
                val refreshedTimeline = repo.getJobTimeline(session.baseUrl, session.apiKey, seedJob.id)
                _context.postValue(
                    JobDetailContext(
                        job = refreshedJob,
                        partsCase = refreshedPartsCase,
                        photoStatus = refreshedPhotoStatus,
                        timeline = refreshedTimeline
                    )
                )
                _error.postValue(null)
            } catch (e: Exception) {
                _context.postValue(
                    JobDetailContext(
                        job = seedJob,
                        partsCase = null,
                        photoStatus = null,
                        timeline = emptyList()
                    )
                )
                _error.postValue(formatError(e.message))
            }
            _loading.postValue(false)
        }
    }

    fun logWorkStart(job: Job, details: String?) {
        runAction(job = job, actionKey = "work_start", details = details) { session ->
            repo.logWorkStart(session.baseUrl, session.apiKey, job.id, details)
        }
    }

    fun updateStatus(job: Job, statusKey: String, fallbackSuccessMessage: String? = null) {
        runAction(job = job, actionKey = statusKey) { session ->
            val result = repo.updateJobStatus(session.baseUrl, session.apiKey, job.id, statusKey)
            if (result.success && result.message.isBlank() && !fallbackSuccessMessage.isNullOrBlank()) {
                result.copy(message = fallbackSuccessMessage)
            } else {
                result
            }
        }
    }

    fun reportNoAnswer(job: Job, details: String) {
        runAction(job = job, actionKey = "no_answer", details = details) { session ->
            repo.reportNoAnswer(session.baseUrl, session.apiKey, job.id, details)
        }
    }

    fun reportNotHome(job: Job, details: String) {
        runAction(job = job, actionKey = "not_home", details = details) { session ->
            repo.reportNotHome(session.baseUrl, session.apiKey, job.id, details)
        }
    }

    fun reportUnableToComplete(job: Job, reason: String) {
        runAction(job = job, actionKey = "unable_to_complete", details = reason) { session ->
            repo.reportUnableToComplete(session.baseUrl, session.apiKey, job.id, reason)
        }
    }

    fun logCallAhead(job: Job, minutes: Int = 30) {
        runAction(job = job, actionKey = "call_ahead") { session ->
            val result = repo.logCallAhead(session.baseUrl, session.apiKey, job.id, minutes)
            if (result.success && result.message.isBlank()) {
                result.copy(message = "Call-ahead logged")
            } else {
                result
            }
        }
    }

    fun createPartsRequest(job: Job, details: String) {
        runAction(job = job, actionKey = "parts", details = details) { session ->
            repo.createPartsRequest(session.baseUrl, session.apiKey, job.id, details)
        }
    }

    fun reportQuoteNeeded(job: Job, details: String, subtype: String) {
        runAction(job = job, actionKey = "quote_needed", details = details) { session ->
            repo.reportQuoteNeeded(session.baseUrl, session.apiKey, job.id, details, subtype)
        }
    }

    fun reportReschedule(job: Job, reason: String) {
        runAction(job = job, actionKey = "reschedule", details = reason) { session ->
            repo.reportReschedule(session.baseUrl, session.apiKey, job.id, reason)
        }
    }

    fun previewCloseout(job: Job, draft: JobCloseoutDraft) {
        viewModelScope.launch(Dispatchers.IO) {
            val preview = try {
                val session = sessionProvider()
                repo.previewCloseout(session.baseUrl, session.apiKey, job.id, draft)
            } catch (_: Exception) {
                null
            }
            _closeoutPreview.postValue(preview)
        }
    }

    fun submitCloseout(job: Job, draft: JobCloseoutDraft) {
        runAction(job = job, actionKey = "closeout_submit", details = draft.workPerformed) { session ->
            repo.submitCloseout(session.baseUrl, session.apiKey, job.id, draft)
        }
    }

    private fun runAction(
        job: Job,
        actionKey: String,
        details: String? = null,
        block: (FieldDeskSession) -> TechnicianActionResult
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                block(sessionProvider())
            } catch (e: Exception) {
                TechnicianActionResult(false, formatError(e.message))
            }
            _actionEvents.postValue(
                JobActionEvent(
                    eventId = System.nanoTime(),
                    job = job,
                    actionKey = actionKey,
                    details = details,
                    result = result
                )
            )
            if (result.success) {
                loadJobContext(job)
            }
        }
    }

    private fun formatError(message: String?): String {
        if (message.isNullOrBlank()) return "Unable to load the latest job context"
        return message.substringBefore("<!DOCTYPE").substringBefore("<html").replace("\n", " ").trim()
    }

    class Factory(
        private val container: FieldDeskAppContainer
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(JobDetailViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return JobDetailViewModel(
                    repo = container.repository(),
                    sessionProvider = container::currentSession
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
