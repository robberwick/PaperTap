package com.robberwick.papertap.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Preserve mappings before rebuilding tickets: the existing mapping table
        // has a CASCADE FK to tickets, so dropping tickets first could erase them.
        database.execSQL(
            """
            CREATE TEMP TABLE `ticket_display_mapping_backup` AS
            SELECT `ticketId`, `displayUid`, `flashedAt`
            FROM `ticket_display_mapping`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `ticket_display_mapping`")

        // Remove the unread, unbounded flashHistory JSON column while preserving
        // every user-visible ticket field and usage counter.
        database.execSQL(
            """
            CREATE TABLE `tickets_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userLabel` TEXT NOT NULL,
                `rawBarcodeData` TEXT NOT NULL,
                `barcodeFormat` INTEGER NOT NULL,
                `originStationCode` TEXT,
                `destinationStationCode` TEXT,
                `travelDate` INTEGER,
                `addedAt` INTEGER NOT NULL,
                `lastFlashedAt` INTEGER,
                `flashCount` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `tickets_new` (
                `id`, `userLabel`, `rawBarcodeData`, `barcodeFormat`,
                `originStationCode`, `destinationStationCode`, `travelDate`,
                `addedAt`, `lastFlashedAt`, `flashCount`, `isFavorite`
            )
            SELECT
                `id`, `userLabel`, `rawBarcodeData`, `barcodeFormat`,
                `originStationCode`, `destinationStationCode`, `travelDate`,
                `addedAt`, `lastFlashedAt`, `flashCount`, `isFavorite`
            FROM `tickets`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `tickets`")
        database.execSQL("ALTER TABLE `tickets_new` RENAME TO `tickets`")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tickets_rawBarcodeData` ON `tickets` (`rawBarcodeData`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tickets_addedAt` ON `tickets` (`addedAt`)",
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                `index_favorite_journeys_originStationCode_destinationStationCode`
            ON `favorite_journeys` (`originStationCode`, `destinationStationCode`)
            """.trimIndent(),
        )

        // Add the missing display FK. Filter legacy orphan mappings rather than
        // failing the whole migration: old code explicitly allowed those rows.
        database.execSQL(
            """
            CREATE TABLE `ticket_display_mapping` (
                `ticketId` INTEGER NOT NULL,
                `displayUid` TEXT NOT NULL,
                `flashedAt` INTEGER NOT NULL,
                PRIMARY KEY(`ticketId`, `displayUid`),
                FOREIGN KEY(`ticketId`) REFERENCES `tickets`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`displayUid`) REFERENCES `displays`(`tagUid`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `ticket_display_mapping` (`ticketId`, `displayUid`, `flashedAt`)
            SELECT backup.`ticketId`, backup.`displayUid`, backup.`flashedAt`
            FROM `ticket_display_mapping_backup` AS backup
            INNER JOIN `tickets` ON `tickets`.`id` = backup.`ticketId`
            INNER JOIN `displays` ON `displays`.`tagUid` = backup.`displayUid`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `ticket_display_mapping_backup`")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ticket_display_mapping_ticketId` ON `ticket_display_mapping` (`ticketId`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ticket_display_mapping_displayUid` ON `ticket_display_mapping` (`displayUid`)",
        )
    }
}
