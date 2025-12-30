package com.robberwick.papertap.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DisplayDao {
    @Query("SELECT * FROM displays ORDER BY lastUsedAt DESC")
    fun getAll(): LiveData<List<DisplayEntity>>

    @Query("SELECT * FROM displays WHERE tagUid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): DisplayEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(display: DisplayEntity): Long

    @Update
    suspend fun update(display: DisplayEntity)

    @Delete
    suspend fun delete(display: DisplayEntity)

    @Query("UPDATE displays SET userLabel = :label WHERE tagUid = :uid")
    suspend fun updateLabel(uid: String, label: String?)

    @Query("""
        UPDATE displays 
        SET lastUsedAt = :timestamp, useCount = useCount + 1 
        WHERE tagUid = :uid
    """)
    suspend fun recordUsage(uid: String, timestamp: Long)
}
