package com.musicapp.player.data

import com.musicapp.player.data.repository.RoomHistoryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.FakeHistoryRepository
import com.musicapp.player.data.repository.HistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryRepositoryTest {
    private lateinit var database: com.musicapp.player.data.local.MusicDatabase
    private lateinit var mediaRepository: RoomMediaLibraryRepository
    private lateinit var repository: RoomHistoryRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        mediaRepository = RoomMediaLibraryRepository(database)
        repository = RoomHistoryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eachTrackHasOneHistoryRowWhoseCountAndRecencyAreUpdated() = runTest {
        val first = track(mediaStoreId = 1)
        val second = track(mediaStoreId = 2)
        mediaRepository.mergeTracks(listOf(first, second))

        repository.recordPlayback(first.id, 10)
        repository.recordPlayback(second.id, 20)
        repository.recordPlayback(first.id, 30)
        mediaRepository.mergeTracks(listOf(first.copy(title = "Updated metadata")))

        val history = repository.observeHistory().first()
        assertEquals(listOf(first.id, second.id), history.map { it.trackId })
        assertEquals(2, history.first().playCount)
        assertEquals(30, history.first().lastPlayedAtMs)
    }

    @Test
    fun deletingMissingTrackCascadesItsHistory() = runTest {
        val retained = track(mediaStoreId = 1)
        val removed = track(mediaStoreId = 2)
        mediaRepository.mergeTracks(listOf(retained, removed))
        repository.recordPlayback(removed.id, 10)

        mediaRepository.replaceTracksForVolume(retained.id.volumeName, listOf(retained))

        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun roomAndFakeRejectHistoryForMissingTracks() = runTest {
        val existing = track(mediaStoreId = 1)
        val missing = track(mediaStoreId = 999).id
        mediaRepository.mergeTracks(listOf(existing))
        val repositories: List<HistoryRepository> = listOf(
            repository,
            FakeHistoryRepository(existingTrackIds = setOf(existing.id)),
        )

        repositories.forEach { subject ->
            val failure = runCatching { subject.recordPlayback(missing, 10) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(subject.observeHistory().first().isEmpty())
        }
    }
}
