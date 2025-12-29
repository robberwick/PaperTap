package com.robberwick.papertap.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FavoriteJourneyDao {
    @Query("SELECT * FROM favorite_journeys ORDER BY createdAt DESC")
    fun getAllFavorites(): LiveData<List<FavoriteJourneyEntity>>

    @Query("SELECT * FROM favorite_journeys ORDER BY createdAt DESC")
    suspend fun getAllFavoritesSync(): List<FavoriteJourneyEntity>

    @Query("SELECT COUNT(*) FROM favorite_journeys")
    suspend fun getFavoritesCount(): Int

    @Insert
    suspend fun insert(favoriteJourney: FavoriteJourneyEntity): Long

    @Update
    suspend fun update(favoriteJourney: FavoriteJourneyEntity)

    @Delete
    suspend fun delete(favoriteJourney: FavoriteJourneyEntity)

    @Query("DELETE FROM favorite_journeys WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM favorite_journeys WHERE id = :id")
    suspend fun getById(id: Long): FavoriteJourneyEntity?

    /**
     * Check for duplicate journey (same origin + destination, ignoring label)
     */
    @Query("SELECT * FROM favorite_journeys WHERE originStationCode = :origin AND destinationStationCode = :dest LIMIT 1")
    suspend fun findDuplicate(origin: String, dest: String): FavoriteJourneyEntity?

    /**
     * Update a favorite's label
     */
    @Query("UPDATE favorite_journeys SET label = :newLabel WHERE id = :id")
    suspend fun updateLabel(id: Long, newLabel: String)

    /**
     * Record that a favorite was used (for analytics/sorting)
     */
    @Query("UPDATE favorite_journeys SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun recordUsage(id: Long, timestamp: Long = System.currentTimeMillis())
}
