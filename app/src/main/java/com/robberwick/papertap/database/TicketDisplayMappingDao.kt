package com.robberwick.papertap.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TicketDisplayMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: TicketDisplayMapping)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<TicketDisplayMapping>)

    @Query("SELECT * FROM ticket_display_mapping WHERE ticketId = :ticketId")
    suspend fun getMappingsForTicket(ticketId: Long): List<TicketDisplayMapping>



    @Query("DELETE FROM ticket_display_mapping WHERE displayUid = :displayUid")
    suspend fun removeMappingsForDisplay(displayUid: String)
    
    /**
     * Remove a specific display from all tickets EXCEPT the specified ticket.
     * Used to enforce one-ticket-per-display constraint.
     */
    @Query("DELETE FROM ticket_display_mapping WHERE displayUid = :displayUid AND ticketId != :exceptTicketId")
    suspend fun removeDisplayFromOtherTickets(displayUid: String, exceptTicketId: Long)
}
