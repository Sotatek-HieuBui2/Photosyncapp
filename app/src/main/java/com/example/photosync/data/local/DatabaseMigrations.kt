package com.example.photosync.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    // v4 keeps the same table shape as v3.
    // Explicit migration prevents destructive fallback and keeps user data.
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_media_items_dateAdded ON media_items(dateAdded)"
            )
        }
    }
}
