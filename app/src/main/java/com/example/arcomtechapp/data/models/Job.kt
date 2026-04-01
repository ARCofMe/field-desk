package com.example.arcomtechapp.data.models

import java.io.Serializable

data class Job(
    val id: String,
    val address: String,
    val appointmentWindow: String,
    val customerName: String,
    val customerPhone: String,
    val status: String,
    val distanceMiles: Double? = null,
    val equipment: String? = null,
    val partsStage: String? = null,
    val nextAction: String? = null
) : Serializable

data class JobPartsCase(
    val reference: String,
    val stage: String,
    val stageLabel: String,
    val status: String,
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
