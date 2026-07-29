package com.musicapp.player.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.data.local.dao.PlaylistTrackIdentity
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDatabaseTest {
  private lateinit var database: MusicDatabase

  @Before
  fun setUp() {
    database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          MusicDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
  }

  @After fun tearDown() = database.close()

  @Test
  fun fullScan_marksMissingTracksUnavailable_withoutDeletingReferences() = runBlocking {
    val first = track(1, generation = 1)
    val second = track(2, generation = 1)
    database.trackDao().commitFullScan(1, listOf(first, second))
    database.hiddenTrackDao().insert(HiddenTrackEntity("external", 2, 10))

    database.trackDao().commitFullScan(2, listOf(first.copy(lastSeenGeneration = 2)))

    assertTrue(database.trackDao().find("external", 1)!!.isAvailable)
    assertFalse(database.trackDao().find("external", 2)!!.isAvailable)
    assertTrue(database.hiddenTrackDao().contains("external", 2))
  }

  @Test
  fun fullScan_failure_rollsBackUpsertsAndAvailabilityChanges() = runBlocking {
    database.trackDao().upsert(listOf(track(1, generation = 1)))
    database.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER force_scan_failure BEFORE UPDATE OF isAvailable ON tracks " +
        "BEGIN SELECT RAISE(ABORT, 'forced scan failure'); END",
    )

    val result = runCatching {
      database.trackDao().commitFullScan(2, listOf(track(2, generation = 2)))
    }

    assertTrue(result.isFailure)
    assertNull(database.trackDao().find("external", 2))
    assertTrue(database.trackDao().find("external", 1)!!.isAvailable)
  }

  @Test
  fun playlistNameAndPositionConstraints_areEnforced() = runBlocking {
    val playlist = PlaylistEntity("p1", "Road", "road", 10, 10)
    database.playlistDao().insert(playlist)
    assertTrue(database.playlistDao().nameExists("road"))

    var duplicateNameRejected = false
    try {
      database.playlistDao().insert(playlist.copy(playlistId = "p2"))
    } catch (_: SQLiteConstraintException) {
      duplicateNameRejected = true
    }
    assertTrue(duplicateNameRejected)

    database.playlistTrackDao().insert(listOf(PlaylistTrackEntity("p1", "external", 1, 0, 10)))
    val collision = database.playlistTrackDao().insert(listOf(PlaylistTrackEntity("p1", "external", 2, 0, 10)))
    assertEquals(listOf(-1L), collision)
  }

  @Test
  fun appendDistinct_preservesSelectionOrderAndReportsSkippedItems() = runBlocking {
    database.playlistDao().insert(PlaylistEntity("p1", "Road", "road", 10, 10))

    val result =
      database.playlistTrackDao().appendDistinct(
        playlistId = "p1",
        tracks =
          listOf(
            PlaylistTrackIdentity("external", 2),
            PlaylistTrackIdentity("external", 1),
            PlaylistTrackIdentity("external", 2),
          ),
        addedAtEpochMillis = 20,
      )

    assertEquals(2, result.addedCount)
    assertEquals(1, result.skippedCount)
    assertEquals(listOf(2L, 1L), database.playlistTrackDao().getForPlaylist("p1").map { it.trackMediaStoreId })
  }

  @Test
  fun deletingPlaylist_cascadesOnlyPlaylistMembership() = runBlocking {
    database.playlistDao().insert(PlaylistEntity("p1", "Road", "road", 10, 10))
    database.playlistTrackDao().insert(listOf(PlaylistTrackEntity("p1", "external", 99, 0, 10)))
    database.hiddenTrackDao().insert(HiddenTrackEntity("external", 99, 10))

    database.playlistDao().delete("p1")

    assertTrue(database.playlistTrackDao().getForPlaylist("p1").isEmpty())
    assertTrue(database.hiddenTrackDao().contains("external", 99))
  }

  @Test
  fun playbackSnapshot_roundTripsAllPersistedState() = runBlocking {
    val snapshot =
      PlaybackSnapshotEntity(
        formatVersion = 1,
        originalQueueJson = "[{\"volumeName\":\"external\",\"mediaStoreId\":1}]",
        shuffledQueueJson = "[{\"volumeName\":\"external\",\"mediaStoreId\":1}]",
        playMode = "SHUFFLE",
        currentTrackVolumeName = "external",
        currentTrackMediaStoreId = 1,
        currentQueueIndex = 0,
        positionMs = 1234,
        playbackInstanceId = "instance-1",
        accumulatedPlayedMs = 900,
        historyRecordedForInstance = true,
      )
    database.playbackSnapshotDao().save(snapshot)

    assertEquals(snapshot, database.playbackSnapshotDao().getActive())
    assertEquals(snapshot, database.playbackSnapshotDao().observeActive().first())
    database.playbackSnapshotDao().clear()
    assertNull(database.playbackSnapshotDao().getActive())
  }

  @Test
  fun versionOneDatabase_opensAndContainsSevenTables() {
    val sqlite = database.openHelper.writableDatabase
    val cursor = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'")
    val names = buildSet {
      cursor.use {
        while (it.moveToNext()) add(it.getString(0))
      }
    }
    assertNotNull(sqlite)
    assertTrue(
      names.containsAll(
        setOf(
          "tracks",
          "playlists",
          "playlist_tracks",
          "play_history",
          "hidden_tracks",
          "path_rules",
          "playback_snapshot",
        ),
      ),
    )
  }

  private fun track(id: Long, generation: Long) =
    TrackEntity(
      volumeName = "external",
      mediaStoreId = id,
      contentUri = "content://media/external/audio/media/$id",
      displayName = "$id.mp3",
      title = "Track $id",
      artistId = 1,
      artistName = "Artist",
      albumId = 1,
      albumName = "Album",
      dateAddedEpochSeconds = 1,
      durationMillis = 60_000,
      relativePath = "Music/",
      mimeType = "audio/mpeg",
      extension = "mp3",
      sizeBytes = 100,
      dateModifiedEpochSeconds = 1,
      isAvailable = true,
      lastSeenGeneration = generation,
    )
}
