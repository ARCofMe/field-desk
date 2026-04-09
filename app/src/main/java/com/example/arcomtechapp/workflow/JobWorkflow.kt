package com.example.arcomtechapp.workflow

import com.example.arcomtechapp.data.models.Job
import java.util.Locale
import java.util.regex.Pattern

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
        val statusMeta = job.statusMeta
        val hasAddress = job.address.isNotBlank() && !job.address.equals("Address not provided", ignoreCase = true)
        val hasPhone = job.customerPhone.isNotBlank()
        val hasEquipment = !job.equipment.isNullOrBlank()
        val hasNextAction = !job.nextAction.isNullOrBlank()

        val isComplete = statusMeta?.isClosed == true || normalizedStatus.contains("complete")
        val needsParts = statusMeta?.isActiveParts == true || normalizedStatus.contains("part") || normalizedPartsStage.contains("part")
        val quoteNeeded = statusMeta?.isQuoteNeeded == true || normalizedStatus.contains("quote")
        val waitingCustomer = statusMeta?.isWaitingCustomer == true
        val reviewState = statusMeta?.isReview == true
        val schedulingState = statusMeta?.isScheduling == true
        val inRoute = normalizedStatus.contains("route") || normalizedStatus.contains("travel")
        val onsite = normalizedStatus.contains("progress") || normalizedStatus.contains("start") || normalizedStatus.contains("onsite")
        val started = normalizedStatus.contains("start")
        val isWaitingParts = normalizedPartsStage.contains("waiting")
        val isReadyToSchedule = normalizedPartsStage.contains("ready")

        val nextStep = when {
            hasNextAction -> job.nextAction.orEmpty()
            isComplete -> "Review notes and photos before leaving the area."
            quoteNeeded -> "Hand the quote workflow to office cleanly so the next trip is not blind."
            waitingCustomer -> "This stop is waiting on customer-side follow-up. Leave a clear note and avoid duplicate outreach."
            reviewState -> "Office review is likely the blocker. Capture clean notes and avoid reworking the same status in the field."
            schedulingState -> "This stop is already in a scheduling-oriented state. Confirm the handoff is clean before adding more updates."
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
            waitingCustomer -> listOf(
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("notes", "Guided note"),
                WorkflowQuickAction("reschedule", "Need reschedule"),
                WorkflowQuickAction("photos", "Take photos")
            )
            needsParts -> listOf(
                WorkflowQuickAction("call", "Call customer"),
                WorkflowQuickAction("parts", "Need part"),
                WorkflowQuickAction("photos", "Part photo"),
                WorkflowQuickAction("notes", "Parts note")
            )
            onsite -> listOf(
                WorkflowQuickAction("start", if (started) "Work started" else "Start work"),
                WorkflowQuickAction("photos", "Take photos"),
                WorkflowQuickAction("notes", "Guided note"),
                WorkflowQuickAction("complete", "Close job")
            )
            inRoute -> listOf(
                WorkflowQuickAction("navigate", "Navigate"),
                WorkflowQuickAction("call_ahead", "Call ahead"),
                WorkflowQuickAction("arrive", "Arrived"),
                WorkflowQuickAction("not_home", "Not home")
            )
            else -> listOf(
                WorkflowQuickAction("navigate", "Navigate"),
                WorkflowQuickAction("call_ahead", "Call ahead"),
                WorkflowQuickAction("enroute", "On my way"),
                WorkflowQuickAction("no_answer", "No answer")
            )
        }

        val priorityScore = when {
            isComplete -> 90
            quoteNeeded -> 15
            waitingCustomer -> 17
            reviewState -> 19
            schedulingState -> 22
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
                waitingCustomer -> "Customer follow-up"
                reviewState -> "Office review"
                schedulingState -> "Scheduling in flight"
                isReadyToSchedule -> "Ready to schedule"
                isWaitingParts -> "Waiting on parts"
                needsParts -> "Parts follow-up"
                onsite -> "Active on site"
                inRoute -> "In transit"
                else -> "Ready to start"
            },
            statusTone = when {
                isComplete -> "success"
                quoteNeeded || needsParts || isWaitingParts || waitingCustomer || reviewState -> "warn"
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
            compareBy<Job> { appointmentStartSortKey(it.appointmentWindow) }
                .thenBy { summarize(it).priorityScore }
                .thenBy { it.customerName.lowercase(Locale.getDefault()) }
        )
    }

    fun activeJob(jobs: List<Job>): Job? {
        val ordered = sortForTechnicianFlow(jobs)
        return ordered.firstOrNull { !it.status.contains("complete", ignoreCase = true) } ?: ordered.firstOrNull()
    }

    private fun appointmentStartSortKey(window: String?): Int {
        val normalized = window.orEmpty().lowercase(Locale.getDefault())
        parseWindowStartHour(normalized)?.let { return it }
        return when {
            "am" in normalized -> 8
            "pm" in normalized -> 13
            "unscheduled" in normalized || normalized.isBlank() -> 99
            else -> 50
        }
    }

    private fun parseWindowStartHour(window: String): Int? {
        val match = WINDOW_START_PATTERN.matcher(window)
        if (!match.find()) return null
        val rawHour = match.group(1)?.toIntOrNull() ?: return null
        val meridiem = match.group(2)?.lowercase(Locale.getDefault())
        var hour = rawHour
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (meridiem == null && hour in 1..6) hour += 12
        return hour
    }

    private val WINDOW_START_PATTERN = Pattern.compile("(\\d{1,2})(?:[:.]\\d{2})?\\s*(am|pm)?")
}
