package com.musicapp.player.feature.settings

import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakeHistoryRepository
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.MediaLibrarySyncFailure
import com.musicapp.player.data.sync.MediaLibrarySyncFeedback
import com.musicapp.player.data.sync.MediaLibrarySyncTrigger
import com.musicapp.player.data.sync.SyncReport
import com.musicapp.player.feature.settings.data.DataManagementAction
import com.musicapp.player.feature.settings.data.DataManagementUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataManagementUseCaseTest {
    @Test
    fun `clear history changes only history`() = runTest {
        val fixture = fixture()

        fixture.useCase.execute(DataManagementAction.CLEAR_HISTORY)

        assertTrue(fixture.history.observeHistory().first().isEmpty())
        assertEquals(1, fixture.playlists.observePlaylists().first().size)
        assertNotNull(fixture.media.getTrack(fixture.track.id))
        assertEquals(0, fixture.sync.manualSyncRequests)
    }

    @Test
    fun `delete all playlists keeps history cache and physical track reference`() = runTest {
        val fixture = fixture()

        fixture.useCase.execute(DataManagementAction.DELETE_ALL_PLAYLISTS)

        assertTrue(fixture.playlists.observePlaylists().first().isEmpty())
        assertEquals(1, fixture.history.observeHistory().first().size)
        assertNotNull(fixture.media.getTrack(fixture.track.id))
        assertEquals(0, fixture.sync.manualSyncRequests)
    }

    @Test
    fun `rebuild waits for successful full sync and preserves other scopes`() = runTest {
        val fixture = fixture()
        fixture.sync.awaitedResult = completedSync()

        fixture.useCase.execute(DataManagementAction.REBUILD_LIBRARY_CACHE)

        assertEquals(1, fixture.sync.manualSyncRequests)
        assertEquals(1, fixture.playlists.observePlaylists().first().size)
        assertEquals(1, fixture.history.observeHistory().first().size)
        assertNotNull(fixture.media.getTrack(fixture.track.id))
    }

    @Test
    fun `failed rebuild does not report success or clear cached domains`() = runTest {
        val fixture = fixture()
        fixture.sync.awaitedResult = LibrarySyncEvent.Failed(
            trigger = MediaLibrarySyncTrigger.MANUAL,
            feedback = MediaLibrarySyncFeedback.RESULT_DIALOG,
            failure = MediaLibrarySyncFailure.QUERY_FAILED,
        )

        val failure = runCatching {
            fixture.useCase.execute(DataManagementAction.REBUILD_LIBRARY_CACHE)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, fixture.playlists.observePlaylists().first().size)
        assertEquals(1, fixture.history.observeHistory().first().size)
        assertNotNull(fixture.media.getTrack(fixture.track.id))
    }

    @Test
    fun `rebuild ignores an old manual event and awaits its own request result`() = runTest {
        val fixture = fixture()
        val rebuild = async {
            fixture.useCase.execute(DataManagementAction.REBUILD_LIBRARY_CACHE)
        }
        runCurrent()
        assertTrue(rebuild.isActive)

        fixture.sync.emit(completedSync())
        runCurrent()
        assertTrue(rebuild.isActive)

        fixture.sync.completeAwaited(completedSync())
        rebuild.await()
        assertEquals(1, fixture.sync.manualSyncRequests)
    }

    private fun fixture(): Fixture {
        val track = track(1)
        val history = FakeHistoryRepository(
            initialHistory = listOf(PlayHistory(track.id, lastPlayedAtMs = 10, playCount = 2)),
            existingTrackIds = setOf(track.id),
        )
        val playlists = FakePlaylistRepository(
            initialPlaylists = listOf(
                Playlist(
                    id = PlaylistId(1),
                    displayName = "Keep",
                    normalizedName = "keep",
                    trackIds = listOf(track.id),
                    createdAtMs = 1,
                ),
            ),
            existingTrackIds = setOf(track.id),
        )
        val media = FakeMediaLibraryRepository(listOf(track))
        val sync = RecordingSettingsSyncController()
        return Fixture(
            useCase = DataManagementUseCase(history, playlists, sync),
            history = history,
            playlists = playlists,
            media = media,
            sync = sync,
            track = track,
        )
    }

    private fun completedSync() = LibrarySyncEvent.Completed(
        trigger = MediaLibrarySyncTrigger.MANUAL,
        feedback = MediaLibrarySyncFeedback.RESULT_DIALOG,
        result = SyncReport(1, 1, 0, emptySet()),
    )

    private fun track(value: Long) = Track(
        id = TrackId("external", value),
        title = "Track $value",
        artistName = "Artist",
        durationMs = 1_000,
        dateAddedMs = value,
        dateModifiedMs = value,
        relativePath = "Music",
        displayName = "$value.mp3",
    )

    private data class Fixture(
        val useCase: DataManagementUseCase,
        val history: FakeHistoryRepository,
        val playlists: FakePlaylistRepository,
        val media: FakeMediaLibraryRepository,
        val sync: RecordingSettingsSyncController,
        val track: Track,
    )
}
