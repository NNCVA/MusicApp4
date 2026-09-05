package com.musicapp.player.data

import com.musicapp.player.data.repository.RoomHistoryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.FakeHistoryRepository
import com.musicapp.player.data.repository.HistoryRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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

    @Test
    fun deleteHistoryRemovesSpecifiedRecordsInRoomAndFake() = runTest {
        val first = track(mediaStoreId = 1)
        val second = track(mediaStoreId = 2)
        val third = track(mediaStoreId = 3)
        mediaRepository.mergeTracks(listOf(first, second, third))

        val room = repository
        val fake = FakeHistoryRepository(existingTrackIds = setOf(first.id, second.id, third.id))

        listOf(room, fake).forEach { subject ->
            subject.recordPlayback(first.id, 10)
            subject.recordPlayback(second.id, 20)
            subject.recordPlayback(third.id, 30)

            subject.deleteHistory(setOf(second.id))
            val remaining = subject.observeHistory().first()
            assertEquals(listOf(third.id, first.id), remaining.map { it.trackId })

            subject.deleteHistory(setOf(first.id, third.id))
            assertTrue(subject.observeHistory().first().isEmpty())
        }
    }
}
