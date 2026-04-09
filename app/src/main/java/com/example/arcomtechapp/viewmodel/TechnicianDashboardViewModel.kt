package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.repo.BlueFolderFieldOpsRepository
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.workflow.JobWorkflow
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

    fun loadDashboard(session: FieldDeskSession) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.postValue(repo.checkConnection(session.baseUrl, session.apiKey))

                val jobs = repo.getTodayJobs(session.baseUrl, session.apiKey, session.technicianId)
                val orderedJobs = JobWorkflow.sortForTechnicianFlow(jobs)
                val completed = jobs.count { it.status.equals("completed", ignoreCase = true) }
                val pending = jobs.size - completed

                _todayJobs.postValue(orderedJobs)
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
        val flattened = clean.replace("\n", " ").trim()
        return when {
            flattened.contains("Configure Ops Hub base URL first", ignoreCase = true) ->
                "Ops Hub setup is incomplete. Open Settings and enter the server URL."
            flattened.contains("Configure Ops Hub API key first", ignoreCase = true) ->
                "Ops Hub setup is incomplete. Open Settings and enter the API key."
            flattened.contains("Technician mapping could not be resolved", ignoreCase = true) ->
                "This technician ID is not mapped in Ops Hub yet. Update Settings or the server mapping."
            flattened.contains("could not reach the configured server", ignoreCase = true) ->
                "Could not reach Ops Hub. Check your network and confirm the server URL is publicly reachable."
            else -> flattened
        }
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
