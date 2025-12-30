package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
import org.json.JSONArray

class TicketRepository(context: Context) {
    private val ticketDao = TicketDatabase.getDatabase(context).ticketDao()
    private val mappingDao = TicketDatabase.getDatabase(context).ticketDisplayMappingDao()

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
     * Find a ticket with matching barcode data
     */
    suspend fun findByBarcodeData(rawData: String): TicketEntity? {
        return ticketDao.findDuplicate(rawData)
    }

    /**
     * Insert a ticket with raw barcode data and user label
     * Returns the ID of the inserted ticket, or the ID of an existing duplicate if found
     */
    suspend fun insertTicket(
        rawData: String,
        format: Int,
        userLabel: String,
        originStationCode: String? = null,
        destinationStationCode: String? = null,
        travelDate: Long? = null
    ): Long {
        // Check for duplicate by raw barcode data
        val existing = ticketDao.findDuplicate(rawData)
        if (existing != null) {
            return existing.id
        }

        // No duplicate found, insert new ticket
        val ticket = TicketEntity(
            userLabel = userLabel,
            rawBarcodeData = rawData,
            barcodeFormat = format,
            originStationCode = originStationCode,
            destinationStationCode = destinationStationCode,
            travelDate = travelDate
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

    /**
     * Add a display to a ticket's list of displays.
     * IMPORTANT: This enforces the one-ticket-per-display constraint by removing 
     * the display from all other tickets first.
     */
    suspend fun addDisplayToTicket(ticketId: Long, displayUid: String) {
        // 1. Remove this display from all other tickets (enforce one-ticket-per-display)
        mappingDao.removeDisplayFromOtherTickets(displayUid, ticketId)
        
        // 2. Add/update the mapping for current ticket
        val mapping = TicketDisplayMapping(
            ticketId = ticketId,
            displayUid = displayUid,
            flashedAt = System.currentTimeMillis()
        )
        mappingDao.insert(mapping)
    }

    /**
     * Get all display UIDs for a ticket
     */
    suspend fun getDisplayUidsForTicket(ticketId: Long): List<String> {
        return mappingDao.getDisplayUidsForTicket(ticketId)
    }

    /**
     * Clear all displays from a ticket
     */
    suspend fun clearDisplaysForTicket(ticketId: Long) {
        mappingDao.clearDisplaysForTicket(ticketId)
    }

    /**
     * Remove a specific display from a ticket
     */
    suspend fun removeDisplayFromTicket(ticketId: Long, displayUid: String) {
        mappingDao.removeMapping(ticketId, displayUid)
    }
}
