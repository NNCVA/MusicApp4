package com.musicapp.player.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDatabaseMigrationTest {
    @Test
    fun migrationFromV1PreservesDataRelationsAndAddsSyncStateTables() {
        val databaseName = "music-database-migration-test"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        createV1Database(context, databaseName).close()

        val migratedDatabase = Room.databaseBuilder(context, MusicDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        val migrated = migratedDatabase.openHelper.writableDatabase

        migrated.query(
            "SELECT title, last_seen_sync_generation, track_number, disc_number, release_year FROM tracks " +
                "WHERE volume_name = 'external_primary' AND media_store_id = 42",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Migration track", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.query("SELECT display_name FROM playlists WHERE playlist_id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Migration playlist", cursor.getString(0))
        }
        migrated.query(
            "SELECT track_volume_name, track_media_store_id, position FROM playlist_tracks " +
                "WHERE playlist_id = 7",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("external_primary", cursor.getString(0))
            assertEquals(42L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
        }
        migrated.query(
            "SELECT last_played_at_ms, play_count FROM play_history " +
                "WHERE track_volume_name = 'external_primary' AND track_media_store_id = 42",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20_000L, cursor.getLong(0))
            assertEquals(3L, cursor.getLong(1))
        }
        migrated.query(
            "SELECT hidden_at_ms FROM hidden_tracks " +
                "WHERE track_volume_name = 'external_primary' AND track_media_store_id = 42",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(21_000L, cursor.getLong(0))
        }
        migrated.query("SELECT directory, kind FROM path_rules WHERE path_rule_id = 9").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Music/Excluded/", cursor.getString(0))
            assertEquals("EXCLUDE", cursor.getString(1))
        }
        migrated.query(
            "SELECT original_queue_json, shuffle_round, position_ms " +
                "FROM playback_snapshot WHERE snapshot_id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[42]", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals(1_500L, cursor.getLong(2))
        }

        migrated.execSQL(
            "INSERT INTO media_sync_state(state_id, last_generation) VALUES(1, 12)",
        )
        migrated.execSQL(
            "INSERT INTO media_volume_sync_state(" +
                "volume_name, availability, last_successful_generation, " +
                "last_complete_generation, media_store_version, updated_at_ms" +
                ") VALUES('external_primary', 'AVAILABLE', 12, 12, 'store-v1', 22000)",
        )
        migrated.query("SELECT last_generation FROM media_sync_state WHERE state_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(12L, cursor.getLong(0))
        }
        migrated.query(
            "SELECT availability, media_store_version, updated_at_ms " +
                "FROM media_volume_sync_state WHERE volume_name = 'external_primary'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("AVAILABLE", cursor.getString(0))
            assertEquals("store-v1", cursor.getString(1))
            assertEquals(22_000L, cursor.getLong(2))
        }
        migratedDatabase.close()
        context.deleteDatabase(databaseName)
    }

    private fun createV1Database(context: Context, databaseName: String): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV1Schema(db)
                    insertV1Data(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).also { helper ->
            helper.writableDatabase
        }
    }

    private fun createV1Schema(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE tracks(" +
                "volume_name TEXT NOT NULL, media_store_id INTEGER NOT NULL, title TEXT NOT NULL, " +
                "artist_name TEXT NOT NULL, artist_media_store_id INTEGER, album_title TEXT, " +
                "album_volume_name TEXT, album_media_store_id INTEGER, duration_ms INTEGER NOT NULL, " +
                "date_added_ms INTEGER NOT NULL, date_modified_ms INTEGER NOT NULL, relative_path TEXT NOT NULL, " +
                "display_name TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER NOT NULL, " +
                "availability TEXT NOT NULL, PRIMARY KEY(volume_name, media_store_id))",
        )
        database.execSQL("CREATE INDEX index_tracks_title ON tracks(title)")
        database.execSQL("CREATE INDEX index_tracks_artist_media_store_id ON tracks(artist_media_store_id)")
        database.execSQL(
            "CREATE INDEX index_tracks_album_volume_name_album_media_store_id " +
                "ON tracks(album_volume_name, album_media_store_id)",
        )
        database.execSQL("CREATE INDEX index_tracks_date_added_ms ON tracks(date_added_ms)")
        database.execSQL("CREATE INDEX index_tracks_duration_ms ON tracks(duration_ms)")
        database.execSQL(
            "CREATE INDEX index_tracks_volume_name_relative_path ON tracks(volume_name, relative_path)",
        )
        database.execSQL(
            "CREATE TABLE playlists(" +
                "playlist_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, display_name TEXT NOT NULL, " +
                "normalized_name TEXT NOT NULL COLLATE NOCASE, created_at_ms INTEGER NOT NULL, " +
                "updated_at_ms INTEGER NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX index_playlists_normalized_name ON playlists(normalized_name)")
        database.execSQL(
            "CREATE TABLE playlist_tracks(" +
                "playlist_id INTEGER NOT NULL, track_volume_name TEXT NOT NULL, " +
                "track_media_store_id INTEGER NOT NULL, position INTEGER NOT NULL, " +
                "PRIMARY KEY(playlist_id, track_volume_name, track_media_store_id), " +
                "FOREIGN KEY(playlist_id) REFERENCES playlists(playlist_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(track_volume_name, track_media_store_id) REFERENCES tracks(volume_name, media_store_id) ON DELETE CASCADE)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX index_playlist_tracks_playlist_id_position " +
                "ON playlist_tracks(playlist_id, position)",
        )
        database.execSQL(
            "CREATE INDEX index_playlist_tracks_track_volume_name_track_media_store_id " +
                "ON playlist_tracks(track_volume_name, track_media_store_id)",
        )
        database.execSQL(
            "CREATE TABLE play_history(" +
                "track_volume_name TEXT NOT NULL, track_media_store_id INTEGER NOT NULL, " +
                "last_played_at_ms INTEGER NOT NULL, play_count INTEGER NOT NULL, " +
                "PRIMARY KEY(track_volume_name, track_media_store_id), " +
                "FOREIGN KEY(track_volume_name, track_media_store_id) REFERENCES tracks(volume_name, media_store_id) ON DELETE CASCADE)",
        )
        database.execSQL("CREATE INDEX index_play_history_last_played_at_ms ON play_history(last_played_at_ms)")
        database.execSQL(
            "CREATE TABLE hidden_tracks(" +
                "track_volume_name TEXT NOT NULL, track_media_store_id INTEGER NOT NULL, hidden_at_ms INTEGER NOT NULL, " +
                "PRIMARY KEY(track_volume_name, track_media_store_id), " +
                "FOREIGN KEY(track_volume_name, track_media_store_id) REFERENCES tracks(volume_name, media_store_id) ON DELETE CASCADE)",
        )
        database.execSQL(
            "CREATE TABLE path_rules(" +
                "path_rule_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, volume_name TEXT NOT NULL, " +
                "directory TEXT NOT NULL, kind TEXT NOT NULL)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX index_path_rules_volume_name_directory_kind " +
                "ON path_rules(volume_name, directory, kind)",
        )
        database.execSQL(
            "CREATE TABLE playback_snapshot(" +
                "snapshot_id INTEGER NOT NULL, original_queue_json TEXT NOT NULL, " +
                "stable_shuffle_sequence_json TEXT NOT NULL, current_queue_item_id INTEGER, " +
                "shuffle_round INTEGER NOT NULL, shuffle_cursor INTEGER, position_ms INTEGER NOT NULL, " +
                "playback_mode TEXT NOT NULL, playback_instance_json TEXT, updated_at_ms INTEGER NOT NULL, " +
                "playback_resumption_allowed INTEGER NOT NULL, PRIMARY KEY(snapshot_id))",
        )
    }

    private fun insertV1Data(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO tracks(" +
                "volume_name, media_store_id, title, artist_name, artist_media_store_id, " +
                "album_title, album_volume_name, album_media_store_id, duration_ms, " +
                "date_added_ms, date_modified_ms, relative_path, display_name, mime_type, " +
                "size_bytes, availability" +
                ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                "external_primary", 42L, "Migration track", "Artist", 7L,
                "Album", "external_primary", 8L, 180_000L, 1_000L, 2_000L,
                "Music/", "migration.mp3", "audio/mpeg", 4_096L, "AVAILABLE",
            ),
        )
        database.execSQL(
            "INSERT INTO playlists(playlist_id, display_name, normalized_name, created_at_ms, updated_at_ms) " +
                "VALUES(7, 'Migration playlist', 'migration', 10, 11)",
        )
        database.execSQL(
            "INSERT INTO playlist_tracks(playlist_id, track_volume_name, track_media_store_id, position) " +
                "VALUES(7, 'external_primary', 42, 0)",
        )
        database.execSQL(
            "INSERT INTO play_history(track_volume_name, track_media_store_id, last_played_at_ms, play_count) " +
                "VALUES('external_primary', 42, 20000, 3)",
        )
        database.execSQL(
            "INSERT INTO hidden_tracks(track_volume_name, track_media_store_id, hidden_at_ms) " +
                "VALUES('external_primary', 42, 21000)",
        )
        database.execSQL(
            "INSERT INTO path_rules(path_rule_id, volume_name, directory, kind) " +
                "VALUES(9, 'external_primary', 'Music/Excluded/', 'EXCLUDE')",
        )
        database.execSQL(
            "INSERT INTO playback_snapshot(" +
                "snapshot_id, original_queue_json, stable_shuffle_sequence_json, " +
                "current_queue_item_id, shuffle_round, shuffle_cursor, position_ms, " +
                "playback_mode, playback_instance_json, updated_at_ms, playback_resumption_allowed" +
                ") VALUES(1, '[42]', '[42]', 42, 2, 0, 1500, 'LIST_REPEAT', NULL, 21000, 1)",
        )
    }
}
