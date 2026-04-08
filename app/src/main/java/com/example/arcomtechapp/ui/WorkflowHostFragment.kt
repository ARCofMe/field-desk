package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.databinding.FragmentWorkflowHostBinding
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.viewmodel.SelectedJobState
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel
import com.example.arcomtechapp.viewmodel.WorkflowHostViewModel
import com.example.arcomtechapp.viewmodel.WorkflowPanel

class WorkflowHostFragment : Fragment() {

    private var _binding: FragmentWorkflowHostBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val workflowHostViewModel: WorkflowHostViewModel by viewModels {
        WorkflowHostViewModel.Factory(requireContext().fieldDeskContainer())
    }
    private var activePanel: WorkflowPanel = WorkflowPanel.DETAIL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkflowHostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonWorkflowOverview.setOnClickListener {
            selectedJobViewModel.openWorkflowPanel(WorkflowPanel.DETAIL)
        }
        binding.buttonWorkflowNotes.setOnClickListener {
            selectedJobViewModel.openWorkflowPanel(WorkflowPanel.NOTES)
        }
        binding.buttonWorkflowPhotos.setOnClickListener {
            selectedJobViewModel.openWorkflowPanel(WorkflowPanel.PHOTOS)
        }
        selectedJobViewModel.selectedJob.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }
        workflowHostViewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.textWorkflowHeadline.text = summary.workflowHeadline
            binding.textWorkflowNextStep.text = summary.nextStep
            binding.textWorkflowReadiness.text = summary.readinessHeadline
            binding.textWorkflowBlockers.text = summary.blockersText
            binding.textWorkflowLocalProgress.text = summary.localProgressText
        }
        requireContext().fieldDeskContainer().localWorkflowStateRepository().updatedJobId.observe(viewLifecycleOwner) { jobId ->
            val currentJob = selectedJobViewModel.currentJob() ?: return@observe
            if (jobId == currentJob.id) {
                workflowHostViewModel.load(currentJob)
            }
        }
    }

    private fun renderState(state: SelectedJobState) {
        val job = state.job
        val panel = state.workflowPanel
        binding.textWorkflowHostEyebrow.text = getString(
            when (panel) {
                WorkflowPanel.DETAIL -> R.string.fielddesk_workflow_eyebrow_detail
                WorkflowPanel.NOTES -> R.string.fielddesk_workflow_eyebrow_notes
                WorkflowPanel.PHOTOS -> R.string.fielddesk_workflow_eyebrow_photos
            }
        )
        binding.textWorkflowHostTitle.text = buildTitle(job)
        binding.textWorkflowHostSummary.text = buildSummary(job, panel)
        binding.layoutWorkflowEmptyState.isVisible = job == null
        binding.workflowChildContainer.isVisible = job != null
        binding.cardWorkflowSummary.isVisible = job != null
        binding.buttonWorkflowOverview.isEnabled = panel != WorkflowPanel.DETAIL
        binding.buttonWorkflowNotes.isEnabled = panel != WorkflowPanel.NOTES
        binding.buttonWorkflowPhotos.isEnabled = panel != WorkflowPanel.PHOTOS
        workflowHostViewModel.load(job)
        if (job == null) {
            activePanel = panel
            return
        }
        if (activePanel != panel || childFragmentManager.findFragmentById(R.id.workflow_child_container) == null) {
            activePanel = panel
            childFragmentManager.beginTransaction()
                .replace(R.id.workflow_child_container, panel.fragment(), panel.tag)
                .commit()
        }
    }

    private fun buildTitle(job: Job?): String {
        if (job == null) return getString(R.string.fielddesk_workflow_no_job_title)
        return buildString {
            append("#${job.id}")
            if (job.customerName.isNotBlank()) {
                append(" • ${job.customerName}")
            }
        }
    }

    private fun buildSummary(job: Job?, panel: WorkflowPanel): String {
        if (job == null) return getString(R.string.fielddesk_workflow_no_job_summary)
        val location = job.address.takeIf { it.isNotBlank() } ?: getString(R.string.fielddesk_workflow_missing_address)
        val panelSummary = getString(
            when (panel) {
                WorkflowPanel.DETAIL -> R.string.fielddesk_workflow_summary_detail
                WorkflowPanel.NOTES -> R.string.fielddesk_workflow_summary_notes
                WorkflowPanel.PHOTOS -> R.string.fielddesk_workflow_summary_photos
            }
        )
        return listOf(job.appointmentWindow, location, panelSummary)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    private fun WorkflowPanel.fragment(): Fragment = when (this) {
        WorkflowPanel.DETAIL -> JobDetailFragment()
        WorkflowPanel.NOTES -> NotesFragment()
        WorkflowPanel.PHOTOS -> PhotosFragment()
    }

    private val WorkflowPanel.tag: String
        get() = when (this) {
            WorkflowPanel.DETAIL -> "workflow-detail"
            WorkflowPanel.NOTES -> "workflow-notes"
            WorkflowPanel.PHOTOS -> "workflow-photos"
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
