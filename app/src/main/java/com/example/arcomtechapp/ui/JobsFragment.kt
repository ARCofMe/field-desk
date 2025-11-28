package com.example.arcomtechapp.ui

import android.os.Bundle
import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.arcomtechapp.R
import com.example.arcomtechapp.databinding.FragmentJobsBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.viewmodel.JobsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: JobsViewModel by viewModels()
    private lateinit var adapter: JobAdapter
    private lateinit var storage: Storage
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now()
    private val bfFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = JobAdapter { job ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.content_frame, JobDetailFragment.newInstance(job))
                .addToBackStack(null)
                .commit()
        }
        binding.recyclerJobs.adapter = adapter

        binding.buttonRefreshJobs.setOnClickListener { loadJobs() }
        binding.buttonStartDate.setOnClickListener { pickDate(true) }
        binding.buttonEndDate.setOnClickListener { pickDate(false) }
        binding.textJobsHeader.text = buildHeaderText()
        updateDateLabels()

        observeViewModel()
        loadJobs()
    }

    private fun buildHeaderText(): String {
        val baseUrl = storage.getBaseUrl()
        val apiKey = storage.getApiKey()
        val techName = storage.getTechnicianName()
        val pieces = mutableListOf<String>()
        techName?.let { if (it.isNotBlank()) pieces.add("Tech: $it") }
        baseUrl?.let { if (it.isNotBlank()) pieces.add(it) }
        if (apiKey.isNullOrBlank()) pieces.add("API key missing")
        return if (pieces.isEmpty()) "Full job list" else pieces.joinToString(" • ")
    }

    private fun observeViewModel() {
        viewModel.jobs.observe(viewLifecycleOwner) { jobs ->
            adapter.submitList(jobs)
            binding.textJobsState.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            if (jobs.isEmpty()) binding.textJobsState.text = "No jobs found"
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressJobs.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textJobsState.visibility = if (error != null) View.VISIBLE else View.GONE
            if (error != null) binding.textJobsState.text = error
        }
    }

    private fun loadJobs() {
        viewModel.loadJobs(
            technicianId = storage.getTechnicianId(),
            baseUrl = storage.getBaseUrl(),
            apiKey = storage.getApiKey(),
            startDate = startDate.format(bfFormatter) + " 12:00 AM",
            endDate = endDate.format(bfFormatter) + " 11:59 PM"
        )
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

    private fun updateDateLabels() {
        binding.textStartDate.text = "Start: ${startDate.format(bfFormatter)}"
        binding.textEndDate.text = "End: ${endDate.format(bfFormatter)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
