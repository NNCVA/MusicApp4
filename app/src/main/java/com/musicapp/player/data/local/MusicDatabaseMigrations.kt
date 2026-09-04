package com.musicapp.player.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tracks ADD COLUMN last_seen_sync_generation INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_sync_state (
                state_id INTEGER NOT NULL,
                last_generation INTEGER NOT NULL,
                PRIMARY KEY(state_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_volume_sync_state (
                volume_name TEXT NOT NULL,
                availability TEXT NOT NULL,
                last_successful_generation INTEGER,
                last_complete_generation INTEGER,
                media_store_version TEXT,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY(volume_name)
            )
            """.trimIndent(),
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN track_number INTEGER")
        db.execSQL("ALTER TABLE tracks ADD COLUMN disc_number INTEGER")
        db.execSQL("ALTER TABLE tracks ADD COLUMN release_year INTEGER")
    }
}

