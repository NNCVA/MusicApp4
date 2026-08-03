package com.musicapp.player.data.sync

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.createInMemoryDatabase
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.repository.RoomHistoryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.RoomPlaybackSnapshotRepository
import com.musicapp.player.data.repository.RoomPlaylistRepository
import com.musicapp.player.fakes.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaLibrarySyncTest {
    private lateinit var database: MusicDatabase
    private lateinit var clock: FakeClock
    private lateinit var coordinator: MediaLibrarySyncCoordinator
    private lateinit var mediaRepository: RoomMediaLibraryRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        clock = FakeClock(timeMillis = 1_000)
        coordinator = MediaLibrarySyncCoordinator(database, clock)
        mediaRepository = RoomMediaLibraryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun firstSyncPersistsCrossVolumeIdentityGenerationAndSignatures() = runTest {
        val report = coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(
                mounted = setOf(PRIMARY, CARD),
                candidates = listOf(candidate(PRIMARY, 42), candidate(CARD, 42)),
                signatures = mapOf(PRIMARY to "v-primary", CARD to "v-card"),
            ),
        )

        assertTrue(report.succeeded)
        assertEquals(1L, report.generation)
        assertEquals(2, report.upsertedTrackCount)
        assertEquals(2, mediaRepository.observeTracks(includeHidden = true).first().size)
        val states = database.mediaSyncStateDao().getVolumeStates().associateBy { it.volumeName }
        assertEquals(1L, states.getValue(PRIMARY).lastCompleteGeneration)
        assertEquals("v-card", states.getValue(CARD).mediaStoreVersion)
    }

    @Test
    fun incrementalSyncUpsertsBatchWithoutDeletingUnobservedTracks() = runTest {
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1), candidate(PRIMARY, 2))),
        )

        val report = coordinator.commit(
            MediaLibrarySyncMode.INCREMENTAL,
            scan(
                setOf(PRIMARY),
                listOf(candidate(PRIMARY, 1, title = "Updated"), candidate(PRIMARY, 3)),
            ),
        )

        assertEquals(2L, report.generation)
        assertEquals(0, report.removedTrackCount)
        assertEquals("Updated", mediaRepository.getTrack(TrackId(PRIMARY, 1))?.title)
        assertNotNull(mediaRepository.getTrack(TrackId(PRIMARY, 2)))
        assertNotNull(mediaRepository.getTrack(TrackId(PRIMARY, 3)))
    }

    @Test
    fun blankMetadataFallsBackToStableNonBlankDomainValues() = runTest {
        val candidate = candidate(PRIMARY, 88).copy(
            title = " ",
            artistName = " ",
            displayName = " ",
        )

        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate)),
        )

        val track = mediaRepository.getTrack(TrackId(PRIMARY, 88))
        assertEquals("88", track?.title)
        assertEquals("88", track?.displayName)
        assertEquals("<unknown>", track?.artistName)
    }

    @Test
    fun queryFailureRetainsCacheAndDoesNotAdvanceGeneration() = runTest {
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1))),
        )

        val report = coordinator.synchronize(MediaLibrarySyncMode.FULL) {
            throw IllegalStateException("query failed")
        }

        assertEquals(MediaLibrarySyncFailure.QUERY_FAILED, report.failure)
        assertEquals(1L, report.generation)
        assertEquals(1L, database.mediaSyncStateDao().getGenerationOrNull())
        assertEquals(
            Availability.TEMPORARILY_UNAVAILABLE,
            mediaRepository.getTrack(TrackId(PRIMARY, 1))?.availability,
        )
    }

    @Test
    fun unmountedVolumeIsRetainedWhileMountedVolumeCompletes() = runTest {
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY, CARD), listOf(candidate(PRIMARY, 1), candidate(CARD, 1))),
        )

        val report = coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1, title = "Primary updated"))),
        )

        assertEquals(setOf(CARD), report.temporarilyUnavailableVolumeNames)
        assertEquals(
            Availability.TEMPORARILY_UNAVAILABLE,
            mediaRepository.getTrack(TrackId(CARD, 1))?.availability,
        )
        assertEquals("Primary updated", mediaRepository.getTrack(TrackId(PRIMARY, 1))?.title)
    }

    @Test
    fun permissionLossRetainsAssociationsAndDoesNotAdvanceGeneration() = runTest {
        val trackId = TrackId(PRIMARY, 1)
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1))),
        )
        val playlists = RoomPlaylistRepository(database)
        val playlistId = playlists.createPlaylist("Kept", "kept", 1)
        playlists.addTracks(playlistId, listOf(trackId), 2)

        val report = coordinator.synchronize(MediaLibrarySyncMode.FULL) {
            throw IllegalStateException("wrapped query failure", SecurityException("permission revoked"))
        }

        assertEquals(MediaLibrarySyncFailure.PERMISSION_LOST, report.failure)
        assertEquals(1L, database.mediaSyncStateDao().getGenerationOrNull())
        assertEquals(listOf(trackId), playlists.observePlaylist(playlistId).first()?.trackIds)
        assertNotNull(mediaRepository.getTrack(trackId))
    }

    @Test
    fun mountedEmptyVolumeFullSyncCascadesRelationsAndPrunesSnapshot() = runTest {
        val removedId = TrackId(PRIMARY, 7)
        val keptId = TrackId(CARD, 7)
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY, CARD), listOf(candidate(PRIMARY, 7), candidate(CARD, 7))),
        )
        val playlists = RoomPlaylistRepository(database)
        val histories = RoomHistoryRepository(database)
        val snapshots = RoomPlaybackSnapshotRepository(database)
        val playlistId = playlists.createPlaylist("Cleanup", "cleanup", 1)
        playlists.addTracks(playlistId, listOf(removedId), 2)
        histories.recordPlayback(removedId, 3)
        mediaRepository.setHidden(removedId, hidden = true, changedAtMs = 4)
        snapshots.saveSnapshot(
            PlaybackSnapshot(
                queue = PlaybackQueue(
                    originalQueue = listOf(
                        QueueItem(QueueItemId(1), removedId),
                        QueueItem(QueueItemId(2), keptId),
                    ),
                    currentItemId = QueueItemId(1),
                ),
                positionMs = 500,
                playbackMode = PlaybackMode.LIST_REPEAT,
            ),
        )

        val report = coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY, CARD), listOf(candidate(CARD, 7))),
        )

        assertEquals(1, report.removedTrackCount)
        assertNull(mediaRepository.getTrack(removedId))
        assertEquals(emptyList<TrackId>(), playlists.observePlaylist(playlistId).first()?.trackIds)
        assertNull(database.playHistoryDao().get(PRIMARY, 7))
        assertFalse(database.hiddenTrackDao().exists(PRIMARY, 7))
        val snapshot = snapshots.getSnapshot()
        assertEquals(listOf(keptId), snapshot?.queue?.originalQueue?.map { it.trackId })
        assertEquals(keptId, snapshot?.queue?.currentItem?.trackId)
        assertEquals(0L, snapshot?.positionMs)
    }

    @Test
    fun rediscoveryRestoresTemporarilyUnavailableTrack() = runTest {
        val cardId = TrackId(CARD, 9)
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(CARD), listOf(candidate(CARD, 9))),
        )
        coordinator.commit(MediaLibrarySyncMode.FULL, scan(emptySet(), emptyList()))
        assertEquals(
            Availability.TEMPORARILY_UNAVAILABLE,
            mediaRepository.getTrack(cardId)?.availability,
        )

        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(CARD), listOf(candidate(CARD, 9, title = "Rediscovered"))),
        )

        assertEquals(Availability.AVAILABLE, mediaRepository.getTrack(cardId)?.availability)
        assertEquals("Rediscovered", mediaRepository.getTrack(cardId)?.title)
    }

    @Test
    fun transactionFailureRollsBackGenerationUpsertAndDeletion() = runTest {
        val keptId = TrackId(PRIMARY, 1)
        val wouldBeRemovedId = TrackId(PRIMARY, 2)
        coordinator.commit(
            MediaLibrarySyncMode.FULL,
            scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1), candidate(PRIMARY, 2))),
        )
        database.playbackSnapshotDao().upsert(
            PlaybackSnapshotEntity(
                originalQueueJson = "not-json",
                stableShuffleSequenceJson = "[]",
                currentQueueItemId = null,
                shuffleRound = 0,
                shuffleCursor = null,
                positionMs = 0,
                playbackMode = PlaybackMode.LIST_REPEAT.name,
                playbackInstanceJson = null,
                updatedAtMs = 0,
                playbackResumptionAllowed = true,
            ),
        )

        val failure = runCatching {
            coordinator.commit(
                MediaLibrarySyncMode.FULL,
                scan(setOf(PRIMARY), listOf(candidate(PRIMARY, 1, title = "Should roll back"))),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1L, database.mediaSyncStateDao().getGenerationOrNull())
        assertEquals("Track 1", mediaRepository.getTrack(keptId)?.title)
        assertNotNull(mediaRepository.getTrack(wouldBeRemovedId))
    }

    private fun scan(
        mounted: Set<String>,
        candidates: List<MediaAudioCandidate>,
        signatures: Map<String, String?> = emptyMap(),
    ) = CompleteMediaLibraryScan(mounted, candidates, signatures)

    private fun candidate(
        volumeName: String,
        mediaStoreId: Long,
        title: String = "Track $mediaStoreId",
    ) = MediaAudioCandidate(
        volumeName = volumeName,
        mediaStoreId = mediaStoreId,
        title = title,
        artistName = "Artist",
        artistId = 10,
        albumTitle = "Album",
        albumId = 20,
        displayName = "$title.mp3",
        mimeType = "audio/mpeg",
        durationMs = 180_000,
        dateAddedMs = 1_000,
        dateModifiedMs = 2_000,
        relativeDirectory = "Music/",
        sizeBytes = 4_096,
    )

    private companion object {
        const val PRIMARY = "external_primary"
        const val CARD = "card"
    }
}
