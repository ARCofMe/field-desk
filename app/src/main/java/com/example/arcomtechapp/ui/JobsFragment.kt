package com.example.arcomtechapp.ui

import android.app.DatePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.databinding.FragmentJobsBinding
import com.example.arcomtechapp.runtime.fieldDeskContainer
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.viewmodel.SelectedJobViewModel
import com.example.arcomtechapp.viewmodel.JobsViewModel
import com.example.arcomtechapp.workflow.JobWorkflow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.ArrayAdapter
import com.google.android.gms.location.LocationServices
import android.os.Bundle
import android.location.Geocoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val selectedJobViewModel: SelectedJobViewModel by activityViewModels()
    private val viewModel: JobsViewModel by viewModels {
        JobsViewModel.Factory(RepositoryProvider.fromContext(requireContext()))
    }
    private lateinit var adapter: JobAdapter
    private lateinit var storage: Storage
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now()
    private val bfFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private var dateRangeType: String = "scheduled"
    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private var pendingRouteJobs: List<com.example.arcomtechapp.data.models.Job> = emptyList()
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestLocationAndRoute(pendingRouteJobs)
        } else {
            binding.textJobsState.visibility = View.VISIBLE
            binding.textJobsState.text = "Location permission denied"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = JobAdapter { job ->
            selectedJobViewModel.select(job)
            fieldDeskNavigator().openJobDetail()
        }
        binding.recyclerJobs.adapter = adapter
        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())

        binding.buttonRefreshJobs.setOnClickListener { loadJobs() }
        binding.buttonStartDate.setOnClickListener { pickDate(true) }
        binding.buttonEndDate.setOnClickListener { pickDate(false) }
        binding.textJobsHeader.text = buildHeaderText()
        updateDateLabels()
        setupRangeTypeSpinner()
        binding.buttonOptimizeJobsRoute.setOnClickListener { launchOptimizedRoute() }

        observeViewModel()
        loadJobs()
    }

    private fun buildHeaderText(): String {
        val baseUrl = storage.getActiveBaseUrl()
        val apiKey = storage.getActiveApiKey()
        val techName = storage.getTechnicianName()
        val pieces = mutableListOf<String>()
        techName?.let { if (it.isNotBlank()) pieces.add("Tech: $it") }
        pieces += when (storage.getBackendMode()) {
            Storage.BackendMode.OPS_HUB -> "Ops Hub"
            Storage.BackendMode.BLUEFOLDER_DIRECT -> "BlueFolder"
        }
        baseUrl?.let { if (it.isNotBlank()) pieces.add(it) }
        if (apiKey.isNullOrBlank()) pieces.add("API key missing")
        return if (pieces.isEmpty()) "Full job list" else pieces.joinToString(" • ")
    }

    private fun observeViewModel() {
        viewModel.jobs.observe(viewLifecycleOwner) { jobs ->
            val ordered = JobWorkflow.sortForTechnicianFlow(jobs)
            adapter.submitList(ordered)
            binding.textJobsState.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            if (jobs.isEmpty()) binding.textJobsState.text = "No jobs found"
            binding.textJobsHeader.text = if (ordered.isEmpty()) {
                buildHeaderText()
            } else {
                val active = JobWorkflow.activeJob(ordered)
                buildHeaderText() + " • Focus: #${active?.id ?: ordered.first().id} • ${JobWorkflow.routeOrderBrief(ordered)}"
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressJobs.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textJobsState.visibility = if (error != null) View.VISIBLE else View.GONE
            if (error != null) {
                binding.textJobsState.text = error
            }
        }
    }

    private fun loadJobs() {
        binding.textJobsState.visibility = View.GONE
        viewModel.loadJobs(
            session = requireContext().fieldDeskContainer().currentSession(),
            startDate = startDate.format(bfFormatter) + " 12:00 AM",
            endDate = endDate.format(bfFormatter) + " 11:59 PM",
            dateRangeType = dateRangeType
        )
    }

    private fun launchOptimizedRoute() {
        val jobs = JobWorkflow.sortForTechnicianFlow(viewModel.jobs.value.orEmpty()).filter {
            it.address.isNotBlank() && !it.address.equals("Address not provided", ignoreCase = true)
        }
        if (jobs.isEmpty()) {
            binding.textJobsState.visibility = View.VISIBLE
            binding.textJobsState.text = "No jobs with addresses to route"
            return
        }
        // Deduplicate by normalized address to avoid double stops in Maps
        val unique = linkedMapOf<String, com.example.arcomtechapp.data.models.Job>()
        jobs.forEach { job ->
            val key = job.address.lowercase(Locale.getDefault()).trim()
            if (!unique.containsKey(key)) {
                unique[key] = job
            }
        }
        val uniqueJobs = unique.values.toList()

        Log.d("JobsFragment", "Routing stops: ${uniqueJobs.map { it.address }}")
        requestLocationAndRoute(uniqueJobs)
    }

    private fun buildGoogleRouteUrl(originLat: Double, originLng: Double, jobs: List<com.example.arcomtechapp.data.models.Job>): String {
        val encodedStops = jobs.map { Uri.encode(it.address) }
            .filter { it.isNotBlank() && !it.lowercase(Locale.getDefault()).startsWith("optimize") }
        if (encodedStops.isEmpty()) return ""

        val storedOrigin = storage.getRouteOrigin()
        val storedDestination = storage.getRouteDestination()

        val origin = storedOrigin?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) } ?: "${originLat},${originLng}"
        val destination = storedDestination?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) } ?: origin // loop back to current position
        val waypoints = encodedStops.joinToString("|")

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

    private fun pickDate(isStart: Boolean) {
        val current = if (isStart) startDate else endDate
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                if (isStart) {
                    startDate = selected
                    if (startDate.isAfter(endDate)) endDate = startDate
                } else {
                    endDate = selected
                    if (endDate.isBefore(startDate)) startDate = endDate
                }
                updateDateLabels()
                loadJobs()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        )
        dialog.show()
    }

    private fun requestLocationAndRoute(jobs: List<com.example.arcomtechapp.data.models.Job>) {
        pendingRouteJobs = jobs
        if (ActivityCompat.checkSelfPermission(requireContext(), locationPermission) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(locationPermission)
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation.addOnSuccessListener { loc ->
            val lat = loc?.latitude
            val lng = loc?.longitude
            if (lat != null && lng != null) {
                val ordered = orderStops(jobs)
                val url = buildGoogleRouteUrl(lat, lng, ordered)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } else {
                binding.textJobsState.visibility = View.VISIBLE
                binding.textJobsState.text = "Location unavailable"
            }
        }.addOnFailureListener {
            binding.textJobsState.visibility = View.VISIBLE
            binding.textJobsState.text = "Unable to get location"
        }
    }

    private fun orderStops(jobs: List<com.example.arcomtechapp.data.models.Job>): List<com.example.arcomtechapp.data.models.Job> {
        return JobWorkflow.sortForTechnicianFlow(jobs)
    }


    private fun updateDateLabels() {
        binding.textStartDate.text = "Start: ${startDate.format(bfFormatter)}"
        binding.textEndDate.text = "End: ${endDate.format(bfFormatter)}"
    }

    private fun setupRangeTypeSpinner() {
        val options = resources.getStringArray(R.array.job_date_range_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDateRangeType.adapter = adapter
        binding.spinnerDateRangeType.setSelection(options.indexOf(dateRangeType))
        binding.spinnerDateRangeType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                dateRangeType = options[position]
                loadJobs()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
