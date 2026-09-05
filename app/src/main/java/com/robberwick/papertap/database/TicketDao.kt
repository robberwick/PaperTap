package com.robberwick.papertap.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TicketDao {
    @Transaction
    @Query("SELECT * FROM tickets ORDER BY addedAt DESC")
    fun getTicketsWithDisplays(): LiveData<List<TicketWithDisplays>>

    @Query("UPDATE tickets SET lastFlashedAt = :timestamp, flashCount = flashCount + 1 WHERE id = :ticketId")
    suspend fun recordFlashEvent(ticketId: Long, timestamp: Long)


    @Insert
    suspend fun insert(ticket: TicketEntity): Long

    @Update
    suspend fun update(ticket: TicketEntity)

    @Delete
    suspend fun delete(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: Long): TicketEntity?


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
