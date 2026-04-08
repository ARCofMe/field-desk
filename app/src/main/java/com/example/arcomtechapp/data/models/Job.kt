package com.example.arcomtechapp.data.models

import java.io.Serializable

data class Job(
    val id: String,
    val address: String,
    val appointmentWindow: String,
    val customerName: String,
    val customerPhone: String,
    val status: String,
    val statusMeta: JobStatusMeta? = null,
    val distanceMiles: Double? = null,
    val equipment: String? = null,
    val partsStage: String? = null,
    val nextAction: String? = null
) : Serializable

data class JobStatusMeta(
    val raw: String? = null,
    val category: String? = null,
    val categoryLabel: String? = null,
    val isClosed: Boolean = false,
    val isOpen: Boolean = false,
    val isQuoteNeeded: Boolean = false,
    val quoteSubtype: String? = null,
    val isActiveParts: Boolean = false,
    val isWaitingCustomer: Boolean = false,
    val isScheduling: Boolean = false,
    val isReview: Boolean = false,
    val knownInTenantCatalog: Boolean = false
) : Serializable

data class JobPartsCase(
    val reference: String,
    val stage: String,
    val stageLabel: String,
    val status: String,
    val serviceRequestStatus: String? = null,
    val serviceRequestStatusMeta: JobStatusMeta? = null,
    val openRequestIds: List<Int> = emptyList(),
    val assignedPartsUserId: Int? = null,
    val blocker: String? = null,
    val latestStatusText: String? = null,
    val latestIssueText: String? = null,
    val nextAction: String? = null,
    val updatedAt: String? = null
) : Serializable

data class JobPhotoRecord(
    val subject: String,
    val fromEmail: String,
    val receivedAt: String,
    val attachmentCount: Int,
    val attachmentNames: List<String> = emptyList()
) : Serializable

data class JobPhotoStatus(
    val enabled: Boolean,
    val srId: String,
    val mailboxStatus: String,
    val message: String,
    val totalPhotos: Int,
    val foundTags: List<String> = emptyList(),
    val missingTags: List<String> = emptyList(),
    val records: List<JobPhotoRecord> = emptyList(),
    val shouldNotify: Boolean,
    val reason: String
) : Serializable

data class JobTimelineEntry(
    val occurredAt: String,
    val source: String,
    val eventType: String,
    val summary: String,
    val details: String? = null,
    val actorLabel: String? = null
) : Serializable

data class JobCloseoutDraft(
    val laborCode: String,
    val workPerformed: String,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    val durationMinutes: Int? = null,
    val signedBy: String? = null,
    val customerApproved: Boolean = false,
    val finalOutcome: String = "completed",
    val outcomeNote: String? = null
) : Serializable

data class JobCloseoutPreview(
    val laborCode: String,
    val laborLabel: String,
    val billable: Boolean,
    val dateWorked: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val durationLabel: String,
    val workPerformed: String,
    val signoffLabel: String
) : Serializable
