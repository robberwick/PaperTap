package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction

data class DeletedTicketSnapshot(
    val ticket: TicketEntity,
    val mappings: List<TicketDisplayMapping>,
)


class TicketRepository(context: Context) {
    private val database = TicketDatabase.getDatabase(context)
    private val ticketDao = database.ticketDao()
    private val mappingDao = database.ticketDisplayMappingDao()

    val allTickets: LiveData<List<TicketWithDisplays>> = ticketDao.getTicketsWithDisplays()


    suspend fun update(ticket: TicketEntity) {
        ticketDao.update(ticket)
    }

    suspend fun deleteWithMappings(ticket: TicketEntity): DeletedTicketSnapshot {
        return database.withTransaction {
            val snapshot = DeletedTicketSnapshot(
                ticket = ticket,
                mappings = mappingDao.getMappingsForTicket(ticket.id),
            )
            ticketDao.delete(ticket)
            snapshot
        }
    }

    suspend fun restoreDeleted(snapshot: DeletedTicketSnapshot) {
        database.withTransaction {
            ticketDao.insert(snapshot.ticket)
            mappingDao.insertAll(snapshot.mappings)
        }
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

    /** Distinguishes a fresh insert from a barcode that already exists in the collection. */
    sealed interface InsertTicketResult {
        data class Inserted(val ticketId: Long) : InsertTicketResult
        data class Duplicate(val existingTicketId: Long) : InsertTicketResult
    }

    /**
     * Insert a ticket with raw barcode data and user label.
     * Returns [InsertTicketResult.Inserted] for a new ticket, or
     * [InsertTicketResult.Duplicate] when the barcode already exists.
     */
    suspend fun insertTicket(
        rawData: String,
        format: Int,
        userLabel: String,
        originStationCode: String? = null,
        destinationStationCode: String? = null,
        travelDate: Long? = null
    ): InsertTicketResult {
        // Check for duplicate by raw barcode data
        val existing = ticketDao.findDuplicate(rawData)
        if (existing != null) {
            return InsertTicketResult.Duplicate(existing.id)
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
        return InsertTicketResult.Inserted(ticketDao.insert(ticket))
    }

    /**
     * Update a ticket's label
     */
    suspend fun updateTicketLabel(ticketId: Long, newLabel: String) {
        ticketDao.updateLabel(ticketId, newLabel)
    }


    /** Atomically record a successful flash so concurrent writes cannot lose counts. */
    suspend fun recordFlashEvent(ticketId: Long) {
        ticketDao.recordFlashEvent(ticketId, System.currentTimeMillis())
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

}
