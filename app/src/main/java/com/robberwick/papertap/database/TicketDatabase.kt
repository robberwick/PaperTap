package com.robberwick.papertap.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.robberwick.papertap.BuildConfig

@Database(
    entities = [
        TicketEntity::class,
        FavoriteJourneyEntity::class,
        DisplayEntity::class,
        TicketDisplayMapping::class,
    ],
    version = 9,
    exportSchema = true,
)
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
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    TicketDatabase::class.java,
                    "ticket_database",
                ).addMigrations(MIGRATION_8_9)

                // Release builds must fail loudly if a migration is missing;
                // only disposable debug databases may fall back to a rebuild.
                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration()
                }

                builder.build().also { INSTANCE = it }
            }
        }
    }
}
