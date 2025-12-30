package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ticket_display_mapping",
    primaryKeys = ["ticketId", "displayUid"],
    foreignKeys = [
        ForeignKey(
            entity = TicketEntity::class,
            parentColumns = ["id"],
            childColumns = ["ticketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ticketId"]),
        Index(value = ["displayUid"])
    ]
)
data class TicketDisplayMapping(
    val ticketId: Long,
    val displayUid: String,
    val flashedAt: Long = System.currentTimeMillis()
)
