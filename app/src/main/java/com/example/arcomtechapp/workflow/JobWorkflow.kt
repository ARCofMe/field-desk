package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job
import java.util.Locale

data class WorkflowChecklistItem(
    val label: String,
    val done: Boolean
)

data class WorkflowQuickAction(
    val key: String,
    val label: String
)

data class JobWorkflowSummary(
    val headline: String,
    val statusTone: String,
    val statusLabel: String,
    val nextStep: String,
    val checklist: List<WorkflowChecklistItem>,
    val quickActions: List<WorkflowQuickAction>,
    val priorityScore: Int
)

object JobWorkflow {
    fun summarize(job: Job): JobWorkflowSummary {
        val normalizedStatus = job.status.trim().lowercase(Locale.getDefault())
        val hasAddress = job.address.isNotBlank() && !job.address.equals("Address not provided", ignoreCase = true)
        val hasPhone = job.customerPhone.isNotBlank()
        val hasEquipment = !job.equipment.isNullOrBlank()

        val isComplete = normalizedStatus.contains("complete")
        val needsParts = normalizedStatus.contains("part")
        val inRoute = normalizedStatus.contains("route") || normalizedStatus.contains("travel")
        val onsite = normalizedStatus.contains("progress") || normalizedStatus.contains("start") || normalizedStatus.contains("onsite")

        val nextStep = when {
            isComplete -> "Review notes and photos before leaving the area."
            needsParts -> "Capture the part issue cleanly and hand it off without extra typing."
            onsite -> "Finish diagnosis, capture required details, and close with structured notes."
            inRoute -> "Call ahead if needed and arrive with the next action already chosen."
            else -> "Start this job with the minimum taps needed: call, navigate, then update status."
        }

        val checklist = listOf(
            WorkflowChecklistItem("Address ready", hasAddress),
            WorkflowChecklistItem("Customer reachable", hasPhone),
            WorkflowChecklistItem("Equipment identified", hasEquipment),
            WorkflowChecklistItem("Completion state", isComplete)
        )

        val quickActions = when {
            isComplete -> listOf(
                WorkflowQuickAction("navigate", "Reopen route"),
                WorkflowQuickAction("photos", "Review photos"),
                WorkflowQuickAction("notes", "Review notes"),
                WorkflowQuickAction("next_job", "Next job")
            )
            needsParts -> listOf(
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("parts", "Need part"),
                WorkflowQuickAction("photos", "Part photo"),
                WorkflowQuickAction("notes", "Parts note")
            )
            onsite -> listOf(
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("photos", "Take photos"),
                WorkflowQuickAction("notes", "Guided note"),
                WorkflowQuickAction("complete", "Close job")
            )
            inRoute -> listOf(
                WorkflowQuickAction("navigate", "Navigate"),
                WorkflowQuickAction("call", "Call ahead"),
                WorkflowQuickAction("arrive", "Arrived"),
                WorkflowQuickAction("parts", "Issue / part")
            )
            else -> listOf(
                WorkflowQuickAction("navigate", "Navigate"),
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("enroute", "On my way"),
                WorkflowQuickAction("photos", "Prep photos")
            )
        }

        val priorityScore = when {
            isComplete -> 90
            needsParts -> 30
            onsite -> 10
            inRoute -> 20
            else -> 40
        }

        return JobWorkflowSummary(
            headline = when {
                isComplete -> "Wrapped up"
                needsParts -> "Parts follow-up"
                onsite -> "Active on site"
                inRoute -> "In transit"
                else -> "Ready to start"
            },
            statusTone = when {
                isComplete -> "success"
                needsParts -> "warn"
                onsite -> "accent"
                else -> "default"
            },
            statusLabel = job.status.ifBlank { "Unknown" },
            nextStep = nextStep,
            checklist = checklist,
            quickActions = quickActions,
            priorityScore = priorityScore,
        )
    }

    fun sortForTechnicianFlow(jobs: List<Job>): List<Job> {
        return jobs.sortedWith(
            compareBy<Job> { summarize(it).priorityScore }
                .thenBy { appointmentBucket(it.appointmentWindow) }
                .thenBy { it.customerName.lowercase(Locale.getDefault()) }
        )
    }

    fun activeJob(jobs: List<Job>): Job? = sortForTechnicianFlow(jobs).firstOrNull()

    private fun appointmentBucket(window: String?): Int {
        val normalized = window.orEmpty().lowercase(Locale.getDefault())
        return when {
            "am" in normalized -> 0
            "pm" in normalized -> 2
            else -> 1
        }
    }
}
