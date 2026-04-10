package com.example.arcomtechapp.ui

import android.graphics.Bitmap
import android.webkit.MimeTypeMap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.repo.PhotoUploadRequest
import com.example.arcomtechapp.databinding.FragmentPhotosBinding
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.workflow.JobExecutionAssist
import java.io.ByteArrayOutputStream
import com.example.arcomtechapp.viewmodel.JobWorkflowViewModel
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel

class PhotosFragment : Fragment() {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val workflowViewModel: JobWorkflowViewModel by viewModels {
        JobWorkflowViewModel.Factory(requireContext().fieldDeskContainer())
    }
    private lateinit var storage: Storage
    private var job: Job? = null
    private var selectedPromptIndex: Int = 0
    private var livePhotoStatus: JobPhotoStatus? = null
    private var pendingUpload: PhotoUploadRequest? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            binding.imagePreview.setImageBitmap(it)
            pendingUpload = buildCameraUploadRequest(it)
            onPhotoCaptured("Captured ${selectedPhotoLabel().lowercase()} from camera")
        } ?: updateStatus("Camera canceled")
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            binding.imagePreview.setImageURI(uri)
            pendingUpload = buildGalleryUploadRequest(uri.toString())
            if (pendingUpload == null) {
                updateStatus("Could not read the selected photo")
            } else {
                onPhotoCaptured("Attached ${selectedPhotoLabel().lowercase()} from gallery")
            }
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
        binding.switchInlineCompress.isChecked = workflowViewModel.isAutoCompressEnabled()
        binding.switchInlineCompress.setOnCheckedChangeListener { _, isChecked ->
            workflowViewModel.setAutoCompressPhotos(isChecked)
        }
        bindPromptButtons()
        observeWorkflowState()
        selectedJobViewModel.selectedJob.observe(viewLifecycleOwner) { state ->
            val selectedJob = state.job ?: return@observe
            if (selectedJob.id != job?.id) {
                job = selectedJob
                binding.textPhotoConfig.text = buildConfigText()
                binding.textPhotoJob.text = buildJobHeader()
                updatePhotoPromptState()
                renderProgress()
                workflowViewModel.load(selectedJob)
                updateJobBoundState()
            }
        }

        binding.buttonCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.buttonGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.buttonOptimize.setOnClickListener {
            val currentJob = job
            if (currentJob == null) {
                updateStatus("Open photos from a job to check compliance")
                return@setOnClickListener
            }
            workflowViewModel.evaluatePhotoCompliance(currentJob)
        }
        binding.buttonEmailUpload.setOnClickListener {
            val currentJob = job
            if (currentJob == null) {
                Toast.makeText(requireContext(), "Open photos from a job to attach them", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val upload = pendingUpload
            if (upload == null) {
                Toast.makeText(requireContext(), "Capture or select a photo first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.buttonEmailUpload.isEnabled = false
            workflowViewModel.uploadPhoto(currentJob, upload)
        }

        binding.textPhotoConfig.text = buildConfigText()
        binding.textPhotoJob.text = buildJobHeader()
        updatePhotoPromptState()
        renderProgress()
        updateJobBoundState()
        updateStatus("Ready for guided photo capture")
        job = selectedJobViewModel.currentJob()
        job?.let { workflowViewModel.load(it) }
        updateJobBoundState()
    }

    private fun observeWorkflowState() {
        workflowViewModel.workflowState.observe(viewLifecycleOwner) {
            renderProgress()
            updatePhotoPromptState()
        }
        workflowViewModel.photoStatus.observe(viewLifecycleOwner) { status ->
            livePhotoStatus = status
            renderProgress()
        }
        workflowViewModel.actionMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                binding.buttonEmailUpload.isEnabled = true
                if (message.startsWith("Photo attached")) {
                    pendingUpload = null
                }
                updateStatus(message)
            }
        }
    }

    private fun buildConfigText(): String {
        val parts = mutableListOf<String>()
        parts += when (storage.getBackendMode()) {
            Storage.BackendMode.OPS_HUB -> "Ops Hub backend"
            Storage.BackendMode.BLUEFOLDER_DIRECT -> "BlueFolder direct"
        }
        parts += storage.getActiveBaseUrl()?.ifBlank { "No backend URL" } ?: "No backend URL"
        parts += if (storage.getActiveApiKey().isNullOrBlank()) "API key missing" else "API key set"
        parts += if (workflowViewModel.isAutoCompressEnabled()) "Auto-compress ON" else "Auto-compress OFF"
        return parts.joinToString(" • ")
    }

    private fun buildJobHeader(): String {
        val currentJob = job ?: return getString(com.example.arcomtechapp.R.string.fielddesk_workflow_no_stop_selected)
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
        val progress = job?.id?.let { requireContext().fieldDeskContainer().localWorkflowStateRepository().getJobWorkflowState(it) }
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
            progress?.photoCount ?: 0,
            progress?.lastPhotoLabel
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
        job?.let { workflowViewModel.recordPhotoCaptured(it, selectedPhotoLabel(), status) }
    }

    private fun renderProgress() {
        val progress = job?.id?.let { requireContext().fieldDeskContainer().localWorkflowStateRepository().getJobWorkflowState(it) }
        binding.textPhotoProgress.text = buildString {
            append("Photos captured: ${progress?.photoCount ?: 0}")
            progress?.lastPhotoLabel?.takeIf { it.isNotBlank() }?.let {
                append("\nLast capture: $it")
            }
            pendingUpload?.let {
                append("\nReady to attach: ${it.filename}")
            }
        }
        binding.textPhotoCompliance.text = buildPhotoComplianceText()
        updateUploadControls()
        updateJobBoundState()
    }

    private fun updateStatus(status: String) {
        binding.textPhotoStatus.isVisible = true
        binding.textPhotoStatus.text = status
    }

    private fun buildPhotoComplianceText(): String {
        if (job == null) {
            return getString(com.example.arcomtechapp.R.string.fielddesk_workflow_choose_stop_guidance)
        }
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
            if (pendingUpload != null) {
                append("\nSelected photo is only local until you attach it to the SR.")
            }
        }
    }

    private fun updateUploadControls() {
        val hasPendingUpload = pendingUpload != null
        val hasJob = job != null
        val canAttachPhoto = storage.getBackendMode() == Storage.BackendMode.OPS_HUB
        binding.buttonEmailUpload.isEnabled = hasPendingUpload && hasJob && canAttachPhoto
        binding.buttonEmailUpload.text = if (!canAttachPhoto) {
            "Photo attach requires Ops Hub backend"
        } else if (hasPendingUpload) {
            "Attach ${selectedPhotoLabel()} to SR"
        } else {
            "Attach photo to SR"
        }
        if (!hasJob || !canAttachPhoto) {
            binding.buttonEmailUpload.isEnabled = false
        }
    }

    private fun updateJobBoundState() {
        val hasJob = job != null
        binding.buttonPhotoTypeOne.isEnabled = hasJob
        binding.buttonPhotoTypeTwo.isEnabled = hasJob
        binding.buttonPhotoTypeThree.isEnabled = hasJob
        binding.buttonCamera.isEnabled = hasJob
        binding.buttonGallery.isEnabled = hasJob
        binding.buttonOptimize.isEnabled = hasJob
        if (!hasJob) {
            binding.textPhotoJob.text = getString(com.example.arcomtechapp.R.string.fielddesk_workflow_no_stop_selected)
            binding.textPhotoPrompt.text = getString(com.example.arcomtechapp.R.string.fielddesk_workflow_choose_stop_guidance)
            binding.textPhotoStatus.text = getString(com.example.arcomtechapp.R.string.fielddesk_workflow_choose_stop_guidance)
            binding.buttonEmailUpload.isEnabled = false
        }
    }

    private fun buildCameraUploadRequest(bitmap: Bitmap): PhotoUploadRequest {
        val stream = ByteArrayOutputStream()
        val quality = if (storage.shouldAutoCompressPhotos()) 82 else 95
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return PhotoUploadRequest(
            filename = buildPhotoFilename("jpg"),
            contentType = "image/jpeg",
            data = stream.toByteArray(),
            label = selectedPhotoLabel()
        )
    }

    private fun buildGalleryUploadRequest(uriText: String): PhotoUploadRequest? {
        val uri = android.net.Uri.parse(uriText)
        val resolver = requireContext().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val contentType = resolver.getType(uri) ?: "image/jpeg"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType) ?: "jpg"
        val filename = buildPhotoFilename(extension)
        return PhotoUploadRequest(
            filename = filename,
            contentType = contentType,
            data = bytes,
            label = selectedPhotoLabel()
        )
    }

    private fun buildPhotoFilename(extension: String): String {
        val jobId = job?.id ?: "local"
        val label = selectedPhotoLabel()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "photo" }
        return "sr-$jobId-$label.$extension"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
