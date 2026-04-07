package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.BlueFolderFieldOpsRepository
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class JobsViewModel(
    private val repo: FieldOpsRepository = BlueFolderFieldOpsRepository()
) : ViewModel() {

    private val _jobs = MutableLiveData<List<Job>>()
    val jobs: LiveData<List<Job>> = _jobs

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadJobs(
        session: FieldDeskSession,
        startDate: String? = null,
        endDate: String? = null,
        dateRangeType: String = "scheduled"
    ) {
        _loading.value = true
        _error.value = null
        Log.d("JobsViewModel", "Loading jobs tech=${session.technicianId} start=$startDate end=$endDate type=$dateRangeType")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jobs = repo.getAllJobs(session.baseUrl, session.apiKey, session.technicianId, startDate, endDate, dateRangeType)
                Log.d("JobsViewModel", "Loaded ${jobs.size} jobs")
                _jobs.postValue(jobs)
                _error.postValue(null)
            } catch (e: Exception) {
                Log.e("JobsViewModel", "Error loading jobs", e)
                _error.postValue(formatError(e.message))
            }
            _loading.postValue(false)
        }
    }

    private fun formatError(message: String?): String {
        if (message.isNullOrBlank()) return "Unable to load jobs"
        val clean = message.substringBefore("<!DOCTYPE").substringBefore("<html")
        return clean.replace("\n", " ").trim()
    }

    class Factory(
        private val repo: FieldOpsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(JobsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return JobsViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
