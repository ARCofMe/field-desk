package com.example.arcomtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.LocalWorkflowStateRepository
import com.example.arcomtechapp.runtime.FieldDeskAppContainer
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.workflow.JobWorkflow

data class WorkflowHostSummary(
    val workflowHeadline: String,
    val nextStep: String,
    val readinessHeadline: String,
    val blockersText: String,
    val localProgressText: String
)

class WorkflowHostViewModel(
    private val localStateRepository: LocalWorkflowStateRepository
) : ViewModel() {

    private val _summary = MutableLiveData<WorkflowHostSummary>()
    val summary: LiveData<WorkflowHostSummary> = _summary

    fun load(job: Job?) {
        if (job == null) {
            _summary.value = WorkflowHostSummary(
                workflowHeadline = "Workflow workspace ready",
                nextStep = "Pick a stop to load the guided field workflow.",
                readinessHeadline = "No stop selected",
                blockersText = "Choose a stop from Today or Queue to start the workflow.",
                localProgressText = "Notes, photos, and closeout readiness will appear here."
            )
            return
        }
        val workflowSummary = JobWorkflow.summarize(job)
        val workflowState = localStateRepository.getJobWorkflowState(job.id)
        val completionSummary = JobExecutionAssist.completionSummary(job, workflowState.asJobProgress())
        val blockersText = if (completionSummary.blockers.isEmpty()) {
            "All required workflow items are in place."
        } else {
            completionSummary.blockers.joinToString("\n") { "• $it" }
        }
        val localProgressText = buildString {
            append("Notes: ")
            append(
                if (workflowState.hasDraft) {
                    "${workflowState.noteDraft?.length ?: 0} chars saved"
                } else {
                    "none yet"
                }
            )
            if (workflowState.notePendingSync) {
                append(" • Pending sync")
            }
            append("\nPhotos: ${workflowState.photoCount}")
            workflowState.lastPhotoLabel?.takeIf { it.isNotBlank() }?.let {
                append(" • Last: $it")
            }
            append("\nOutcome: ${workflowState.finalOutcome?.replace('_', ' ') ?: "not chosen"}")
            workflowState.lastAction?.takeIf { it.isNotBlank() && workflowState.appliesLastActionToThisJob }?.let {
                append("\nLast action: $it")
            }
        }
        _summary.value = WorkflowHostSummary(
            workflowHeadline = workflowSummary.headline,
            nextStep = workflowSummary.nextStep,
            readinessHeadline = completionSummary.headline,
            blockersText = blockersText,
            localProgressText = localProgressText
        )
    }

    class Factory(
        private val container: FieldDeskAppContainer
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkflowHostViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WorkflowHostViewModel(
                    localStateRepository = container.localWorkflowStateRepository()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
