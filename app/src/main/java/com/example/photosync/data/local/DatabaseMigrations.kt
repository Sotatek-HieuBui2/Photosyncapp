package com.example.photosync.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            rebuildMediaItemsTableToV3(database)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            rebuildMediaItemsTableToV3(database)
        }
    }

    // v4 keeps the same table shape as v3.
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_media_items_dateAdded ON media_items(dateAdded)"
            )
        }
    }

    private fun rebuildMediaItemsTableToV3(database: SupportSQLiteDatabase) {
        val existingColumns = readExistingColumns(database)

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_items_new (
                id TEXT NOT NULL,
                fileName TEXT NOT NULL,
                filePath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                dateAdded INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0,
                isLocal INTEGER NOT NULL DEFAULT 1,
                remoteUrl TEXT,
                googlePhotosId TEXT,
                lastSyncedAt INTEGER,
                tags TEXT,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )

        val selectExpressions = listOf(
            expr(existingColumns, "id", "''"),
            expr(existingColumns, "fileName", "'Unknown'"),
            expr(existingColumns, "filePath", "''"),
            expr(existingColumns, "mimeType", "'application/octet-stream'"),
            expr(existingColumns, "fileSize", "0"),
            expr(existingColumns, "dateAdded", "0"),
            expr(existingColumns, "isSynced", "0"),
            expr(existingColumns, "isLocal", "1"),
            expr(existingColumns, "remoteUrl", "NULL"),
            expr(existingColumns, "googlePhotosId", "NULL"),
            expr(existingColumns, "lastSyncedAt", "NULL"),
            expr(existingColumns, "tags", "NULL")
        ).joinToString(", ")

        database.execSQL(
            """
            INSERT INTO media_items_new (
                id, fileName, filePath, mimeType, fileSize, dateAdded, isSynced, isLocal,
                remoteUrl, googlePhotosId, lastSyncedAt, tags
            )
            SELECT $selectExpressions FROM media_items
            """.trimIndent()
        )

        database.execSQL("DROP TABLE media_items")
        database.execSQL("ALTER TABLE media_items_new RENAME TO media_items")

        database.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_isSynced ON media_items(isSynced)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_isLocal ON media_items(isLocal)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_media_items_googlePhotosId ON media_items(googlePhotosId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_dateAdded ON media_items(dateAdded)")
    }

    private fun expr(existingColumns: Set<String>, column: String, fallback: String): String {
        return if (existingColumns.contains(column)) column else fallback
    }

    private fun readExistingColumns(database: SupportSQLiteDatabase): Set<String> {
        val columns = mutableSetOf<String>()
        database.query("PRAGMA table_info(media_items)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        return columns
    }
}
