package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_7_TO_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `PdfAnnotationSaveDestination` (
                    `sourceKey` TEXT NOT NULL,
                    `destinationUri` TEXT NOT NULL,
                    `lastSavedHash` TEXT,
                    `lastSavedAt` TEXT NOT NULL,
                    PRIMARY KEY(`sourceKey`)
                )
                """.trimIndent()
            )
            if (tableExists(db, "PdfSaveTarget")) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `PdfAnnotationSaveDestination` (`sourceKey`, `destinationUri`, `lastSavedHash`, `lastSavedAt`)
                    SELECT `sourceKey`, `targetUri`, `lastSavedHash`, `lastSavedAt` FROM `PdfSaveTarget`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `PdfSaveTarget`")
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
                return cursor.moveToFirst()
            }
        }
    }
}
