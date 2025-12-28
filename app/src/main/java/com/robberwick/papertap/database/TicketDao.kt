package com.robberwick.papertap.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY addedAt DESC")
    fun getAllTickets(): LiveData<List<TicketEntity>>

    @Query("SELECT * FROM tickets ORDER BY addedAt DESC")
    suspend fun getAllTicketsSync(): List<TicketEntity>

    @Insert
    suspend fun insert(ticket: TicketEntity): Long

    @Update
    suspend fun update(ticket: TicketEntity)

    @Delete
    suspend fun delete(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: Long): TicketEntity?

    @Query("DELETE FROM tickets WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Find a duplicate ticket by raw barcode data
     */
    @Query("SELECT * FROM tickets WHERE rawBarcodeData = :rawData LIMIT 1")
    suspend fun findDuplicate(rawData: String): TicketEntity?

    /**
     * Update a ticket's label
     */
    @Query("UPDATE tickets SET userLabel = :newLabel WHERE id = :ticketId")
    suspend fun updateLabel(ticketId: Long, newLabel: String)
}
