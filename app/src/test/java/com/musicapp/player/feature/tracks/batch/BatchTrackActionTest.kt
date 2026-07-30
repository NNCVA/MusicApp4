package com.musicapp.player.feature.tracks.batch

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.PlaylistTrackChangeResult
import com.musicapp.player.fakes.FakeClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTrackActionTest {
    @Test
    fun `add to playlist preserves first selection order and reports added and skipped`() = runTest {
        val ids = ids(1, 2, 3)
        val playlistId = PlaylistId(1)
        val playlistRepository =
            FakePlaylistRepository(
                initialPlaylists = listOf(playlist(playlistId, listOf(ids[1]))),
                existingTrackIds = ids.toSet(),
            )
        val executor = subject(playlistRepository = playlistRepository, tracks = tracks(1, 2, 3))

        val result =
            executor.execute(
                BatchTrackAction.AddToPlaylist(playlistId),
                listOf(ids[2], ids[1], ids[0], ids[2]),
            )

        assertEquals(
            BatchTrackActionResult.Completed(
                action = BatchTrackAction.AddToPlaylist(playlistId),
                selectedCount = 3,
                affectedCount = 2,
                skippedCount = 1,
            ),
            result,
        )
        assertEquals(
            listOf(ids[1], ids[2], ids[0]),
            playlistRepository.observePlaylist(playlistId).first()?.trackIds,
        )
    }

    @Test
    fun `queue actions receive selection order once and empty selection is not dispatched`() = runTest {
        val playback = RecordingPlaybackControllerFacade()
        val executor = subject(playbackController = playback)
        val orderedIds = ids(3, 1, 2)

        val queueResult = executor.execute(BatchTrackAction.AddToQueue, orderedIds)
        val nextResult = executor.execute(BatchTrackAction.PlayNext, orderedIds)
        val emptyResult = executor.execute(BatchTrackAction.AddToQueue, emptyList())

        assertTrue(queueResult is BatchTrackActionResult.Completed)
        assertTrue(nextResult is BatchTrackActionResult.Completed)
        assertEquals(BatchTrackActionResult.EmptySelection, emptyResult)
        assertEquals(listOf(orderedIds), playback.addToQueueRequests)
        assertEquals(listOf(orderedIds), playback.playNextRequests)
    }

    @Test
    fun `playlist failure returns failed without partial mutation`() = runTest {
        val trackIds = ids(1, 2)
        val playlistId = PlaylistId(1)
        val delegate =
            FakePlaylistRepository(
                initialPlaylists = listOf(playlist(playlistId)),
                existingTrackIds = trackIds.toSet(),
            )
        val executor =
            subject(
                playlistRepository = FailingAddPlaylistRepository(delegate),
                tracks = tracks(1, 2),
            )

        val result = executor.execute(BatchTrackAction.AddToPlaylist(playlistId), trackIds)

        assertEquals(
            BatchTrackActionResult.Failed(BatchTrackAction.AddToPlaylist(playlistId), 2),
            result,
        )
        assertTrue(delegate.observePlaylist(playlistId).first()?.trackIds.orEmpty().isEmpty())
    }

    @Test
    fun `hide removes tracks from visible library without deleting media records`() = runTest {
        val tracks = tracks(1, 2)
        val delegate = FakeMediaLibraryRepository(tracks)
        val mediaLibrary = RecordingMediaLibraryRepository(delegate)
        val executor = subject(mediaLibraryRepository = mediaLibrary)

        val result = executor.execute(BatchTrackAction.Hide, tracks.map(Track::id))

        assertTrue(result is BatchTrackActionResult.Completed)
        assertEquals(listOf(tracks.map(Track::id)), mediaLibrary.hiddenRequests)
        assertTrue(delegate.observeTracks().first().isEmpty())
        tracks.forEach { track -> assertNotNull(delegate.getTrack(track.id)) }
    }

    @Test
    fun `hide validation failure leaves every selected track visible`() = runTest {
        val existingTracks = tracks(1, 2)
        val mediaLibrary = FakeMediaLibraryRepository(existingTracks)
        val executor = subject(mediaLibraryRepository = mediaLibrary)

        val result =
            executor.execute(
                BatchTrackAction.Hide,
                listOf(existingTracks.first().id, TrackId("external", 99), existingTracks.last().id),
            )

        assertEquals(BatchTrackActionResult.Failed(BatchTrackAction.Hide, 3), result)
        assertEquals(
            existingTracks.map(Track::id).toSet(),
            mediaLibrary.observeTracks().first().map(Track::id).toSet(),
        )
    }

    private fun subject(
        playlistRepository: PlaylistRepository = FakePlaylistRepository(),
        tracks: List<Track> = emptyList(),
        mediaLibraryRepository: MediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        playbackController: RecordingPlaybackControllerFacade = RecordingPlaybackControllerFacade(),
    ) = DefaultBatchTrackActionExecutor(
        playlistRepository = playlistRepository,
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        clock = FakeClock(123),
    )

    private fun ids(vararg values: Long) = values.map { TrackId("external", it) }

    private fun tracks(vararg values: Long) = values.map { value ->
        Track(
            id = TrackId("external", value),
            title = "Track $value",
            artistName = "Artist",
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music/",
            displayName = "Track $value.mp3",
        )
    }

    private fun playlist(id: PlaylistId, trackIds: List<TrackId> = emptyList()) =
        Playlist(
            id = id,
            displayName = "Playlist",
            normalizedName = "playlist",
            trackIds = trackIds,
            createdAtMs = 1,
        )
}

private class RecordingMediaLibraryRepository(
    private val delegate: MediaLibraryRepository,
) : MediaLibraryRepository by delegate {
    val hiddenRequests = mutableListOf<List<TrackId>>()

    override suspend fun setHidden(
        trackIds: List<TrackId>,
        hidden: Boolean,
        changedAtMs: Long,
    ) {
        hiddenRequests += trackIds
        delegate.setHidden(trackIds, hidden, changedAtMs)
    }
}

private class FailingAddPlaylistRepository(
    delegate: PlaylistRepository,
) : PlaylistRepository by delegate {
    override suspend fun addTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ): PlaylistTrackChangeResult = error("transaction failed")
}

private class RecordingPlaybackControllerFacade : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    val addToQueueRequests = mutableListOf<List<TrackId>>()
    val playNextRequests = mutableListOf<List<TrackId>>()

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun play(context: PlaybackContext) = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun skipToPrevious() = Unit

    override fun skipToNext() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun addToQueue(trackIds: List<TrackId>) {
        addToQueueRequests += trackIds
    }

    override fun playNext(trackIds: List<TrackId>) {
        playNextRequests += trackIds
    }
}
