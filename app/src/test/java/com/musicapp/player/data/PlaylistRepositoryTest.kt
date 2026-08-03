package com.musicapp.player.data

import android.database.sqlite.SQLiteConstraintException
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.RoomPlaylistRepository
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
class PlaylistRepositoryTest {
    private lateinit var database: com.musicapp.player.data.local.MusicDatabase
    private lateinit var mediaRepository: RoomMediaLibraryRepository
    private lateinit var repository: RoomPlaylistRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        mediaRepository = RoomMediaLibraryRepository(database)
        repository = RoomPlaylistRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun normalizedNameTrackAndPositionUniquenessAreEnforced() = runTest {
        val tracks = listOf(track(mediaStoreId = 1), track(mediaStoreId = 2), track(mediaStoreId = 3))
        mediaRepository.mergeTracks(tracks)
        val playlistId = repository.createPlaylist("Road", "road", 1)
        val added = repository.addTracks(playlistId, listOf(tracks[1].id, tracks[0].id, tracks[1].id), 2)

        assertEquals(2, added.changedCount)
        assertEquals(1, added.skippedCount)
        assertEquals(listOf(tracks[1].id, tracks[0].id), repository.observePlaylist(playlistId).first()?.trackIds)
        val duplicatePositionFailure = runCatching {
            database.playlistTrackDao().insert(
                listOf(
                    PlaylistTrackEntity(
                        playlistId = playlistId.value,
                        trackVolumeName = tracks[2].id.volumeName,
                        trackMediaStoreId = tracks[2].id.mediaStoreId,
                        position = 1,
                    ),
                ),
            )
        }.exceptionOrNull()
        assertTrue(duplicatePositionFailure is SQLiteConstraintException)
        val duplicateNameFailure = runCatching {
            repository.createPlaylist("ROAD", "ROAD", 3)
        }.exceptionOrNull()
        assertTrue(duplicateNameFailure is SQLiteConstraintException)

        val removed = repository.removeTracks(
            playlistId,
            listOf(tracks[0].id, TrackId("external_primary", 999)),
            4,
        )
        assertEquals(1, removed.changedCount)
        assertEquals(1, removed.skippedCount)
        assertEquals(listOf(tracks[1].id), repository.observePlaylist(playlistId).first()?.trackIds)
    }

    @Test
    fun bulkInsertIsAtomicAndEmptyReplacementPreservesOldTracks() = runTest {
        val existing = track(mediaStoreId = 1)
        val validAddition = track(mediaStoreId = 2)
        mediaRepository.mergeTracks(listOf(existing, validAddition))
        val playlistId = repository.createPlaylist("List", "list", 1)
        repository.addTracks(playlistId, listOf(existing.id), 2)

        val failure = runCatching {
            repository.addTracks(
                playlistId,
                listOf(validAddition.id, TrackId("external_primary", 999)),
                3,
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf(existing.id), repository.observePlaylist(playlistId).first()?.trackIds)

        repository.replaceTracks(playlistId, emptyList(), 4)
        assertEquals(listOf(existing.id), repository.observePlaylist(playlistId).first()?.trackIds)
    }

    @Test
    fun failedBatchRemovalRollsBackDeletedRelations() = runTest {
        val existing = track(mediaStoreId = 1)
        mediaRepository.mergeTracks(listOf(existing))
        val playlistId = repository.createPlaylist("Atomic", "atomic", 10)
        repository.addTracks(playlistId, listOf(existing.id), 11)

        val failure = runCatching {
            repository.removeTracks(playlistId, listOf(existing.id), updatedAtMs = 1)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf(existing.id), repository.observePlaylist(playlistId).first()?.trackIds)
    }

    @Test
    fun roomAndFakeShareNameReferenceAndCreationOrderContracts() = runTest {
        val existing = track(mediaStoreId = 1)
        mediaRepository.mergeTracks(listOf(existing))
        val repositories: List<PlaylistRepository> = listOf(
            repository,
            FakePlaylistRepository(existingTrackIds = setOf(existing.id)),
        )
        val missing = TrackId("external_primary", 999)

        repositories.forEach { subject ->
            assertIllegalArgument { subject.createPlaylist(" ", "blank", 1) }
            assertIllegalArgument { subject.createPlaylist(" untrimmed", "untrimmed", 1) }
            assertIllegalArgument { subject.createPlaylist("Valid", " ", 1) }

            val first = subject.createPlaylist("First", "first", 10)
            val second = subject.createPlaylist("Second", "second", 10)
            val newest = subject.createPlaylist("Newest", "newest", 20)
            assertEquals(listOf(newest, second, first), subject.observePlaylists().first().map { it.id })

            assertIllegalArgument { subject.renamePlaylist(first, " ", "renamed", 21) }
            assertIllegalArgument { subject.renamePlaylist(first, " Renamed", "renamed", 21) }
            assertIllegalArgument { subject.renamePlaylist(first, "Renamed", " ", 21) }
            assertIllegalArgument { subject.addTracks(first, listOf(missing), 21) }

            subject.addTracks(first, listOf(existing.id), 21)
            assertIllegalArgument { subject.replaceTracks(first, listOf(missing), 22) }
            assertEquals(listOf(existing.id), subject.observePlaylist(first).first()?.trackIds)
        }
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }
}
