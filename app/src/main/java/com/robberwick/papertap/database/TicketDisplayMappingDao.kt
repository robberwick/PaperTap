package com.robberwick.papertap.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TicketDisplayMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: TicketDisplayMapping)

    @Query("SELECT displayUid FROM ticket_display_mapping WHERE ticketId = :ticketId ORDER BY flashedAt DESC")
    suspend fun getDisplayUidsForTicket(ticketId: Long): List<String>

    @Query("DELETE FROM ticket_display_mapping WHERE ticketId = :ticketId")
    suspend fun clearDisplaysForTicket(ticketId: Long)

    @Query("DELETE FROM ticket_display_mapping WHERE ticketId = :ticketId AND displayUid = :displayUid")
    suspend fun removeMapping(ticketId: Long, displayUid: String)

    @Query("DELETE FROM ticket_display_mapping WHERE displayUid = :displayUid")
    suspend fun removeMappingsForDisplay(displayUid: String)
    
    /**
     * Remove a specific display from all tickets EXCEPT the specified ticket.
     * Used to enforce one-ticket-per-display constraint.
     */
    @Query("DELETE FROM ticket_display_mapping WHERE displayUid = :displayUid AND ticketId != :exceptTicketId")
    suspend fun removeDisplayFromOtherTickets(displayUid: String, exceptTicketId: Long)
}
