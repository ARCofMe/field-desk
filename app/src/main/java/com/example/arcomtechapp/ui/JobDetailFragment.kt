package com.example.arcomtechapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import android.widget.Toast
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.databinding.FragmentJobDetailBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.serializableCompat
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.workflow.JobProgress
import com.example.arcomtechapp.workflow.JobWorkflow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JobDetailFragment : Fragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private var job: Job? = null
    private lateinit var storage: Storage
    private var launchCallOnOpen: Boolean = false
    private var launchNavigationOnOpen: Boolean = false
    private var partsCase: JobPartsCase? = null
    private var photoStatus: JobPhotoStatus? = null
    private var timeline: List<JobTimelineEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = arguments?.serializableCompat(ARG_JOB)
        launchCallOnOpen = arguments?.getBoolean(ARG_LAUNCH_CALL, false) == true
        launchNavigationOnOpen = arguments?.getBoolean(ARG_LAUNCH_NAVIGATION, false) == true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobDetailBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        job?.let { renderJob(it) }
        refreshJobContext()
    }

    override fun onResume() {
        super.onResume()
        job?.let { renderJob(it) }
        refreshJobContext()
    }

    private fun renderJob(job: Job) {
        val summary = JobWorkflow.summarize(job)
        val progress = storage.getLocalJobProgress(job.id)
        val closeout = JobExecutionAssist.completionSummary(
            job,
            JobProgress(
                noteDraftLength = progress.noteDraft?.length ?: 0,
                photoCount = progress.photoCount,
                lastPhotoLabel = progress.lastPhotoLabel
            )
        )
        binding.textJobId.text = job.id
        binding.textCustomerName.text = job.customerName
        binding.textJobStatus.text = "${summary.headline} • ${job.status}"
        binding.textAppointment.text = job.appointmentWindow
        binding.textAddress.text = job.address
        binding.textPhone.text = job.customerPhone
        binding.textDistance.text = job.distanceMiles?.let { String.format("%.1f mi away", it) } ?: "N/A"
        binding.textPartsCase.text = buildPartsCaseText(job)
        binding.textPhotoCompliance.text = buildPhotoComplianceText()
        binding.textTimelinePreview.text = buildTimelinePreview()
        binding.textWorkflowHeadline.text = summary.nextStep
        binding.textWorkflowChecklist.text = summary.checklist.joinToString("\n") {
            "${if (it.done) "•" else "○"} ${it.label}"
        }
        binding.textLocalProgress.text = buildString {
            append(closeout.headline)
            append("\nNotes: ")
            append(
                if ((progress.noteDraft?.length ?: 0) > 0) {
                    "${progress.noteDraft?.length ?: 0} chars saved"
                } else {
                    "none yet"
                }
            )
            append(" • Photos: ${progress.photoCount}")
            progress.lastPhotoLabel?.takeIf { it.isNotBlank() }?.let {
                append("\nLast photo: $it")
            }
            if (closeout.blockers.isNotEmpty()) {
                append("\n")
                append(closeout.blockers.joinToString("\n") { "○ $it" })
            }
        }
        binding.textCloseoutStatus.text = buildString {
            append("Outcome: ${progress.finalOutcome?.replace('_', ' ') ?: "not chosen"}")
            progress.finalOutcomeNote?.takeIf { it.isNotBlank() }?.let {
                append("\nReason: $it")
            }
            if (closeout.requiredPhotoLabels.isNotEmpty()) {
                append("\nRequired photos: ${closeout.requiredPhotoLabels.joinToString(", ")}")
            }
        }
        binding.buttonPrimaryWorkflow.text = summary.quickActions.firstOrNull()?.label ?: "Open workflow"
        binding.buttonSecondaryWorkflow.text = summary.quickActions.getOrNull(1)?.label ?: "More actions"

        binding.buttonCall.setOnClickListener {
            dialCustomer(job)
        }

        binding.buttonNavigate.setOnClickListener {
            openNavigation(job)
        }
        binding.buttonPrimaryWorkflow.setOnClickListener { handleWorkflowAction(job, summary.quickActions.firstOrNull()?.key) }
        binding.buttonSecondaryWorkflow.setOnClickListener { handleWorkflowAction(job, summary.quickActions.getOrNull(1)?.key) }
        binding.buttonWorkflowPhotos.setOnClickListener { openPhotos(job) }
        binding.buttonWorkflowNotes.setOnClickListener { openNotes(job) }
        binding.buttonWorkflowParts.setOnClickListener {
            promptForPartsRequest(job)
        }
        binding.buttonWorkflowComplete.setOnClickListener {
            promptForCloseout(job)
        }
        binding.buttonWorkflowStart.setOnClickListener { promptForWorkStart(job) }
        binding.buttonWorkflowNoAnswer.setOnClickListener { promptForNoAnswer(job) }
        binding.buttonWorkflowNotHome.setOnClickListener { promptForNotHome(job) }

        if (launchCallOnOpen) {
            launchCallOnOpen = false
            dialCustomer(job)
        } else if (launchNavigationOnOpen) {
            launchNavigationOnOpen = false
            openNavigation(job)
        }
    }

    private fun refreshJobContext() {
        val currentJob = job ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = RepositoryProvider.fromContext(requireContext())
            val refreshedJob = repo.getJob(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                currentJob.id
            ) ?: currentJob
            val refreshedPartsCase = repo.getJobPartsCase(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                currentJob.id
            )
            val refreshedPhotoStatus = repo.getJobPhotoStatus(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                currentJob.id
            )
            val refreshedTimeline = repo.getJobTimeline(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                currentJob.id
            )
            withContext(Dispatchers.Main) {
                job = refreshedJob
                partsCase = refreshedPartsCase
                photoStatus = refreshedPhotoStatus
                timeline = refreshedTimeline
                renderJob(refreshedJob)
            }
        }
    }

    private fun dialCustomer(job: Job) {
        storage.saveLastJobAction(job.id, "Called customer")
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.customerPhone}")))
    }

    private fun openNavigation(job: Job) {
        storage.saveLastJobAction(job.id, "Opened navigation")
        val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(job.address)}")
        startActivity(Intent(Intent.ACTION_VIEW, mapUri))
    }

    private fun openNotes(job: Job) {
        storage.saveLastJobAction(job.id, "Opened guided note")
        parentFragmentManager.beginTransaction()
            .replace(R.id.content_frame, NotesFragment.newInstance(job))
            .addToBackStack(null)
            .commit()
    }

    private fun openPhotos(job: Job) {
        storage.saveLastJobAction(job.id, "Opened photo capture")
        parentFragmentManager.beginTransaction()
            .replace(R.id.content_frame, PhotosFragment.newInstance(job))
            .addToBackStack(null)
            .commit()
    }

    private fun handleWorkflowAction(job: Job, key: String?) {
        when (key) {
            "call" -> dialCustomer(job)
            "call_ahead" -> performCallAhead(job)
            "navigate", "next_job" -> openNavigation(job)
            "photos" -> openPhotos(job)
            "notes" -> openNotes(job)
            "start" -> promptForWorkStart(job)
            "no_answer" -> promptForNoAnswer(job)
            "not_home" -> promptForNotHome(job)
            "quote_needed" -> promptForQuoteNeeded(job)
            "reschedule" -> promptForReschedule(job)
            "parts" -> promptForPartsRequest(job)
            "complete" -> promptForCloseout(job)
            "arrive", "enroute" -> {
                val fallbackLabel = when (key) {
                    "arrive" -> "Marked arrived"
                    else -> "Marked en route"
                }
                performStatusAction(job, key.orEmpty(), fallbackLabel)
            }
            else -> Toast.makeText(requireContext(), "More guided actions coming next.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForWorkStart(job: Job) {
        promptForOptionalText(
            title = "Start work",
            hint = "Optional start note or work plan"
        ) { details ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).logWorkStart(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    details
                )
                withContext(Dispatchers.Main) {
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun performStatusAction(job: Job, actionKey: String, fallbackLabel: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = RepositoryProvider.fromContext(requireContext())
            val result = repo.updateJobStatus(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                job.id,
                actionKey
            )
            withContext(Dispatchers.Main) {
                val message = if (result.message.isNotBlank()) result.message else fallbackLabel
                storage.saveLastJobAction(job.id, message)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                refreshJobContext()
            }
        }
    }

    private fun promptForNoAnswer(job: Job) {
        promptForText(
            title = "No answer",
            hint = "Phone attempts or contact details"
        ) { details ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).reportNoAnswer(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    details
                )
                withContext(Dispatchers.Main) {
                    storage.setJobFinalOutcome(job.id, "unable_to_complete", details)
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun promptForNotHome(job: Job) {
        promptForText(
            title = "Not home",
            hint = "Proof-of-visit context or site details"
        ) { details ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).reportNotHome(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    details
                )
                withContext(Dispatchers.Main) {
                    storage.setJobFinalOutcome(job.id, "unable_to_complete", details)
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun promptForCloseout(job: Job) {
        val options = arrayOf("Completed", "Unable to complete")
        AlertDialog.Builder(requireContext())
            .setTitle("Final outcome")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> completeJob(job)
                    1 -> promptForUnableToComplete(job)
                }
            }
            .show()
    }

    private fun completeJob(job: Job) {
        val progress = storage.getLocalJobProgress(job.id)
        storage.setJobFinalOutcome(job.id, "completed", progress.noteDraft)
        val closeout = JobExecutionAssist.completionSummary(
            job,
            JobProgress(
                noteDraftLength = progress.noteDraft?.length ?: 0,
                photoCount = progress.photoCount,
                lastPhotoLabel = progress.lastPhotoLabel,
                finalOutcome = storage.getJobFinalOutcome(job.id),
                finalOutcomeNote = storage.getJobFinalOutcomeNote(job.id)
            )
        )
        if (!closeout.ready) {
            val blocker = closeout.blockers.firstOrNull() ?: closeout.headline
            storage.saveLastJobAction(job.id, blocker)
            Toast.makeText(requireContext(), blocker, Toast.LENGTH_SHORT).show()
            renderJob(job)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = RepositoryProvider.fromContext(requireContext()).updateJobStatus(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                job.id,
                "complete"
            )
            withContext(Dispatchers.Main) {
                storage.saveLastJobAction(job.id, result.message)
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                refreshJobContext()
            }
        }
    }

    private fun promptForUnableToComplete(job: Job) {
        promptForText(
            title = "Unable to complete",
            hint = "Explain what blocked closeout or completion"
        ) { reason ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).reportUnableToComplete(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    reason
                )
                withContext(Dispatchers.Main) {
                    storage.setJobFinalOutcome(job.id, "unable_to_complete", reason)
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun performCallAhead(job: Job) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = RepositoryProvider.fromContext(requireContext()).logCallAhead(
                storage.getActiveBaseUrl(),
                storage.getActiveApiKey(),
                job.id,
                30
            )
            withContext(Dispatchers.Main) {
                val message = if (result.message.isNotBlank()) result.message else "Call-ahead logged"
                storage.saveLastJobAction(job.id, message)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                refreshJobContext()
                dialCustomer(job)
            }
        }
    }

    private fun promptForPartsRequest(job: Job) {
        promptForText(
            title = "Need part",
            hint = "Part number, symptom, vendor, or other field details"
        ) { details ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).createPartsRequest(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    details.ifBlank { "Requested from mobile technician workflow" }
                )
                withContext(Dispatchers.Main) {
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun promptForQuoteNeeded(job: Job) {
        promptForText(
            title = "Quote needed",
            hint = "Explain what office needs to quote or approve"
        ) { details ->
            val subtype = inferQuoteSubtype(details)
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).reportQuoteNeeded(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    details,
                    subtype
                )
                withContext(Dispatchers.Main) {
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun promptForReschedule(job: Job) {
        promptForText(
            title = "Need reschedule",
            hint = "Why this job needs dispatch follow-up"
        ) { reason ->
            lifecycleScope.launch(Dispatchers.IO) {
                val result = RepositoryProvider.fromContext(requireContext()).reportReschedule(
                    storage.getActiveBaseUrl(),
                    storage.getActiveApiKey(),
                    job.id,
                    reason
                )
                withContext(Dispatchers.Main) {
                    storage.saveLastJobAction(job.id, result.message)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    refreshJobContext()
                }
            }
        }
    }

    private fun promptForText(title: String, hint: String, onSubmit: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            setHint(hint)
            minLines = 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val value = input.text?.toString().orEmpty().trim()
                if (value.isBlank()) {
                    Toast.makeText(requireContext(), "Details are required.", Toast.LENGTH_SHORT).show()
                } else {
                    onSubmit(value)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptForOptionalText(title: String, hint: String, onSubmit: (String?) -> Unit) {
        val input = EditText(requireContext()).apply {
            setHint(hint)
            minLines = 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val value = input.text?.toString().orEmpty().trim()
                onSubmit(value.ifBlank { null })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildPartsCaseText(job: Job): String {
        val case = partsCase
        if (case == null) {
            return buildString {
                append("Parts: ")
                append(job.partsStage ?: "No active parts case")
                job.nextAction?.takeIf { it.isNotBlank() }?.let {
                    append("\nNext action: $it")
                }
            }
        }
        return buildString {
            append("Parts: ${case.stageLabel.ifBlank { case.stage.ifBlank { "No active parts case" } }}")
            case.blocker?.takeIf { it.isNotBlank() }?.let { append("\nBlocker: $it") }
            case.latestStatusText?.takeIf { it.isNotBlank() }?.let { append("\nLatest: $it") }
            case.nextAction?.takeIf { it.isNotBlank() }?.let { append("\nNext action: $it") }
        }
    }

    private fun buildPhotoComplianceText(): String {
        val status = photoStatus ?: return "Photo compliance: live mailbox state not loaded yet."
        return buildString {
            append("Photo compliance: ${status.totalPhotos} found")
            if (status.missingTags.isNotEmpty()) {
                append("\nMissing: ${status.missingTags.joinToString(", ")}")
            } else if (status.foundTags.isNotEmpty()) {
                append("\nTagged: ${status.foundTags.joinToString(", ")}")
            }
            if (status.shouldNotify) {
                append("\nAttention: ${status.reason}")
            }
        }
    }

    private fun buildTimelinePreview(): String {
        if (timeline.isEmpty()) return "Timeline: waiting for workflow events."
        return buildString {
            append("Latest updates:")
            timeline.take(3).forEach { entry ->
                append("\n• ${entry.summary}")
                entry.actorLabel?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
            }
        }
    }

    private fun inferQuoteSubtype(details: String): String {
        val normalized = details.lowercase()
        return when {
            "landlord" in normalized || "tenant" in normalized -> "landlord"
            "prepay" in normalized || "pre-payment" in normalized || "cod" in normalized -> "prepayment"
            else -> "customer"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_JOB = "arg_job"
        private const val ARG_LAUNCH_CALL = "arg_launch_call"
        private const val ARG_LAUNCH_NAVIGATION = "arg_launch_navigation"

        fun newInstance(job: Job, launchCall: Boolean = false, launchNavigation: Boolean = false): JobDetailFragment {
            val fragment = JobDetailFragment()
            fragment.arguments = Bundle().apply {
                putSerializable(ARG_JOB, job)
                putBoolean(ARG_LAUNCH_CALL, launchCall)
                putBoolean(ARG_LAUNCH_NAVIGATION, launchNavigation)
            }
            return fragment
        }
    }
}
