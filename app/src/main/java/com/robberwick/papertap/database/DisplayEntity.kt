package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "displays",
    indices = [Index(value = ["tagUid"], unique = true)]
)
data class DisplayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Unique NFC tag UID in hex format (e.g., "UID:04:AB:CD:EF:12:34:56")
    val tagUid: String,

    // User-defined label (e.g., "Home Display", "Work Badge")
    val userLabel: String? = null,

    // Timestamp when first detected
    val addedAt: Long = System.currentTimeMillis(),

    // Timestamp of last flash
    val lastUsedAt: Long? = null,

    // Total number of times flashed to
    val useCount: Int = 0
)
