package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.runtime.FieldDeskAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class JobDetailContext(
    val job: Job,
    val partsCase: JobPartsCase?,
    val photoStatus: JobPhotoStatus?,
    val timeline: List<JobTimelineEntry>
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
