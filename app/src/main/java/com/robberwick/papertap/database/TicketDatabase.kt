package com.robberwick.papertap.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TicketEntity::class, FavoriteJourneyEntity::class, DisplayEntity::class, TicketDisplayMapping::class], version = 8, exportSchema = false)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
    abstract fun favoriteJourneyDao(): FavoriteJourneyDao
    abstract fun displayDao(): DisplayDao
    abstract fun ticketDisplayMappingDao(): TicketDisplayMappingDao

    companion object {
        @Volatile
        private var INSTANCE: TicketDatabase? = null

        fun getDatabase(context: Context): TicketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TicketDatabase::class.java,
                    "ticket_database"
                )
                    .fallbackToDestructiveMigration() // No migration - users can clear data
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
