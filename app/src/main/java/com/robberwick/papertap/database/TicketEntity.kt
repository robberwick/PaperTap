package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tickets",
    indices = [
        Index(value = ["rawBarcodeData"]),
        Index(value = ["addedAt"]),
    ],
)
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
    val isFavorite: Boolean = false
)
