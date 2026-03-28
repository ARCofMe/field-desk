package com.example.arcomtechapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.models.Job
import com.example.arcomtechapp.databinding.ItemJobBinding
import com.example.arcomtechapp.workflow.JobWorkflow

class JobAdapter(
    private val onJobClicked: (Job) -> Unit
) : ListAdapter<Job, JobAdapter.JobViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return JobViewHolder(binding, onJobClicked)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class JobViewHolder(
        private val binding: ItemJobBinding,
        private val onClick: (Job) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(job: Job) {
            // Subtle background tint based on equipment type
            val bgRes = backgroundForEquipment(job.equipment)
            binding.root.setBackgroundResource(bgRes)

            val equipmentLabel = friendlyEquipment(job.equipment)
            val titleParts = mutableListOf<String>()
            titleParts += "#${job.id}"
            if (equipmentLabel.isNotBlank()) titleParts += equipmentLabel
            if (job.customerName.isNotBlank()) titleParts += job.customerName
            binding.textJobTitle.text = titleParts.joinToString(" - ")
            val summary = JobWorkflow.summarize(job)
            binding.textJobAddress.text = job.address
            binding.textJobWindow.text = listOf(job.appointmentWindow, summary.headline).filter { it.isNotBlank() }.joinToString(" • ")
            binding.textJobPhone.text = job.customerPhone
            binding.textJobEquipment.isVisible = true
            binding.textJobEquipment.text = summary.nextStep

            val distance = job.distanceMiles
            if (distance != null) {
                binding.textJobDistance.isVisible = true
                binding.textJobDistance.text = String.format("%.1f mi away", distance)
            } else {
                binding.textJobDistance.isVisible = false
            }

            binding.root.setOnClickListener { onClick(job) }
        }

        private fun backgroundForEquipment(equipment: String?): Int {
            if (equipment.isNullOrBlank()) return R.drawable.bg_job_default
            val code = equipment.uppercase()
            return when {
                code.startsWith("WM") -> R.drawable.bg_job_wm   // washer
                code.startsWith("RF") -> R.drawable.bg_job_rf   // refrigerator
                code.startsWith("DW") -> R.drawable.bg_job_dw   // dishwasher
                code.startsWith("DR") -> R.drawable.bg_job_dr   // dryer
                code.startsWith("OV") || code.startsWith("ST") -> R.drawable.bg_job_ov // oven/stove
                else -> R.drawable.bg_job_default
            }
        }

        private fun friendlyEquipment(equipment: String?): String {
            if (equipment.isNullOrBlank()) return ""
            val code = equipment.uppercase()
            return when {
                code.contains("WASH") || code.startsWith("WM") -> "Washer"
                code.contains("DRY") || code.startsWith("DR") -> "Dryer"
                code.contains("DISH") || code.startsWith("DW") -> "Dishwasher"
                code.contains("FRID") || code.contains("REFR") || code.startsWith("RF") -> "Refrigerator"
                code.contains("OVEN") || code.contains("RANGE") || code.contains("STOVE") || code.startsWith("OV") || code.startsWith("ST") -> "Oven/Range"
                else -> equipment
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Job>() {
            override fun areItemsTheSame(oldItem: Job, newItem: Job): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Job, newItem: Job): Boolean = oldItem == newItem
        }
    }
}
