package com.example.arcomtechapp.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.databinding.FragmentPhotosBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.serializableCompat
import com.example.arcomtechapp.workflow.JobExecutionAssist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotosFragment : Fragment() {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage
    private var job: Job? = null
    private var selectedPromptIndex: Int = 0
    private var livePhotoStatus: JobPhotoStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = arguments?.serializableCompat(ARG_JOB)
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            binding.imagePreview.setImageBitmap(it)
            onPhotoCaptured("Captured ${selectedPhotoLabel().lowercase()} from camera")
        } ?: updateStatus("Camera canceled")
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            binding.imagePreview.setImageURI(uri)
            onPhotoCaptured("Attached ${selectedPhotoLabel().lowercase()} from gallery")
        } else {
            updateStatus("Gallery canceled")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.switchInlineCompress.isChecked = storage.shouldAutoCompressPhotos()
        binding.switchInlineCompress.setOnCheckedChangeListener { _, isChecked ->
            storage.setAutoCompressPhotos(isChecked)
            updateStatus(if (isChecked) "Auto-compress enabled" else "Auto-compress disabled")
        }
        bindPromptButtons()

        binding.buttonCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.buttonGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.buttonOptimize.setOnClickListener {
            val currentJob = job
            if (currentJob == null) {
                updateStatus("Prepared ${selectedPhotoLabel().lowercase()} package for upload")
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).evaluatePhotoCompliance(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    currentJob.id,
                    sendNotice = false
                )
                withContext(Dispatchers.Main) {
                    updateStatus(result.message.ifBlank { "Photo compliance checked" })
                    refreshPhotoStatus()
                }
            }
        }
        binding.buttonEmailUpload.setOnClickListener {
            val currentJob = job
            if (currentJob == null) {
                Toast.makeText(requireContext(), "Open photos from a job to sync them", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.buttonEmailUpload.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).preparePhotoUpload(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    currentJob.id,
                    selectedPhotoLabel()
                )
                withContext(Dispatchers.Main) {
                    binding.buttonEmailUpload.isEnabled = true
                    storage.saveLastJobAction(currentJob.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    renderProgress()
                    refreshPhotoStatus()
                }
            }
        }

        binding.textPhotoConfig.text = buildConfigText()
        binding.textPhotoJob.text = buildJobHeader()
        updatePhotoPromptState()
        renderProgress()
        updateStatus("Ready for guided photo capture")
        refreshPhotoStatus()
    }

    private fun buildConfigText(): String {
        val parts = mutableListOf<String>()
        parts += storage.getBaseUrl()?.ifBlank { "No base URL" } ?: "No base URL"
        parts += if (storage.getApiKey().isNullOrBlank()) "API key missing" else "API key set"
        parts += if (storage.shouldAutoCompressPhotos()) "Auto-compress ON" else "Auto-compress OFF"
        return parts.joinToString(" • ")
    }

    private fun buildJobHeader(): String {
        val currentJob = job ?: return "General photo workflow"
        return buildString {
            append("Job #${currentJob.id}")
            if (currentJob.customerName.isNotBlank()) append(" • ${currentJob.customerName}")
            if (!currentJob.equipment.isNullOrBlank()) append(" • ${currentJob.equipment}")
        }
    }

    private fun bindPromptButtons() {
        binding.buttonPhotoTypeOne.setOnClickListener { setPrompt(0) }
        binding.buttonPhotoTypeTwo.setOnClickListener { setPrompt(1) }
        binding.buttonPhotoTypeThree.setOnClickListener { setPrompt(2) }
    }

    private fun setPrompt(index: Int) {
        selectedPromptIndex = index
        updatePhotoPromptState()
    }

    private fun updatePhotoPromptState() {
        val prompts = currentPrompts()
        binding.buttonPhotoTypeOne.text = prompts.getOrNull(0)?.label ?: "Primary"
        binding.buttonPhotoTypeTwo.text = prompts.getOrNull(1)?.label ?: "Support"
        binding.buttonPhotoTypeThree.text = prompts.getOrNull(2)?.label ?: "Issue"
        binding.textPhotoPrompt.text = prompts.getOrNull(selectedPromptIndex)?.helper ?: "Capture supporting field photos."
        val progress = storage.getLocalJobProgress(job?.id)
        binding.textPhotoChecklist.text = JobExecutionAssist.photoChecklist(
            job ?: Job(
                id = "local",
                address = "",
                appointmentWindow = "",
                customerName = "Customer",
                customerPhone = "",
                status = "Pending",
                distanceMiles = null,
                equipment = null
            ),
            progress.photoCount,
            progress.lastPhotoLabel
        ).joinToString("\n") { "• $it" }
    }

    private fun currentPrompts() = JobExecutionAssist.photoPrompts(
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

    private fun selectedPhotoLabel(): String =
        currentPrompts().getOrNull(selectedPromptIndex)?.label ?: "Job photo"

    private fun onPhotoCaptured(status: String) {
        job?.id?.let { jobId ->
            storage.recordJobPhotoCapture(jobId, selectedPhotoLabel())
            storage.saveLastJobAction(jobId, "Captured ${selectedPhotoLabel().lowercase()}")
        }
        renderProgress()
        updateStatus(status)
    }

    private fun renderProgress() {
        val progress = storage.getLocalJobProgress(job?.id)
        binding.textPhotoProgress.text = buildString {
            append("Photos captured: ${progress.photoCount}")
            progress.lastPhotoLabel?.takeIf { it.isNotBlank() }?.let {
                append("\nLast capture: $it")
            }
        }
        binding.textPhotoCompliance.text = buildPhotoComplianceText()
    }

    private fun updateStatus(status: String) {
        binding.textPhotoStatus.isVisible = true
        binding.textPhotoStatus.text = status
    }

    private fun refreshPhotoStatus() {
        val currentJob = job ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val status = RepositoryProvider.fromContext(requireContext()).getJobPhotoStatus(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                currentJob.id
            )
            withContext(Dispatchers.Main) {
                livePhotoStatus = status
                renderProgress()
            }
        }
    }

    private fun buildPhotoComplianceText(): String {
        val status = livePhotoStatus ?: return "Compliance state will appear here after Ops Hub responds."
        return buildString {
            append(status.message.ifBlank { "Mailbox status: ${status.mailboxStatus}" })
            if (status.missingTags.isNotEmpty()) {
                append("\nMissing tags: ${status.missingTags.joinToString(", ")}")
            }
            if (status.foundTags.isNotEmpty()) {
                append("\nFound tags: ${status.foundTags.joinToString(", ")}")
            }
            val prompts = currentPrompts().map { it.label }
            if (prompts.isNotEmpty()) {
                append("\nSuggested set: ${prompts.joinToString(", ")}")
            }
        }
    }

    companion object {
        private const val ARG_JOB = "arg_job"

        fun newInstance(job: Job): PhotosFragment {
            val fragment = PhotosFragment()
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
