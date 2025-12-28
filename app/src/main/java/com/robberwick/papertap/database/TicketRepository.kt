package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
import org.json.JSONArray

class TicketRepository(context: Context) {
    private val ticketDao = TicketDatabase.getDatabase(context).ticketDao()

    val allTickets: LiveData<List<TicketEntity>> = ticketDao.getAllTickets()

    suspend fun insert(ticket: TicketEntity): Long {
        return ticketDao.insert(ticket)
    }

    suspend fun update(ticket: TicketEntity) {
        ticketDao.update(ticket)
    }

    suspend fun delete(ticket: TicketEntity) {
        ticketDao.delete(ticket)
    }

    suspend fun deleteById(id: Long) {
        ticketDao.deleteById(id)
    }

    suspend fun getById(id: Long): TicketEntity? {
        return ticketDao.getById(id)
    }

    /**
     * Insert a ticket with raw barcode data and user label
     * Returns the ID of the inserted ticket, or the ID of an existing duplicate if found
     */
    suspend fun insertTicket(rawData: String, format: Int, userLabel: String): Long {
        // Check for duplicate by raw barcode data
        val existing = ticketDao.findDuplicate(rawData)
        if (existing != null) {
            return existing.id
        }

        // No duplicate found, insert new ticket
        val ticket = TicketEntity(
            userLabel = userLabel,
            rawBarcodeData = rawData,
            barcodeFormat = format
        )
        return ticketDao.insert(ticket)
    }

    /**
     * Update a ticket's label
     */
    suspend fun updateTicketLabel(ticketId: Long, newLabel: String) {
        ticketDao.updateLabel(ticketId, newLabel)
    }

    /**
     * Get the most recently created ticket
     */
    suspend fun getMostRecentTicket(): TicketEntity? {
        val tickets = ticketDao.getAllTicketsSync()
        return tickets.firstOrNull()
    }

    /**
     * Record a successful flash event for a ticket
     * Updates: lastFlashedAt, flashCount, and flashHistory
     */
    suspend fun recordFlashEvent(ticketId: Long) {
        val ticket = ticketDao.getById(ticketId) ?: return
        val timestamp = System.currentTimeMillis()

        // Parse existing flash history or create new array
        val historyArray = try {
            ticket.flashHistory?.let { JSONArray(it) } ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }

        // Add new timestamp
        historyArray.put(timestamp)

        // Update ticket with new metrics
        val updatedTicket = ticket.copy(
            lastFlashedAt = timestamp,
            flashCount = ticket.flashCount + 1,
            flashHistory = historyArray.toString()
        )

        ticketDao.update(updatedTicket)
    }

    /**
     * Toggle favorite status for a ticket
     */
    suspend fun toggleFavorite(ticketId: Long) {
        val ticket = ticketDao.getById(ticketId) ?: return
        val updatedTicket = ticket.copy(isFavorite = !ticket.isFavorite)
        ticketDao.update(updatedTicket)
    }
}
