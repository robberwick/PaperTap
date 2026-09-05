package com.robberwick.papertap.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TicketWithDisplays(
    @Embedded
    val ticket: TicketEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagUid",
        associateBy = Junction(
            value = TicketDisplayMapping::class,
            parentColumn = "ticketId",
            entityColumn = "displayUid",
        ),
    )
    val displays: List<DisplayEntity>,
)
