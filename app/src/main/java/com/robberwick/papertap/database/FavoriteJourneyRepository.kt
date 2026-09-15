package com.robberwick.papertap.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction

private const val MAX_FAVORITES = 50

class FavoriteJourneyRepository(context: Context) {

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


    suspend fun getFavoritesCount(): Int {
        return favoriteJourneyDao.getFavoritesCount()
    }

    /** Distinguishes a successful save from hitting the 50-favorite cap. */
    sealed interface SaveFavoriteResult {
        data class Saved(val id: Long) : SaveFavoriteResult
        data object LimitReached : SaveFavoriteResult
    }

    /**
     * Insert a new favorite journey. The 50-entry cap is enforced inside a
     * Room transaction so a concurrent save cannot slip past the count check
     * (C8). The default-label flag is persisted at insert time.
     */
    suspend fun insertFavoriteWithLimit(
        originCode: String,
        destinationCode: String,
        label: String,
    ): SaveFavoriteResult {
        return database.withTransaction {
            if (favoriteJourneyDao.getFavoritesCount() >= MAX_FAVORITES) {
                return@withTransaction SaveFavoriteResult.LimitReached
            }
            val favorite = FavoriteJourneyEntity(
                originStationCode = originCode,
                destinationStationCode = destinationCode,
                label = label,
                isDefaultLabel = label == generateDefaultLabel(originCode, destinationCode),
            )
            SaveFavoriteResult.Saved(favoriteJourneyDao.insert(favorite))
        }
    }

    /**
     * Update a favorite's label, recomputing the default-label flag so the
     * list UI never needs string comparison (C8).
     */
    suspend fun updateLabel(id: Long, newLabel: String) {
        val favorite = favoriteJourneyDao.getById(id) ?: return
        val isDefault = newLabel == generateDefaultLabel(
            favorite.originStationCode,
            favorite.destinationStationCode,
        )
        favoriteJourneyDao.updateLabelWithFlag(id, newLabel, isDefault)
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
