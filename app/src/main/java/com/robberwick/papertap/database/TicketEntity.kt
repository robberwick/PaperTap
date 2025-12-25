package com.robberwick.papertap.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.robberwick.papertap.TicketData

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val journeySummary: String,
    val dateTime: String,
    val ticketType: String?,
    val reference: String?,
    val qrCodeImagePath: String,

    // Store TicketData as JSON
    val ticketDataJson: String?,

    val createdAt: Long = System.currentTimeMillis(),

    // Store origin and destination separately for duplicate detection
    val originStation: String? = null,
    val destinationStation: String? = null
) {
    companion object {
        fun fromTicketData(
            ticketData: TicketData?,
            qrCodeImagePath: String
        ): TicketEntity {
            val origin = ticketData?.originStation ?: "Unknown"
            val dest = ticketData?.destinationStation ?: "Unknown"
            val journeySummary = "$origin → $dest"

            val date = ticketData?.travelDate ?: ""
            val time = ticketData?.travelTime ?: ""
            val shouldShowTime = time.isNotEmpty() && time != "00:00"
            val dateTime = when {
                date.isNotEmpty() && shouldShowTime -> "$date $time"
                date.isNotEmpty() -> date
                else -> "Unknown"
            }

            val ticketType = ticketData?.ticketType
            val ticketClass = ticketData?.ticketClass
            val formattedTicketType = when {
                ticketType != null && ticketClass != null -> "$ticketType • $ticketClass"
                ticketType != null -> ticketType
                else -> null
            }

            return TicketEntity(
                journeySummary = journeySummary,
                dateTime = dateTime,
                ticketType = formattedTicketType,
                reference = ticketData?.ticketReference,
                qrCodeImagePath = qrCodeImagePath,
                ticketDataJson = ticketData?.toJson(),
                originStation = ticketData?.originStation,
                destinationStation = ticketData?.destinationStation
            )
        }
    }

    fun getTicketData(): TicketData? {
        return ticketDataJson?.let { TicketData.fromJson(it) }
    }
}
