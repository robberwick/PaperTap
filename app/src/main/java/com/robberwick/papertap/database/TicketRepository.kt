package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
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
}
