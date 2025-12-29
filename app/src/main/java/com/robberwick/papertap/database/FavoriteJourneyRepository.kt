package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData

class FavoriteJourneyRepository(context: Context) {
    private val favoriteJourneyDao = TicketDatabase.getDatabase(context).favoriteJourneyDao()

    val allFavorites: LiveData<List<FavoriteJourneyEntity>> = favoriteJourneyDao.getAllFavorites()

    suspend fun insert(favorite: FavoriteJourneyEntity): Long {
        return favoriteJourneyDao.insert(favorite)
    }

    suspend fun update(favorite: FavoriteJourneyEntity) {
        favoriteJourneyDao.update(favorite)
    }

    suspend fun delete(favorite: FavoriteJourneyEntity) {
        favoriteJourneyDao.delete(favorite)
    }

    suspend fun deleteById(id: Long) {
        favoriteJourneyDao.deleteById(id)
    }

    suspend fun getById(id: Long): FavoriteJourneyEntity? {
        return favoriteJourneyDao.getById(id)
    }

    suspend fun getFavoritesCount(): Int {
        return favoriteJourneyDao.getFavoritesCount()
    }

    /**
     * Insert a new favorite journey
     * Returns the ID of the inserted favorite
     * Note: Per requirements, duplicates ARE allowed (users can have same route with different labels)
     */
    suspend fun insertFavorite(
        originCode: String,
        destinationCode: String,
        label: String
    ): Long {
        val favorite = FavoriteJourneyEntity(
            originStationCode = originCode,
            destinationStationCode = destinationCode,
            label = label
        )
        return favoriteJourneyDao.insert(favorite)
    }

    /**
     * Update a favorite's label
     */
    suspend fun updateLabel(id: Long, newLabel: String) {
        favoriteJourneyDao.updateLabel(id, newLabel)
    }

    /**
     * Record that a favorite was used (for analytics/sorting)
     */
    suspend fun recordUsage(id: Long) {
        favoriteJourneyDao.recordUsage(id)
    }

    /**
     * Generate default label for a journey
     * Uses StationLookup to get human-readable names
     */
    fun generateDefaultLabel(originCode: String, destCode: String): String {
        val originName = com.robberwick.papertap.StationLookup.getStationName(originCode)
        val destName = com.robberwick.papertap.StationLookup.getStationName(destCode)
        return "${originName ?: originCode} → ${destName ?: destCode}"
    }
}
