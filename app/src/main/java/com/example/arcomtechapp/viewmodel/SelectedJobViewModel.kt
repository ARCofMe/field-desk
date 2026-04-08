package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.arcomtechapp.data.models.Job

enum class WorkflowPanel {
    DETAIL,
    NOTES,
    PHOTOS
}

data class SelectedJobState(
    val job: Job? = null,
    val workflowPanel: WorkflowPanel = WorkflowPanel.DETAIL,
    val launchCallOnOpen: Boolean = false,
    val launchNavigationOnOpen: Boolean = false
)

class SelectedJobViewModel : ViewModel() {

    private val _selectedJob = MutableLiveData(SelectedJobState())
    val selectedJob: LiveData<SelectedJobState> = _selectedJob

    fun select(
        job: Job,
        workflowPanel: WorkflowPanel = WorkflowPanel.DETAIL,
        launchCallOnOpen: Boolean = false,
        launchNavigationOnOpen: Boolean = false
    ) {
        _selectedJob.value = SelectedJobState(
            job = job,
            workflowPanel = workflowPanel,
            launchCallOnOpen = launchCallOnOpen,
            launchNavigationOnOpen = launchNavigationOnOpen
        )
    }

    fun currentJob(): Job? = _selectedJob.value?.job

    fun currentWorkflowPanel(): WorkflowPanel = _selectedJob.value?.workflowPanel ?: WorkflowPanel.DETAIL

    fun openWorkflowPanel(panel: WorkflowPanel) {
        val current = _selectedJob.value ?: SelectedJobState()
        if (current.workflowPanel == panel) return
        _selectedJob.value = current.copy(workflowPanel = panel)
    }

    fun consumeLaunchFlags() {
        val current = _selectedJob.value ?: return
        if (!current.launchCallOnOpen && !current.launchNavigationOnOpen) return
        _selectedJob.value = current.copy(
            launchCallOnOpen = false,
            launchNavigationOnOpen = false
        )
    }
}
