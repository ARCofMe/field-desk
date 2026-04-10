package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.R
import com.example.arcomtechapp.databinding.FragmentDashboardBinding
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.util.WorkspaceLinks
import com.example.arcomtechapp.workflow.JobWorkflow
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel
import com.example.arcomtechapp.viewmodel.TechnicianDashboardViewModel
import java.text.DateFormat
import java.util.Date

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val viewModel: TechnicianDashboardViewModel by viewModels {
        TechnicianDashboardViewModel.Factory(requireContext().fieldDeskContainer().repository())
    }
    private lateinit var storage: Storage
    private lateinit var adapter: JobAdapter
    private var nextStopJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = JobAdapter { job ->
            selectedJobViewModel.select(job)
            fieldDeskNavigator().openJobDetail()
        }
        binding.recyclerTodayJobs.adapter = adapter

        binding.buttonOpenJobs.setOnClickListener { fieldDeskNavigator().openJobs() }
        binding.buttonOpenPhotos.setOnClickListener { fieldDeskNavigator().openPhotos() }
        binding.buttonOpenNotes.setOnClickListener { fieldDeskNavigator().openNotes() }
        binding.buttonSyncNow.setOnClickListener {
            storage.markSyncNow()
            viewModel.loadDashboard(requireContext().fieldDeskContainer().currentSession())
            Toast.makeText(requireContext(), "Sync marked", Toast.LENGTH_SHORT).show()
            updateConnectionBanner()
        }
        binding.buttonOptimizeRoute.setOnClickListener { launchOptimizedRoute() }
        binding.buttonOpenNextStop.setOnClickListener {
            val nextJob = nextStopJob ?: return@setOnClickListener
            selectedJobViewModel.select(nextJob)
            fieldDeskNavigator().openJobDetail()
        }
        binding.buttonOpenRouteDesk.setOnClickListener {
            openConfiguredWorkspace(
                url = storage.getRouteDeskUrl(),
                missingMessage = getString(R.string.fielddesk_route_desk_missing),
                invalidMessage = getString(R.string.fielddesk_workspace_invalid_url)
            )
        }
        binding.buttonOpenOpsHub.setOnClickListener {
            openConfiguredWorkspace(
                url = storage.getOpsHubUrl(),
                missingMessage = getString(R.string.fielddesk_ops_hub_missing),
                invalidMessage = getString(R.string.fielddesk_workspace_invalid_url)
            )
        }
        binding.buttonOpenPartsDesk.setOnClickListener {
            openConfiguredWorkspace(
                url = storage.getPartsDeskUrl(),
                missingMessage = getString(R.string.fielddesk_parts_desk_missing),
                invalidMessage = getString(R.string.fielddesk_workspace_invalid_url)
            )
        }

        observeViewModel()
        updateConnectionBanner()
        updateWorkspaceButtons()
        viewModel.loadDashboard(requireContext().fieldDeskContainer().currentSession())
    }

    override fun onResume() {
        super.onResume()
        updateWorkspaceButtons()
        updateDemoReadiness(viewModel.todayJobs.value.orEmpty())
    }

    private fun observeViewModel() {
        viewModel.todayJobs.observe(viewLifecycleOwner) { jobs ->
            adapter.submitList(jobs)
            binding.textTodayState.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            if (jobs.isEmpty()) {
                binding.textTodayState.text = getString(R.string.no_jobs_available)
            }
            updateNextStopCard(jobs)
            updateDemoReadiness(jobs)
        }

        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.textCompleted.text = summary.completed.toString()
            binding.textPending.text = summary.pending.toString()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressToday.visibility = if (loading) View.VISIBLE else View.GONE
            updateDemoReadiness(viewModel.todayJobs.value.orEmpty())
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textTodayState.visibility = if (error != null) View.VISIBLE else View.GONE
            if (error != null) {
                binding.textTodayState.text = error
            }
            updateDemoReadiness(viewModel.todayJobs.value.orEmpty())
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            if (!status.isNullOrBlank()) {
                binding.textConnection.text = status
            }
        }

        viewModel.lastLoadedAt.observe(viewLifecycleOwner) { timestamp ->
            binding.textLastRefresh.text = if (timestamp == null) {
                getString(R.string.fielddesk_dashboard_last_refresh_empty)
            } else {
                val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
                getString(R.string.fielddesk_dashboard_last_refresh, formatted)
            }
        }
    }

    private fun updateConnectionBanner() {
        val name = storage.getTechnicianName().orEmpty().ifBlank { "Technician" }
        val baseUrl = storage.getActiveBaseUrl()
        val apiKey = storage.getActiveApiKey()
        val statusParts = mutableListOf<String>()
        statusParts += name
        statusParts += when (storage.getBackendMode()) {
            Storage.BackendMode.OPS_HUB -> "Ops Hub"
            Storage.BackendMode.BLUEFOLDER_DIRECT -> "BlueFolder"
        }
        statusParts += if (baseUrl.isNullOrBlank()) "No server URL set" else baseUrl
        statusParts += if (apiKey.isNullOrBlank()) "Missing API key" else "Key saved"

        if (binding.textConnection.text.isNullOrBlank()) {
            binding.textConnection.text = statusParts.joinToString(" • ")
        }
    }

    private fun launchOptimizedRoute() {
        val jobs = JobWorkflow.sortForTechnicianFlow(viewModel.todayJobs.value.orEmpty()).filter {
            it.address.isNotBlank() && !it.address.equals("Address not provided", ignoreCase = true)
        }
        if (jobs.isEmpty()) {
            Toast.makeText(requireContext(), "No jobs with addresses to route", Toast.LENGTH_SHORT).show()
            return
        }
        val url = buildGoogleRouteUrl(jobs)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun buildGoogleRouteUrl(jobs: List<com.example.arcomtechapp.data.models.Job>): String {
        val encodedStops = jobs.map { Uri.encode(it.address) }
        if (encodedStops.isEmpty()) return ""
        val origin = "Current+Location"
        val destination = encodedStops.last()
        val waypoints = if (encodedStops.size > 1) encodedStops.dropLast(1).joinToString("|") else ""
        return buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=$origin")
            append("&destination=$destination")
            if (waypoints.isNotEmpty()) {
                append("&waypoints=$waypoints")
            }
            append("&travelmode=driving")
        }
    }

    private fun updateWorkspaceButtons() {
        val opsHubUrl = WorkspaceLinks.normalizeUrl(storage.getOpsHubUrl())
        val routeDeskUrl = WorkspaceLinks.normalizeUrl(storage.getRouteDeskUrl())
        val partsDeskUrl = WorkspaceLinks.normalizeUrl(storage.getPartsDeskUrl())
        binding.buttonOpenOpsHub.isEnabled = opsHubUrl != null
        binding.buttonOpenRouteDesk.isEnabled = routeDeskUrl != null
        binding.buttonOpenPartsDesk.isEnabled = partsDeskUrl != null
        binding.buttonOpenOpsHub.alpha = if (opsHubUrl != null) 1f else 0.55f
        binding.buttonOpenRouteDesk.alpha = if (routeDeskUrl != null) 1f else 0.55f
        binding.buttonOpenPartsDesk.alpha = if (partsDeskUrl != null) 1f else 0.55f
        updateDemoReadiness(viewModel.todayJobs.value.orEmpty())
    }

    private fun updateNextStopCard(jobs: List<Job>) {
        val nextJob = JobWorkflow.activeJob(jobs)
        nextStopJob = nextJob
        if (nextJob == null) {
            binding.textNextStopTitle.text = getString(R.string.fielddesk_dashboard_next_stop_empty)
            binding.textNextStopMeta.visibility = View.GONE
            binding.textNextStopAddress.visibility = View.GONE
            binding.buttonOpenNextStop.isEnabled = false
            binding.buttonOpenNextStop.alpha = 0.55f
            return
        }

        binding.textNextStopTitle.text = nextJob.customerName.ifBlank { "Service Request ${nextJob.id}" }
        binding.textNextStopMeta.text = listOf(nextJob.appointmentWindow, nextJob.status)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        binding.textNextStopMeta.visibility = View.VISIBLE
        binding.textNextStopAddress.text = nextJob.address
        binding.textNextStopAddress.visibility = View.VISIBLE
        binding.buttonOpenNextStop.isEnabled = true
        binding.buttonOpenNextStop.alpha = 1f
    }

    private fun updateDemoReadiness(jobs: List<Job>) {
        val configStatus = storage.getConfigStatus()
        val workspaceReadyCount = listOf(
            WorkspaceLinks.normalizeUrl(storage.getOpsHubUrl()),
            WorkspaceLinks.normalizeUrl(storage.getRouteDeskUrl()),
            WorkspaceLinks.normalizeUrl(storage.getPartsDeskUrl())
        ).count { it != null }
        val checks = listOf(
            configStatus.complete,
            jobs.isNotEmpty(),
            nextStopJob != null,
            workspaceReadyCount >= 2,
            !viewModel.loading.value.orFalse(),
            viewModel.error.value.isNullOrBlank()
        )
        val ready = checks.all { it }
        val details = mutableListOf<String>()
        if (!configStatus.complete) {
            details += "config"
        }
        if (jobs.isEmpty()) {
            details += "queue"
        }
        if (nextStopJob == null) {
            details += "next stop"
        }
        if (workspaceReadyCount < 2) {
            details += "ecosystem links"
        }
        if (!viewModel.error.value.isNullOrBlank()) {
            details += "load error"
        }
        binding.textDemoReadiness.text = if (ready) {
            getString(R.string.fielddesk_dashboard_demo_ready)
        } else {
            val suffix = if (details.isEmpty()) "" else ": ${details.joinToString(", ")}"
            getString(R.string.fielddesk_dashboard_demo_needs_attention) + suffix
        }
        binding.textDemoReadiness.alpha = if (ready) 0.92f else 1f
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false

    private fun openConfiguredWorkspace(url: String?, missingMessage: String, invalidMessage: String) {
        if (url.isNullOrBlank()) {
            Toast.makeText(requireContext(), missingMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val normalizedUrl = WorkspaceLinks.normalizeUrl(url)
        if (normalizedUrl == null) {
            Toast.makeText(requireContext(), invalidMessage, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, normalizedUrl))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
