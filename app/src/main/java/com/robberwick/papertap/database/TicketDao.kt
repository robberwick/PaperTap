package com.robberwick.papertap.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY dateTime DESC, createdAt DESC")
    fun getAllTickets(): LiveData<List<TicketEntity>>

    @Query("SELECT * FROM tickets ORDER BY dateTime DESC, createdAt DESC")
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
     * Find a potential duplicate ticket by comparing reference, origin, and destination.
     * This properly handles return journeys where both legs have the same reference but swapped stations.
     */
    @Query("""
        SELECT * FROM tickets
        WHERE (reference = :reference OR (reference IS NULL AND :reference IS NULL))
        AND (originStation = :originStation OR (originStation IS NULL AND :originStation IS NULL))
        AND (destinationStation = :destinationStation OR (destinationStation IS NULL AND :destinationStation IS NULL))
        LIMIT 1
    """)
    suspend fun findDuplicate(
        reference: String?,
        originStation: String?,
        destinationStation: String?
    ): TicketEntity?

    /**
     * Find a duplicate by exact ticketDataJson match.
     * Used for generic barcodes where raw barcode data should be checked.
     */
    @Query("""
        SELECT * FROM tickets
        WHERE ticketDataJson = :ticketDataJson
        LIMIT 1
    """)
    suspend fun findDuplicateByTicketData(ticketDataJson: String): TicketEntity?
}
