package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.BlueFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JobsViewModel(
    private val repo: BlueFolderRepository = BlueFolderRepository()
) : ViewModel() {

    private val _jobs = MutableLiveData<List<Job>>()
    val jobs: LiveData<List<Job>> = _jobs

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadJobs(technicianId: String?, baseUrl: String? = null, apiKey: String? = null, startDate: String? = null, endDate: String? = null) {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _jobs.postValue(repo.getAllJobs(baseUrl, apiKey, technicianId, startDate, endDate))
                _error.postValue(null)
            } catch (e: Exception) {
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
}
