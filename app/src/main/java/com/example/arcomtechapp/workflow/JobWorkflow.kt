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
        val normalizedPartsStage = job.partsStage.orEmpty().trim().lowercase(Locale.getDefault())
        val hasAddress = job.address.isNotBlank() && !job.address.equals("Address not provided", ignoreCase = true)
        val hasPhone = job.customerPhone.isNotBlank()
        val hasEquipment = !job.equipment.isNullOrBlank()
        val hasNextAction = !job.nextAction.isNullOrBlank()

        val isComplete = normalizedStatus.contains("complete")
        val needsParts = normalizedStatus.contains("part") || normalizedPartsStage.contains("part")
        val quoteNeeded = normalizedStatus.contains("quote")
        val inRoute = normalizedStatus.contains("route") || normalizedStatus.contains("travel")
        val onsite = normalizedStatus.contains("progress") || normalizedStatus.contains("start") || normalizedStatus.contains("onsite")
        val isWaitingParts = normalizedPartsStage.contains("waiting")
        val isReadyToSchedule = normalizedPartsStage.contains("ready")

        val nextStep = when {
            hasNextAction -> job.nextAction.orEmpty()
            isComplete -> "Review notes and photos before leaving the area."
            quoteNeeded -> "Hand the quote workflow to office cleanly so the next trip is not blind."
            isReadyToSchedule -> "Parts are effectively ready. Make sure office has the customer-facing handoff."
            isWaitingParts -> "The parts case is active. Leave a clean trail for office and return scheduling."
            needsParts -> "Capture the part issue cleanly and hand it off without extra typing."
            onsite -> "Finish diagnosis, capture required details, and close with structured notes."
            inRoute -> "Call ahead if needed and arrive with the next action already chosen."
            else -> "Start this job with the minimum taps needed: call, navigate, then update status."
        }

        val checklist = listOf(
            WorkflowChecklistItem("Address ready", hasAddress),
            WorkflowChecklistItem("Customer reachable", hasPhone),
            WorkflowChecklistItem("Equipment identified", hasEquipment),
            WorkflowChecklistItem("Ops Hub next step", hasNextAction),
            WorkflowChecklistItem("Completion state", isComplete)
        )

        val quickActions = when {
            isComplete -> listOf(
                WorkflowQuickAction("navigate", "Reopen route"),
                WorkflowQuickAction("photos", "Review photos"),
                WorkflowQuickAction("notes", "Review notes"),
                WorkflowQuickAction("next_job", "Next job")
            )
            quoteNeeded -> listOf(
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("quote_needed", "Send quote handoff"),
                WorkflowQuickAction("notes", "Guided note"),
                WorkflowQuickAction("reschedule", "Need reschedule")
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
                WorkflowQuickAction("call_ahead", "Call ahead"),
                WorkflowQuickAction("arrive", "Arrived"),
                WorkflowQuickAction("quote_needed", "Quote / issue")
            )
            else -> listOf(
                WorkflowQuickAction("navigate", "Navigate"),
                WorkflowQuickAction("call_ahead", "Call ahead"),
                WorkflowQuickAction("enroute", "On my way"),
                WorkflowQuickAction("photos", "Prep photos")
            )
        }

        val priorityScore = when {
            isComplete -> 90
            quoteNeeded -> 15
            isReadyToSchedule -> 18
            isWaitingParts -> 25
            needsParts -> 30
            onsite -> 10
            inRoute -> 20
            else -> 40
        }

        return JobWorkflowSummary(
            headline = when {
                isComplete -> "Wrapped up"
                quoteNeeded -> "Quote follow-up"
                isReadyToSchedule -> "Ready to schedule"
                isWaitingParts -> "Waiting on parts"
                needsParts -> "Parts follow-up"
                onsite -> "Active on site"
                inRoute -> "In transit"
                else -> "Ready to start"
            },
            statusTone = when {
                isComplete -> "success"
                quoteNeeded || needsParts || isWaitingParts -> "warn"
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
