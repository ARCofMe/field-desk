package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.databinding.FragmentNotesBinding
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.viewmodel.JobWorkflowViewModel
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val workflowViewModel: JobWorkflowViewModel by viewModels {
        JobWorkflowViewModel.Factory(requireContext().fieldDeskContainer())
    }
    private lateinit var storage: Storage
    private var job: Job? = null
    private var suppressDraftWatcher: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textNotesMeta.text = buildMeta()
        binding.textNoteGuidance.text = buildNoteGuidance()
        binding.textTemplateOne.setOnClickListener { appendTemplate(0) }
        binding.textTemplateTwo.setOnClickListener { appendTemplate(1) }
        binding.textTemplateThree.setOnClickListener { appendTemplate(2) }
        binding.textTemplateFour.setOnClickListener { appendTemplate(3) }
        applyTemplateLabels()
        binding.buttonSendNote.text = "Sync note to Ops Hub"
        observeWorkflowState()
        selectedJobViewModel.selectedJob.observe(viewLifecycleOwner) { state ->
            val selectedJob = state.job ?: return@observe
            if (selectedJob.id != job?.id) {
                job = selectedJob
                binding.textNotesMeta.text = buildMeta()
                applyTemplateLabels()
                workflowViewModel.load(selectedJob)
            }
        }
        job = selectedJobViewModel.currentJob()
        job?.let { workflowViewModel.load(it) }
        binding.inputNote.doAfterTextChanged { text ->
            if (suppressDraftWatcher) return@doAfterTextChanged
            job?.let { workflowViewModel.onNoteDraftChanged(it, text?.toString()) }
        }

        binding.buttonSaveDraft.setOnClickListener {
            val note = binding.inputNote.text?.toString().orEmpty()
            val currentJob = job ?: return@setOnClickListener
            workflowViewModel.saveDraft(currentJob, note)
        }

        binding.buttonClearDraft.setOnClickListener {
            val currentJob = job ?: return@setOnClickListener
            workflowViewModel.clearDraft(currentJob)
            suppressDraftWatcher = true
            binding.inputNote.setText("")
            suppressDraftWatcher = false
        }

        binding.buttonSendNote.setOnClickListener {
            val note = binding.inputNote.text?.toString().orEmpty()
            if (note.isBlank()) {
                Toast.makeText(requireContext(), "Nothing to send yet", Toast.LENGTH_SHORT).show()
            } else {
                val currentJob = job
                if (currentJob == null) {
                    Toast.makeText(requireContext(), "Open notes from a job to sync them", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                binding.buttonSendNote.isEnabled = false
                workflowViewModel.syncNote(currentJob, note)
            }
        }
    }

    private fun observeWorkflowState() {
        workflowViewModel.workflowState.observe(viewLifecycleOwner) { state ->
            suppressDraftWatcher = true
            if (binding.inputNote.text?.toString() != state.noteDraft.orEmpty()) {
                binding.inputNote.setText(state.noteDraft.orEmpty())
                binding.inputNote.setSelection(binding.inputNote.text?.length ?: 0)
            }
            suppressDraftWatcher = false
            updateDraftStatus(state)
            binding.textNoteGuidance.text = buildNoteGuidance(state)
        }
        workflowViewModel.actionMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                binding.buttonSendNote.isEnabled = true
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildMeta(): String {
        val parts = mutableListOf<String>()
        job?.let {
            parts += "Job #${it.id}"
            if (it.customerName.isNotBlank()) parts += it.customerName
            if (!it.equipment.isNullOrBlank()) parts += it.equipment
            it.partsStage?.takeIf { stage -> stage.isNotBlank() }?.let { stage -> parts += "Parts: $stage" }
            it.nextAction?.takeIf { action -> action.isNotBlank() }?.let { action -> parts += "Next: $action" }
        }
        parts += storage.getTechnicianName()?.ifBlank { "Technician" } ?: "Technician"
        storage.getTechnicianId()?.let { if (it.isNotBlank()) parts += "ID: $it" }
        parts += when (storage.getBackendMode()) {
            Storage.BackendMode.OPS_HUB -> "Ops Hub"
            Storage.BackendMode.BLUEFOLDER_DIRECT -> "BlueFolder"
        }
        parts += storage.getActiveBaseUrl()?.ifBlank { "No server URL" } ?: "No server URL"
        parts += if (storage.getActiveApiKey().isNullOrBlank()) "API key missing" else "API ready"
        return parts.joinToString(" • ")
    }

    private fun updateDraftStatus(state: com.example.arcomtechapp.data.models.JobWorkflowState?) {
        val draft = state?.noteDraft
        val closeout = job?.let {
            JobExecutionAssist.completionSummary(it, state?.asJobProgress() ?: com.example.arcomtechapp.workflow.JobProgress())
        }
        binding.textDraftStatus.text = if (draft.isNullOrBlank()) {
            "No draft saved${closeout?.let { ". ${it.headline}" } ?: ""}"
        } else {
            buildString {
                append("Draft saved (${draft.length} chars)")
                if (state?.notePendingSync == true) {
                    append("\nPending sync to Ops Hub")
                } else if (!state?.noteLastSyncMessage.isNullOrBlank()) {
                    append("\nLast sync: ${state?.noteLastSyncMessage}")
                }
                closeout?.let {
                    append("\n${it.headline}")
                    if (it.blockers.isNotEmpty()) {
                        append("\n")
                        append(it.blockers.joinToString("\n") { blocker -> "○ $blocker" })
                    }
                }
            }
        }
    }

    private fun buildNoteGuidance(state: com.example.arcomtechapp.data.models.JobWorkflowState? = null): String {
        val currentJob = job ?: return "Use the note blocks to capture the field story cleanly."
        return JobExecutionAssist.noteGuidance(currentJob, state?.finalOutcome)
    }

    private fun applyTemplateLabels() {
        val templates = templateSet()
        binding.textTemplateOne.text = templates.getOrNull(0)?.label ?: "Arrival"
        binding.textTemplateTwo.text = templates.getOrNull(1)?.label ?: "Diagnosis"
        binding.textTemplateThree.text = templates.getOrNull(2)?.label ?: "Parts"
        binding.textTemplateFour.text = templates.getOrNull(3)?.label ?: "Closeout"
    }

    private fun templateSet() = JobExecutionAssist.noteTemplates(
        job ?: Job(
            id = "local",
            address = "",
            appointmentWindow = "",
            customerName = "Customer",
            customerPhone = "",
            status = "Pending",
            distanceMiles = null,
            equipment = null
        )
    )

    private fun appendTemplate(index: Int) {
        val template = templateSet().getOrNull(index) ?: return
        val current = binding.inputNote.text?.toString().orEmpty().trim()
        val updated = if (current.contains(template.body)) {
            current
        } else {
            listOf(current, template.body).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        binding.inputNote.setText(updated)
        binding.inputNote.setSelection(updated.length)
        binding.textNoteGuidance.text = buildNoteGuidance()
        updateDraftStatus(workflowViewModel.workflowState.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        job?.let { workflowViewModel.onNoteDraftChanged(it, binding.inputNote.text?.toString()) }
    }
}
