package com.robberwick.papertap.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TicketEntity::class], version = 3, exportSchema = false)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao

    companion object {
        @Volatile
        private var INSTANCE: TicketDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns for duplicate detection
                db.execSQL("ALTER TABLE tickets ADD COLUMN originStation TEXT")
                db.execSQL("ALTER TABLE tickets ADD COLUMN destinationStation TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add usage tracking columns
                db.execSQL("ALTER TABLE tickets ADD COLUMN lastFlashedAt INTEGER")
                db.execSQL("ALTER TABLE tickets ADD COLUMN flashCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN flashHistory TEXT")
                db.execSQL("ALTER TABLE tickets ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): TicketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TicketDatabase::class.java,
                    "ticket_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
