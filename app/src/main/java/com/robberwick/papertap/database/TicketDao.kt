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
}
