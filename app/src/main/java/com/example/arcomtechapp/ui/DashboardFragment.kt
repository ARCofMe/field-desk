package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.R
import com.example.arcomtechapp.databinding.FragmentDashboardBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.viewmodel.TechnicianDashboardViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TechnicianDashboardViewModel by viewModels()
    private lateinit var storage: Storage
    private lateinit var adapter: JobAdapter

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
            openFragment(JobDetailFragment.newInstance(job))
        }
        binding.recyclerTodayJobs.adapter = adapter

        binding.buttonOpenJobs.setOnClickListener { openFragment(JobsFragment()) }
        binding.buttonOpenPhotos.setOnClickListener { openFragment(PhotosFragment()) }
        binding.buttonOpenNotes.setOnClickListener { openFragment(NotesFragment()) }
        binding.buttonSyncNow.setOnClickListener {
            storage.markSyncNow()
            viewModel.loadDashboard(storage.getTechnicianId(), storage.getBaseUrl(), storage.getApiKey())
            Toast.makeText(requireContext(), "Sync marked", Toast.LENGTH_SHORT).show()
            updateConnectionBanner()
        }
        binding.buttonOptimizeRoute.setOnClickListener { launchOptimizedRoute() }

        observeViewModel()
        updateConnectionBanner()
        viewModel.loadDashboard(storage.getTechnicianId(), storage.getBaseUrl(), storage.getApiKey())
    }

    private fun observeViewModel() {
        viewModel.todayJobs.observe(viewLifecycleOwner) { jobs ->
            adapter.submitList(jobs)
            binding.textTodayState.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            if (jobs.isEmpty()) {
                binding.textTodayState.text = getString(R.string.no_jobs_available)
            }
        }

        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.textCompleted.text = summary.completed.toString()
            binding.textPending.text = summary.pending.toString()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressToday.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textTodayState.visibility = if (error != null) View.VISIBLE else View.GONE
            if (error != null) {
                binding.textTodayState.text = error
            }
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            if (!status.isNullOrBlank()) {
                binding.textConnection.text = status
            }
        }
    }

    private fun updateConnectionBanner() {
        val name = storage.getTechnicianName().orEmpty().ifBlank { "Technician" }
        val baseUrl = storage.getBaseUrl()
        val apiKey = storage.getApiKey()
        val statusParts = mutableListOf<String>()
        statusParts += name
        statusParts += if (baseUrl.isNullOrBlank()) "No subdomain set" else baseUrl
        statusParts += if (apiKey.isNullOrBlank()) "Missing API key" else "Key saved"

        binding.textGreeting.text = "Home dashboard"
        if (binding.textConnection.text.isNullOrBlank()) {
            binding.textConnection.text = statusParts.joinToString(" • ")
        }
    }

    private fun launchOptimizedRoute() {
        val jobs = viewModel.todayJobs.value.orEmpty().filter { it.address.isNotBlank() }
        if (jobs.isEmpty()) {
            Toast.makeText(requireContext(), "No jobs with addresses to route", Toast.LENGTH_SHORT).show()
            return
        }
        val url = buildGoogleRouteUrl(jobs)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun buildGoogleRouteUrl(jobs: List<com.example.arcomtechapp.data.models.Job>): String {
        // Use Google Maps web directions with optimize:true to let Maps reorder stops.
        val encodedStops = jobs.map { Uri.encode(it.address) }
        val origin = "Current+Location"
        val destination = encodedStops.last()
        val waypoints = if (encodedStops.size > 1) {
            "optimize:true|" + encodedStops.dropLast(1).joinToString("|")
        } else ""
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

    private fun openFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
