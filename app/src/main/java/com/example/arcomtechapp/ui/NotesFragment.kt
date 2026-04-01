package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.databinding.FragmentNotesBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.serializableCompat
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.workflow.JobProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = arguments?.serializableCompat(ARG_JOB)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textNotesMeta.text = buildMeta()
        binding.textTemplateOne.setOnClickListener { appendTemplate(0) }
        binding.textTemplateTwo.setOnClickListener { appendTemplate(1) }
        binding.textTemplateThree.setOnClickListener { appendTemplate(2) }
        binding.textTemplateFour.setOnClickListener { appendTemplate(3) }
        binding.inputNote.setText(storage.getJobNotesDraft(job?.id).orEmpty())
        updateDraftStatus()

        binding.buttonSaveDraft.setOnClickListener {
            val note = binding.inputNote.text?.toString().orEmpty()
            storage.setJobNotesDraft(job?.id, note)
            if (job != null) {
                storage.saveLastJobAction(job?.id, "Saved guided note draft")
            } else {
                storage.setNotesDraft(note)
            }
            updateDraftStatus()
            Toast.makeText(requireContext(), "Draft saved locally", Toast.LENGTH_SHORT).show()
        }

        binding.buttonClearDraft.setOnClickListener {
            storage.clearJobNotesDraft(job?.id)
            if (job == null) {
                storage.clearNotesDraft()
            }
            binding.inputNote.setText("")
            updateDraftStatus()
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
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = RepositoryProvider.fromContext(requireContext()).submitJobNote(
                        storage.getActiveBaseUrl(),
                        storage.getActiveApiKey(),
                        currentJob.id,
                        note
                    )
                    withContext(Dispatchers.Main) {
                        binding.buttonSendNote.isEnabled = true
                        storage.saveLastJobAction(currentJob.id, result.message)
                        storage.setJobNotesDraft(currentJob.id, note)
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        updateDraftStatus()
                    }
                }
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
        parts += storage.getBaseUrl()?.ifBlank { "No subdomain" } ?: "No subdomain"
        parts += if (storage.getApiKey().isNullOrBlank()) "API key missing" else "API ready"
        return parts.joinToString(" • ")
    }

    private fun updateDraftStatus() {
        val draft = storage.getJobNotesDraft(job?.id)
        val progress = storage.getLocalJobProgress(job?.id)
        val closeout = job?.let {
            JobExecutionAssist.completionSummary(
                it,
                JobProgress(
                    noteDraftLength = draft?.length ?: 0,
                    photoCount = progress.photoCount,
                    lastPhotoLabel = progress.lastPhotoLabel
                )
            )
        }
        binding.textDraftStatus.text = if (draft.isNullOrBlank()) {
            "No draft saved${closeout?.let { ". ${it.headline}" } ?: ""}"
        } else {
            buildString {
                append("Draft saved (${draft.length} chars)")
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

    private fun appendTemplate(index: Int) {
        val template = JobExecutionAssist.noteTemplates(
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
        ).getOrNull(index) ?: return
        val current = binding.inputNote.text?.toString().orEmpty().trim()
        val updated = if (current.contains(template.body)) {
            current
        } else {
            listOf(current, template.body).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        binding.inputNote.setText(updated)
        binding.inputNote.setSelection(updated.length)
        updateDraftStatus()
    }

    companion object {
        private const val ARG_JOB = "arg_job"

        fun newInstance(job: Job): NotesFragment {
            val fragment = NotesFragment()
            fragment.arguments = Bundle().apply {
                putSerializable(ARG_JOB, job)
            }
            return fragment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
