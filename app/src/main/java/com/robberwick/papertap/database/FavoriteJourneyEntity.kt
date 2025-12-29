package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_journeys")
data class FavoriteJourneyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val originStationCode: String,      // CRS code e.g. "HIT"
    val destinationStationCode: String, // CRS code e.g. "CBG"
    val label: String,                  // User label e.g. "commute"
    val createdAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0,            // Track usage for future sorting
    val lastUsedAt: Long? = null        // Track last usage
)
