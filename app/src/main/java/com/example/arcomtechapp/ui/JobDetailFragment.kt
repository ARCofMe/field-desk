package com.example.arcomtechapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.databinding.FragmentJobDetailBinding

class JobDetailFragment : Fragment() {

    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = arguments?.getSerializable(ARG_JOB) as? Job
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        job?.let { renderJob(it) }
    }

    private fun renderJob(job: Job) {
        binding.textJobId.text = job.id
        binding.textCustomerName.text = job.customerName
        binding.textJobStatus.text = job.status
        binding.textAppointment.text = job.appointmentWindow
        binding.textAddress.text = job.address
        binding.textPhone.text = job.customerPhone
        binding.textDistance.text = job.distanceMiles?.let { String.format("%.1f mi away", it) } ?: "N/A"

        binding.buttonCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.customerPhone}")))
        }

        binding.buttonNavigate.setOnClickListener {
            val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(job.address)}")
            startActivity(Intent(Intent.ACTION_VIEW, mapUri))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_JOB = "arg_job"

        fun newInstance(job: Job): JobDetailFragment {
            val fragment = JobDetailFragment()
            fragment.arguments = Bundle().apply {
                putSerializable(ARG_JOB, job)
            }
            return fragment
        }
    }
}
