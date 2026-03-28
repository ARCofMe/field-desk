package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.arcomtechapp.databinding.FragmentTodayBinding
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.viewmodel.TechnicianDashboardViewModel
import com.example.arcomtechapp.workflow.JobWorkflow

class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TechnicianDashboardViewModel by viewModels {
        TechnicianDashboardViewModel.Factory(RepositoryProvider.fromContext(requireContext()))
    }
    private lateinit var storage: Storage

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonOpenAllJobs.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content_frame, JobsFragment())
                .addToBackStack(null)
                .commit()
        }
        observeViewModel()
        viewModel.loadDashboard(storage.getTechnicianId(), storage.getActiveBaseUrl(), storage.getActiveApiKey())
    }

    private fun observeViewModel() {
        viewModel.todayJobs.observe(viewLifecycleOwner) { jobs ->
            renderToday(jobs)
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressToday.visibility = if (loading) VISIBLE else GONE
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textTodayState.visibility = if (error.isNullOrBlank()) GONE else VISIBLE
            binding.textTodayState.text = error
        }
    }

    private fun renderToday(jobs: List<Job>) {
        val ordered = JobWorkflow.sortForTechnicianFlow(jobs)
        val active = JobWorkflow.activeJob(ordered)

        binding.textTodaySummary.text = if (ordered.isEmpty()) {
            "No scheduled work is loaded right now."
        } else {
            "${ordered.size} jobs loaded. Focus the next tap on the highest-value action, not the full list."
        }
        binding.textQueueCount.text = ordered.size.toString()
        binding.textQueueComplete.text = ordered.count { it.status.contains("complete", ignoreCase = true) }.toString()
        binding.textQueuePending.text = ordered.count { !it.status.contains("complete", ignoreCase = true) }.toString()

        if (active == null) {
            binding.cardActiveJob.visibility = GONE
            return
        }
        binding.cardActiveJob.visibility = VISIBLE
        val summary = JobWorkflow.summarize(active)
        binding.textActiveEyebrow.text = summary.headline
        binding.textActiveTitle.text = buildString {
            append("#${active.id}")
            if (active.customerName.isNotBlank()) append(" • ${active.customerName}")
        }
        binding.textActiveMeta.text = listOf(active.appointmentWindow, summary.statusLabel)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        binding.textActiveAddress.text = active.address
        binding.textNextStep.text = summary.nextStep
        binding.buttonPrimaryAction.text = summary.quickActions.firstOrNull()?.label ?: "Open job"
        binding.buttonSecondaryAction.text = summary.quickActions.getOrNull(1)?.label ?: "Open details"
        binding.buttonPrimaryAction.setOnClickListener { handleQuickAction(active, summary.quickActions.firstOrNull()?.key) }
        binding.buttonSecondaryAction.setOnClickListener { handleQuickAction(active, summary.quickActions.getOrNull(1)?.key) }
        binding.buttonOpenActiveJob.setOnClickListener { openJob(active) }

        val checklistText = summary.checklist.joinToString("\n") {
            "${if (it.done) "•" else "○"} ${it.label}"
        }
        binding.textChecklist.text = checklistText

        val lastAction = storage.getLastJobAction()
        val lastActionJobId = storage.getLastJobActionJobId()
        binding.textLastAction.visibility = if (lastAction.isNullOrBlank()) GONE else VISIBLE
        if (!lastAction.isNullOrBlank()) {
            binding.textLastAction.text = if (lastActionJobId == active.id) {
                "Last action on this job: $lastAction"
            } else {
                "Last local action: $lastAction on job #$lastActionJobId"
            }
        }
    }

    private fun handleQuickAction(job: Job, actionKey: String?) {
        when (actionKey) {
            "call" -> openJob(job, launchCall = true)
            "navigate", "next_job" -> openJob(job, launchNavigation = true)
            "notes" -> {
                storage.saveLastJobAction(job.id, "Opened guided note")
                parentFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, NotesFragment.newInstance(job))
                    .addToBackStack(null)
                    .commit()
            }
            "photos" -> {
                storage.saveLastJobAction(job.id, "Opened photo workflow")
                parentFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, PhotosFragment.newInstance(job))
                    .addToBackStack(null)
                    .commit()
            }
            "parts", "complete", "arrive", "enroute" -> {
                val label = when (actionKey) {
                    "parts" -> "Flagged parts workflow"
                    "complete" -> "Prepared closeout flow"
                    "arrive" -> "Marked arrival locally"
                    else -> "Marked en route locally"
                }
                storage.saveLastJobAction(job.id, label)
                Toast.makeText(requireContext(), "$label. Wire this to Ops Hub next.", Toast.LENGTH_SHORT).show()
                renderToday(viewModel.todayJobs.value.orEmpty())
            }
            else -> openJob(job)
        }
    }

    private fun openJob(job: Job, launchCall: Boolean = false, launchNavigation: Boolean = false) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content_frame, JobDetailFragment.newInstance(job, launchCall, launchNavigation))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
