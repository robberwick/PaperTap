package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
import com.robberwick.papertap.BarcodeData
import com.robberwick.papertap.TicketData
import java.io.File

class TicketRepository(context: Context) {
    private val ticketDao = TicketDatabase.getDatabase(context).ticketDao()
    private val ticketsDir = File(context.filesDir, "tickets").apply {
        if (!exists()) mkdirs()
    }

    val allTickets: LiveData<List<TicketEntity>> = ticketDao.getAllTickets()

    suspend fun insert(ticket: TicketEntity): Long {
        return ticketDao.insert(ticket)
    }

    suspend fun update(ticket: TicketEntity) {
        ticketDao.update(ticket)
    }

    suspend fun delete(ticket: TicketEntity) {
        // Delete the associated QR code image file
        try {
            val imageFile = File(ticket.qrCodeImagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            }
        } catch (e: Exception) {
            // Ignore if file doesn't exist or can't be deleted
        }

        ticketDao.delete(ticket)
    }

    suspend fun deleteById(id: Long) {
        val ticket = ticketDao.getById(id)
        if (ticket != null) {
            delete(ticket)
        }
    }

    suspend fun getById(id: Long): TicketEntity? {
        return ticketDao.getById(id)
    }

    fun getQrCodeImagePath(ticketId: Long): String {
        return File(ticketsDir, "ticket_$ticketId.png").absolutePath
    }

    fun getTicketsDirectory(): File {
        return ticketsDir
    }

    /**
     * Insert a ticket from BarcodeData
     * Returns the ID of the inserted ticket, or the ID of an existing duplicate if found
     */
    suspend fun insertTicket(barcodeData: BarcodeData): Long {
        // If there's no TicketData (not a UK rail ticket), create a minimal TicketData
        // that just wraps the raw barcode data
        val ticketData = barcodeData.ticketData ?: TicketData(
            originStation = null,
            destinationStation = null,
            travelDate = null,
            travelTime = null,
            ticketType = null,
            railcardType = null,
            ticketClass = null,
            ticketReference = null,
            rawData = barcodeData.rawData,
            barcodeFormat = barcodeData.barcodeFormat
        )

        // Create a temporary entity to get the formatted data
        val tempEntity = TicketEntity.fromTicketData(ticketData, "")

        // First, check for exact ticketDataJson match (handles all cases including generic barcodes)
        tempEntity.ticketDataJson?.let { jsonString ->
            val exactMatch = ticketDao.findDuplicateByTicketData(jsonString)
            if (exactMatch != null) {
                return exactMatch.id
            }
        }

        // Check by reference + origin + destination
        // This properly handles return journeys with same reference but swapped stations
        val existingTicket = ticketDao.findDuplicate(
            reference = ticketData.ticketReference,
            originStation = ticketData.originStation,
            destinationStation = ticketData.destinationStation
        )

        if (existingTicket != null) {
            return existingTicket.id
        }

        // No duplicate found, insert new ticket
        val imagePath = getQrCodeImagePath(System.currentTimeMillis())
        val ticketEntity = TicketEntity.fromTicketData(ticketData, imagePath)
        return ticketDao.insert(ticketEntity)
    }

    /**
     * Get the most recently created ticket
     */
    suspend fun getMostRecentTicket(): TicketEntity? {
        val tickets = ticketDao.getAllTicketsSync()
        return tickets.firstOrNull()
    }

    /**
     * Check if a duplicate ticket exists
     * Returns the existing ticket if found, null otherwise
     */
    suspend fun checkForDuplicate(ticketData: TicketData?): TicketEntity? {
        // First try exact JSON match
        ticketData?.toJson()?.let { jsonString ->
            val exactMatch = ticketDao.findDuplicateByTicketData(jsonString)
            if (exactMatch != null) {
                return exactMatch
            }
        }

        // Then check by reference + origin + destination
        return ticketDao.findDuplicate(
            reference = ticketData?.ticketReference,
            originStation = ticketData?.originStation,
            destinationStation = ticketData?.destinationStation
        )
    }
}
