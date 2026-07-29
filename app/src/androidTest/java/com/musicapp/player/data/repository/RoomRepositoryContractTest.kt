package com.musicapp.player.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
import com.musicapp.player.core.system.AppClock
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.repository.api.MediaLibraryRepository
import com.musicapp.player.data.repository.api.PlaylistRepository
import com.musicapp.player.data.repository.api.RepositoryResult
import com.musicapp.player.data.repository.fake.FakeHistoryRepository
import com.musicapp.player.data.repository.fake.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.fake.FakePlaylistRepository
import com.musicapp.player.data.repository.room.RoomHistoryRepository
import com.musicapp.player.data.repository.room.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.room.RoomPlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryContractTest {
  private lateinit var database: MusicDatabase
  private lateinit var scope: CoroutineScope
  private val clock = object : AppClock {
    override fun currentTimeMillis() = 1_000L
    override fun elapsedRealtimeMillis() = 2_000L
  }

  @Before
  fun setUp() {
    database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          MusicDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  @After
  fun tearDown() {
    scope.cancel()
    database.close()
  }

  @Test
  fun mediaLibrary_invalidGeneration_matchesFakeContract() = runBlocking {
    val room = roomMediaLibrary()
    val fake = FakeMediaLibraryRepository()

    assertFailureEquals(
      fake.commitFullScan(-1, emptyList()),
      room.commitFullScan(-1, emptyList()),
    )
  }

  @Test
  fun mediaLibrary_missingBatchTarget_doesNotPartiallyUpdate() = runBlocking {
    val initial = track(1, isAvailable = true)
    val room = roomMediaLibrary()
    val fake = FakeMediaLibraryRepository(listOf(initial))
    room.upsertTracks(listOf(initial))
    val requested = setOf(initial.id, TrackId("external", 404))

    assertFailureEquals(
      fake.setTracksAvailable(requested, false),
      room.setTracksAvailable(requested, false),
    )
    assertTrue(database.trackDao().find("external", 1)!!.isAvailable)
  }

  @Test
  fun playlistTimeValidation_matchesFakeContract() = runBlocking {
    val room = roomPlaylist()
    val fake = FakePlaylistRepository(newId = { "playlist-1" })

    assertFailureEquals(fake.create("Road", -1), room.create("Road", -1))
    val fakeCreated = fake.create("Road", 100) as RepositoryResult.Success
    val roomCreated = room.create("Road", 100) as RepositoryResult.Success
    assertFailureEquals(
      fake.rename(fakeCreated.value.id, "Renamed", 99),
      room.rename(roomCreated.value.id, "Renamed", 99),
    )
  }

  @Test
  fun historyTimeValidation_matchesFakeContract() = runBlocking {
    val room = RoomHistoryRepository(database.playHistoryDao(), scope)
    val fake = FakeHistoryRepository()

    assertFailureEquals(
      fake.recordQualifiedPlay(TrackId("external", 1), -1),
      room.recordQualifiedPlay(TrackId("external", 1), -1),
    )
    assertEquals(null, database.playHistoryDao().find("external", 1))
  }

  private fun roomMediaLibrary(): MediaLibraryRepository =
    RoomMediaLibraryRepository(database.trackDao(), database.hiddenTrackDao(), scope, clock)

  private fun roomPlaylist(): PlaylistRepository =
    RoomPlaylistRepository(database.playlistDao(), database.playlistTrackDao(), scope, clock, newId = { "playlist-1" })

  private fun assertFailureEquals(expected: RepositoryResult<*>, actual: RepositoryResult<*>) {
    assertTrue(expected is RepositoryResult.Failure)
    assertTrue(actual is RepositoryResult.Failure)
    assertEquals((expected as RepositoryResult.Failure).error, (actual as RepositoryResult.Failure).error)
  }

  private fun track(id: Long, isAvailable: Boolean) =
    Track(
      id = TrackId("external", id),
      contentUri = "content://media/external/audio/media/$id",
      title = "Track $id",
      displayName = "$id.mp3",
      extension = "mp3",
      artistId = null,
      artistName = null,
      albumId = null,
      albumName = null,
      durationMs = 60_000,
      dateAddedMs = 1_000,
      modifiedAtMs = 2_000,
      relativePath = "Music/",
      mimeType = "audio/mpeg",
      sizeBytes = 100,
      isAvailable = isAvailable,
    )
}
