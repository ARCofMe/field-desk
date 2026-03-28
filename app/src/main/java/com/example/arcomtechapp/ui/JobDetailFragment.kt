package com.example.arcomtechapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Toast
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.databinding.FragmentJobDetailBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.serializableCompat
import com.example.arcomtechapp.workflow.JobExecutionAssist
import com.example.arcomtechapp.workflow.JobProgress
import com.example.arcomtechapp.workflow.JobWorkflow

class JobDetailFragment : Fragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private var job: Job? = null
    private lateinit var storage: Storage
    private var launchCallOnOpen: Boolean = false
    private var launchNavigationOnOpen: Boolean = false

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
    }

    override fun onResume() {
        super.onResume()
        job?.let { renderJob(it) }
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
            storage.saveLastJobAction(job.id, "Opened parts handoff")
            Toast.makeText(requireContext(), "Parts workflow should call Ops Hub next.", Toast.LENGTH_SHORT).show()
        }
        binding.buttonWorkflowComplete.setOnClickListener {
            storage.saveLastJobAction(job.id, "Prepared closeout checklist")
            Toast.makeText(requireContext(), "Completion guard should be wired to Ops Hub.", Toast.LENGTH_SHORT).show()
        }

        if (launchCallOnOpen) {
            launchCallOnOpen = false
            dialCustomer(job)
        } else if (launchNavigationOnOpen) {
            launchNavigationOnOpen = false
            openNavigation(job)
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
            "navigate", "next_job" -> openNavigation(job)
            "photos" -> openPhotos(job)
            "notes" -> openNotes(job)
            "parts", "complete", "arrive", "enroute" -> {
                val label = when (key) {
                    "parts" -> "Captured parts issue locally"
                    "complete" -> {
                        val progress = storage.getLocalJobProgress(job.id)
                        val closeout = JobExecutionAssist.completionSummary(
                            job,
                            JobProgress(
                                noteDraftLength = progress.noteDraft?.length ?: 0,
                                photoCount = progress.photoCount,
                                lastPhotoLabel = progress.lastPhotoLabel
                            )
                        )
                        if (closeout.ready) {
                            "Closeout checklist ready"
                        } else {
                            closeout.blockers.firstOrNull() ?: "Prepared closeout locally"
                        }
                    }
                    "arrive" -> "Marked arrived locally"
                    else -> "Marked en route locally"
                }
                storage.saveLastJobAction(job.id, label)
                Toast.makeText(requireContext(), "$label. Wire this to Ops Hub.", Toast.LENGTH_SHORT).show()
                renderJob(job)
            }
            else -> Toast.makeText(requireContext(), "More guided actions coming next.", Toast.LENGTH_SHORT).show()
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
