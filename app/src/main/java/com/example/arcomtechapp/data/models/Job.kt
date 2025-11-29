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
    val equipment: String? = null
) : Serializable
