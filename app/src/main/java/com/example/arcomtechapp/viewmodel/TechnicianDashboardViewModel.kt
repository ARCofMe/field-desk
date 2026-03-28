package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.repo.BlueFolderFieldOpsRepository
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.models.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TechnicianDashboardViewModel(
    private val repo: FieldOpsRepository = BlueFolderFieldOpsRepository()
) : ViewModel() {

    data class DashboardSummary(
        val completed: Int,
        val pending: Int
    )

    private val _todayJobs = MutableLiveData<List<Job>>()
    val todayJobs: LiveData<List<Job>> = _todayJobs

    private val _summary = MutableLiveData<DashboardSummary>()
    val summary: LiveData<DashboardSummary> = _summary

    private val _connectionStatus = MutableLiveData<String>()
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadDashboard(technicianId: String?, baseUrl: String? = null, apiKey: String? = null) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.postValue(repo.checkConnection(baseUrl, apiKey))

                val jobs = repo.getTodayJobs(baseUrl, apiKey, technicianId)
                val completed = jobs.count { it.status.equals("completed", ignoreCase = true) }
                val pending = jobs.size - completed

                _todayJobs.postValue(jobs)
                _summary.postValue(DashboardSummary(completed = completed, pending = pending))
                _error.postValue(null)
            } catch (e: Exception) {
                _error.postValue(formatError(e.message))
            }
            _loading.postValue(false)
        }
    }

    private fun formatError(message: String?): String {
        if (message.isNullOrBlank()) return "Unknown error"
        // Strip any HTML fragments
        val clean = message.substringBefore("<!DOCTYPE").substringBefore("<html")
        return clean.replace("\n", " ").trim()
    }

    class Factory(
        private val repo: FieldOpsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TechnicianDashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TechnicianDashboardViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
