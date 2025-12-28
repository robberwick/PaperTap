package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Core data
    val userLabel: String,
    val rawBarcodeData: String,
    val barcodeFormat: Int, // ML Kit Barcode.FORMAT_* constant

    // Optional journey metadata
    val originStationCode: String? = null,
    val destinationStationCode: String? = null,
    val travelDate: Long? = null, // Unix timestamp

    // Timestamps
    val addedAt: Long = System.currentTimeMillis(),
    val lastFlashedAt: Long? = null,

    // Usage tracking
    val flashCount: Int = 0,
    val flashHistory: String? = null, // JSON array of timestamps
    val isFavorite: Boolean = false
)
