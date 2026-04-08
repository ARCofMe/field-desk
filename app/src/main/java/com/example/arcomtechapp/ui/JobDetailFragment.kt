package com.example.arcomtechapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.core.view.isVisible
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.models.JobCloseoutDraft
import com.example.arcomtechapp.data.models.JobPartsCase
import com.example.arcomtechapp.data.models.JobPhotoStatus
import com.example.arcomtechapp.data.models.JobTimelineEntry
import com.example.arcomtechapp.data.repo.TechnicianActionResult
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.databinding.FragmentJobDetailBinding
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.workflow.JobWorkflow
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.arcomtechapp.viewmodel.JobActionEvent
import com.example.arcomtechapp.viewmodel.JobDetailContext
import com.example.arcomtechapp.viewmodel.JobDetailViewModel
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import android.util.Base64

class JobDetailFragment : Fragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val detailViewModel: JobDetailViewModel by viewModels {
        JobDetailViewModel.Factory(requireContext().fieldDeskContainer())
    }
    private var job: Job? = null
    private lateinit var storage: Storage
    private var launchCallOnOpen: Boolean = false
    private var launchNavigationOnOpen: Boolean = false
    private var partsCase: JobPartsCase? = null
    private var photoStatus: JobPhotoStatus? = null
    private var timeline: List<JobTimelineEntry> = emptyList()
    private var lastHandledActionEventId: Long = Long.MIN_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedState = selectedJobViewModel.selectedJob.value
        job = selectedState?.job
        launchCallOnOpen = selectedState?.launchCallOnOpen == true
        launchNavigationOnOpen = selectedState?.launchNavigationOnOpen == true
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
        observeViewModel()
        selectedJobViewModel.selectedJob.observe(viewLifecycleOwner) { state ->
            val selectedJob = state.job
            if (selectedJob != null && selectedJob.id != job?.id) {
                job = selectedJob
                renderJob(selectedJob)
                detailViewModel.loadJobContext(selectedJob)
            }
            if (state.launchCallOnOpen || state.launchNavigationOnOpen) {
                launchCallOnOpen = state.launchCallOnOpen
                launchNavigationOnOpen = state.launchNavigationOnOpen
                selectedJobViewModel.consumeLaunchFlags()
            }
        }
        job?.let { renderJob(it) }
        job?.let { detailViewModel.loadJobContext(it) }
    }

    override fun onResume() {
        super.onResume()
        job?.let { renderJob(it) }
        job?.let { detailViewModel.loadJobContext(it) }
    }

    private fun observeViewModel() {
        detailViewModel.context.observe(viewLifecycleOwner) { context ->
            applyJobContext(context)
        }
        detailViewModel.actionEvents.observe(viewLifecycleOwner) { event ->
            handleActionEvent(event)
        }
        detailViewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                showActionFeedback(error)
            }
        }
        detailViewModel.closeoutPreview.observe(viewLifecycleOwner) { preview ->
            preview?.let {
                showActionFeedback(
                    "Closeout preview: ${it.laborLabel} • ${it.durationLabel} hrs • ${it.signoffLabel}",
                    isError = false
                )
            }
        }
    }

    private fun applyJobContext(context: JobDetailContext) {
        job = context.job
        partsCase = context.partsCase
        photoStatus = context.photoStatus
        timeline = context.timeline
        renderJob(context.job)
    }

    private fun renderJob(job: Job) {
        val summary = JobWorkflow.summarize(job)
        val workflowState = requireContext().fieldDeskContainer().localWorkflowStateRepository().getJobWorkflowState(job.id)
        val closeout = JobExecutionAssist.completionSummary(job, workflowState.asJobProgress())
        binding.textJobId.text = job.id
        binding.textCustomerName.text = job.customerName
        binding.textJobStatus.text = listOf(
            summary.headline,
            job.status,
            job.statusMeta?.categoryLabel
        ).filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")
        binding.textAppointment.text = job.appointmentWindow
        binding.textAddress.text = job.address
        binding.textPhone.text = job.customerPhone
        binding.textDistance.text = job.distanceMiles?.let { String.format("%.1f mi away", it) } ?: "N/A"
        binding.textPartsCase.text = buildPartsCaseText(job)
        binding.textPhotoCompliance.text = buildPhotoComplianceText()
        binding.textTimelinePreview.text = buildTimelinePreview()
        binding.textActionFeedback.isVisible = false
        binding.textWorkflowHeadline.text = summary.nextStep
        binding.textWorkflowChecklist.text = summary.checklist.joinToString("\n") {
            "${if (it.done) "•" else "○"} ${it.label}"
        }
        binding.textLocalProgress.text = buildString {
            append(closeout.headline)
            append("\nNotes: ")
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
            append(" • Photos: ${workflowState.photoCount}")
            workflowState.lastPhotoLabel?.takeIf { it.isNotBlank() }?.let {
                append("\nLast photo: $it")
            }
            if (closeout.blockers.isNotEmpty()) {
                append("\n")
                append(closeout.blockers.joinToString("\n") { "○ $it" })
            }
        }
        binding.textCloseoutStatus.text = buildString {
            append("Outcome: ${workflowState.finalOutcome?.replace('_', ' ') ?: "not chosen"}")
            workflowState.finalOutcomeNote?.takeIf { it.isNotBlank() }?.let {
                append("\nReason: $it")
            }
            workflowState.workStartedAtEpochMillis?.let {
                append("\nWork started: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}")
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

    private fun dialCustomer(job: Job) {
        if (job.customerPhone.isBlank()) {
            showActionFeedback("No customer phone is available for this job.")
            return
        }
        requireContext().fieldDeskContainer().localWorkflowStateRepository().saveLastAction(job.id, "Called customer")
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.customerPhone}")))
    }

    private fun openNavigation(job: Job) {
        if (job.address.isBlank() || job.address.equals("Address not provided", ignoreCase = true)) {
            showActionFeedback("No mappable address is available for this job.")
            return
        }
        requireContext().fieldDeskContainer().localWorkflowStateRepository().saveLastAction(job.id, "Opened navigation")
        val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(job.address)}")
        startActivity(Intent(Intent.ACTION_VIEW, mapUri))
    }

    private fun openNotes(job: Job) {
        requireContext().fieldDeskContainer().localWorkflowStateRepository().saveLastAction(job.id, "Opened guided note")
        selectedJobViewModel.select(job)
        fieldDeskNavigator().openNotes()
    }

    private fun openPhotos(job: Job) {
        requireContext().fieldDeskContainer().localWorkflowStateRepository().saveLastAction(job.id, "Opened photo capture")
        selectedJobViewModel.select(job)
        fieldDeskNavigator().openPhotos()
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
            detailViewModel.logWorkStart(job, details)
        }
    }

    private fun performStatusAction(job: Job, actionKey: String, fallbackLabel: String) {
        detailViewModel.updateStatus(job, actionKey, fallbackLabel)
    }

    private fun promptForNoAnswer(job: Job) {
        promptForText(
            title = "No answer",
            hint = "Phone attempts or contact details"
        ) { details ->
            detailViewModel.reportNoAnswer(job, details)
        }
    }

    private fun promptForNotHome(job: Job) {
        promptForText(
            title = "Not home",
            hint = "Proof-of-visit context or site details"
        ) { details ->
            detailViewModel.reportNotHome(job, details)
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
        val workflowStateRepo = requireContext().fieldDeskContainer().localWorkflowStateRepository()
        val workflowState = workflowStateRepo.getJobWorkflowState(job.id)
        workflowStateRepo.setFinalOutcome(job.id, "completed", workflowState.noteDraft)
        val closeout = JobExecutionAssist.completionSummary(
            job,
            workflowStateRepo.getJobWorkflowState(job.id).asJobProgress()
        )
        if (!closeout.ready) {
            val blocker = closeout.blockers.firstOrNull() ?: closeout.headline
            workflowStateRepo.saveLastAction(job.id, blocker)
            Toast.makeText(requireContext(), blocker, Toast.LENGTH_SHORT).show()
            renderJob(job)
            return
        }
        promptForCompletedCloseout(job)
    }

    private fun promptForCompletedCloseout(job: Job) {
        val workflowStateRepo = requireContext().fieldDeskContainer().localWorkflowStateRepository()
        val workflowState = workflowStateRepo.getJobWorkflowState(job.id)
        val form = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }
        val laborOptions = listOf(
            "Warranty / in warranty",
            "OOW hourly",
            "Diagnostic fee / no defect found",
            "Declined repair / customer refused"
        )
        val laborSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, laborOptions)
        }
        val durationInput = EditText(requireContext()).apply {
            hint = "Duration minutes"
            setText(derivedDurationMinutes(workflowState.workStartedAtEpochMillis).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val signerInput = EditText(requireContext()).apply {
            hint = "Signer name"
        }
        val summaryInput = EditText(requireContext()).apply {
            hint = "Work performed summary"
            minLines = 4
            setText(workflowState.noteDraft ?: "")
        }
        val approvalBox = CheckBox(requireContext()).apply {
            text = "Customer signoff captured"
        }
        val signaturePad = SignaturePadView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.density.times(180).toInt()
            ).apply {
                topMargin = 16
            }
        }
        val clearSignatureButton = Button(requireContext()).apply {
            text = "Clear signature"
            setOnClickListener { signaturePad.clearSignature() }
        }
        form.addView(laborSpinner)
        form.addView(durationInput)
        form.addView(signerInput)
        form.addView(summaryInput)
        form.addView(approvalBox)
        form.addView(signaturePad)
        form.addView(clearSignatureButton)
        AlertDialog.Builder(requireContext())
            .setTitle("Completed closeout")
            .setView(form)
            .setPositiveButton("Preview") { _, _ ->
                val signatureRequired = approvalBox.isChecked
                if (signatureRequired && !signaturePad.hasSignature()) {
                    Toast.makeText(requireContext(), "Draw the customer signature before previewing closeout.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val draft = JobCloseoutDraft(
                    laborCode = laborCodeForSelection(laborSpinner.selectedItemPosition),
                    workPerformed = summaryInput.text?.toString().orEmpty().trim(),
                    startedAtEpochMs = workflowState.workStartedAtEpochMillis,
                    endedAtEpochMs = System.currentTimeMillis(),
                    durationMinutes = durationInput.text?.toString()?.toIntOrNull(),
                    signedBy = signerInput.text?.toString().orEmpty().trim().ifBlank { null },
                    signatureDataBase64 = if (signatureRequired) signaturePad.toBase64Png() else null,
                    customerApproved = approvalBox.isChecked,
                    finalOutcome = "completed",
                    outcomeNote = workflowState.finalOutcomeNote
                )
                if (draft.workPerformed.isBlank()) {
                    Toast.makeText(requireContext(), "Work summary is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (draft.customerApproved && draft.signedBy.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Signer name is required when customer signoff is captured.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                detailViewModel.previewCloseout(job, draft)
                confirmCompletedCloseout(job, draft)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmCompletedCloseout(job: Job, draft: JobCloseoutDraft) {
        AlertDialog.Builder(requireContext())
            .setTitle("Submit closeout")
            .setMessage("Submit labor closeout for ${job.customerName} using ${draft.laborCode.replace('_', ' ')}?")
            .setPositiveButton("Submit") { _, _ ->
                detailViewModel.submitCloseout(job, draft)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun promptForUnableToComplete(job: Job) {
        promptForText(
            title = "Unable to complete",
            hint = "Explain what blocked closeout or completion"
        ) { reason ->
            detailViewModel.reportUnableToComplete(job, reason)
        }
    }

    private fun performCallAhead(job: Job) {
        detailViewModel.logCallAhead(job, 30)
    }

    private fun promptForPartsRequest(job: Job) {
        promptForText(
            title = "Need part",
            hint = "Part number, symptom, vendor, or other field details"
        ) { details ->
            detailViewModel.createPartsRequest(job, details.ifBlank { "Requested from mobile technician workflow" })
        }
    }

    private fun promptForQuoteNeeded(job: Job) {
        promptForText(
            title = "Quote needed",
            hint = "Explain what office needs to quote or approve"
        ) { details ->
            val subtype = inferQuoteSubtype(details)
            detailViewModel.reportQuoteNeeded(job, details, subtype)
        }
    }

    private fun promptForReschedule(job: Job) {
        promptForText(
            title = "Need reschedule",
            hint = "Why this job needs dispatch follow-up"
        ) { reason ->
            detailViewModel.reportReschedule(job, reason)
        }
    }

    private fun handleActionEvent(event: JobActionEvent) {
        if (event.eventId == lastHandledActionEventId) return
        lastHandledActionEventId = event.eventId
        if (event.result.success) {
            val workflowRepo = requireContext().fieldDeskContainer().localWorkflowStateRepository()
            when (event.actionKey) {
                "work_start" -> workflowRepo.recordWorkStarted(event.job.id)
                "closeout_submit" -> workflowRepo.setFinalOutcome(event.job.id, "completed", event.details)
                "no_answer", "not_home", "unable_to_complete" -> requireContext().fieldDeskContainer().localWorkflowStateRepository().setFinalOutcome(
                    event.job.id,
                    "unable_to_complete",
                    event.details
                )
            }
        }
        if (event.actionKey == "call_ahead" && event.result.success) {
            dialCustomer(event.job)
            return
        }
        handleActionResult(event.job, event.result)
    }

    private fun handleActionResult(job: Job, result: TechnicianActionResult) {
        val message = result.message.ifBlank {
            if (result.success) "Action submitted." else "Action failed."
        }
        requireContext().fieldDeskContainer().localWorkflowStateRepository().saveLastAction(job.id, message)
        showActionFeedback(message, isError = !result.success)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showActionFeedback(message: String, isError: Boolean = true) {
        binding.textActionFeedback.isVisible = message.isNotBlank()
        binding.textActionFeedback.text = message
        binding.textActionFeedback.setTextColor(
            resources.getColor(
                if (isError) R.color.brand_warn else R.color.brand_success,
                requireContext().theme
            )
        )
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
                job.statusMeta?.categoryLabel?.takeIf { it.isNotBlank() }?.let { append("\nSR category: $it") }
                job.nextAction?.takeIf { it.isNotBlank() }?.let {
                    append("\nNext action: $it")
                }
            }
        }
        return buildString {
            append("Parts: ${case.stageLabel.ifBlank { case.stage.ifBlank { "No active parts case" } }}")
            case.serviceRequestStatus?.takeIf { it.isNotBlank() }?.let { append("\nSR status: $it") }
            case.serviceRequestStatusMeta?.categoryLabel?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
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

    private fun laborCodeForSelection(index: Int): String = when (index) {
        0 -> "warranty"
        1 -> "oow_hourly"
        2 -> "diagnostic_fee"
        3 -> "declined_repair"
        else -> "oow_hourly"
    }

    private fun derivedDurationMinutes(startedAtEpochMillis: Long?): Int {
        val startedAt = startedAtEpochMillis ?: return 60
        val minutes = ((System.currentTimeMillis() - startedAt) / 60000L).toInt()
        return minutes.coerceIn(15, 12 * 60)
    }

    private fun SignaturePadView.toBase64Png(): String {
        val bitmap = renderBitmap()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
