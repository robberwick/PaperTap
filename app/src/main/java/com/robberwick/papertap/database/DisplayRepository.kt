package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData

class DisplayRepository(context: Context) {
    private val displayDao: DisplayDao = TicketDatabase.getDatabase(context).displayDao()
    private val mappingDao: TicketDisplayMappingDao = TicketDatabase.getDatabase(context).ticketDisplayMappingDao()

    val allDisplays: LiveData<List<DisplayEntity>> = displayDao.getAll()

    suspend fun getByUid(uid: String): DisplayEntity? {
        return displayDao.getByUid(uid)
    }

    suspend fun getOrCreateDisplay(tagUid: String): DisplayEntity {
        var display = displayDao.getByUid(tagUid)
        if (display == null) {
            // Create new display with default values
            display = DisplayEntity(tagUid = tagUid)
            displayDao.insert(display)
            // Fetch again to get the auto-generated ID
            display = displayDao.getByUid(tagUid)!!
        }
        return display
    }

    suspend fun recordUsage(uid: String) {
        displayDao.recordUsage(uid, System.currentTimeMillis())
    }

    suspend fun updateLabel(uid: String, label: String?) {
        displayDao.updateLabel(uid, label)
    }

    suspend fun delete(display: DisplayEntity) {
        // Also clean up orphaned mappings
        mappingDao.removeMappingsForDisplay(display.tagUid)
        displayDao.delete(display)
    }

    suspend fun insert(display: DisplayEntity): Long {
        return displayDao.insert(display)
    }
}
