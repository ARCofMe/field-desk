package com.example.arcomtechapp.data.models

import com.example.arcomtechapp.storage.Storage

data class FieldDeskSession(
    val backendMode: Storage.BackendMode,
    val technicianId: String?,
    val technicianName: String?,
    val baseUrl: String?,
    val apiKey: String?,
    val configComplete: Boolean,
    val missingConfig: List<String> = emptyList()
)
