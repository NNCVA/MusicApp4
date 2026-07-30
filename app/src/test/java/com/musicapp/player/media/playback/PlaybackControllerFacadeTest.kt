package com.musicapp.player.media.playback

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerFacadeTest {
    @Test
    fun forwardsBasicPlaybackCommandsAndClampsSeekToZero() {
        val connection = RecordingConnection()
        val facade = facade(connection = connection)

        facade.connect()
        facade.play()
        facade.pause()
        facade.skipToPrevious()
        facade.skipToNext()
        facade.seekTo(-250)
        facade.disconnect()

        assertEquals(
            listOf("connect", "play", "pause", "previous", "next", "seek:0", "disconnect"),
            connection.commands,
        )
    }

    @Test
    fun exposesControllerConnectionStateWithoutMedia3Types() {
        val expected = PlaybackControllerState(connectionState = PlaybackConnectionState.CONNECTED)
        val connection = RecordingConnection(initialState = expected)

        assertEquals(expected, facade(connection = connection).state.value)
    }

    @Test
    fun overlappingStartedClientsReleaseOnlyAfterTheLastClientStops() {
        val connection = RecordingConnection()
        val facade = facade(connection = connection)

        facade.connect()
        facade.connect()
        facade.disconnect()
        assertEquals(listOf("connect"), connection.commands)

        facade.disconnect()
        facade.disconnect()
        assertEquals(listOf("connect", "disconnect"), connection.commands)
    }

    @Test
    fun resolvesContextInItsOriginalOrderAndStartsTheSelectedTrack() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val first = track(1)
        val second = track(2)
        val third = track(3)
        val connection = RecordingConnection()
        val facade = facade(connection, scope, listOf(first, second, third))

        facade.play(
            PlaybackContext(
                source = PlaybackContextSource.TRACKS,
                orderedTrackIds = listOf(third.id, first.id, second.id),
                selectedTrackId = first.id,
            ),
        )
        scope.advanceUntilIdle()

        assertEquals(listOf(third.id, first.id, second.id), connection.replacedTracks?.map(Track::id))
        assertEquals(1, connection.startIndex)
        assertEquals(true, connection.playWhenReady)
    }

    @Test
    fun doesNotReplaceQueueWhenSelectedTrackDisappearedFromRoom() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val available = track(1)
        val missing = TrackId("external_primary", 2)
        val connection = RecordingConnection()
        val facade = facade(connection, scope, listOf(available))

        facade.play(
            PlaybackContext(
                source = PlaybackContextSource.TRACKS,
                orderedTrackIds = listOf(available.id, missing),
                selectedTrackId = missing,
            ),
        )
        scope.advanceUntilIdle()

        assertNull(connection.replacedTracks)
    }

    @Test
    fun forwardsModeAndQueueEditsThroughThePlatformFreeFacade() {
        val scope = TestScope(StandardTestDispatcher())
        val first = track(1)
        val second = track(2)
        val connection = RecordingConnection()
        val facade = facade(connection, scope, listOf(first, second))

        facade.setPlaybackMode(PlaybackMode.SHUFFLE)
        facade.addToQueue(listOf(second.id, first.id))
        scope.advanceUntilIdle()
        facade.playNext(listOf(first.id))
        scope.advanceUntilIdle()
        facade.removeFromQueue(QueueItemId(9))

        assertEquals(PlaybackMode.SHUFFLE, connection.mode)
        assertEquals(listOf(second.id, first.id), connection.addedTracks.map(Track::id))
        assertEquals(listOf(first.id), connection.nextTracks.map(Track::id))
        assertEquals(QueueItemId(9), connection.removedId)
    }

    private fun facade(
        connection: RecordingConnection,
        scope: TestScope = TestScope(StandardTestDispatcher()),
        tracks: List<Track> = emptyList(),
    ) = DefaultPlaybackControllerFacade(
        mediaLibraryRepository = FakeMediaLibraryRepository(initialTracks = tracks),
        connection = connection,
        applicationScope = scope,
    )

    private fun track(id: Long) =
        Track(
            id = TrackId("external_primary", id),
            title = "Track $id",
            artistName = "Artist",
            durationMs = 60_000,
            dateAddedMs = 1,
            dateModifiedMs = 1,
            relativePath = "Music/",
            displayName = "track-$id.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 1_024,
        )

    private class RecordingConnection(
        initialState: PlaybackControllerState = PlaybackControllerState(),
    ) : PlaybackControllerConnection {
        override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(initialState)
        val commands = mutableListOf<String>()
        var replacedTracks: List<Track>? = null
        var startIndex: Int? = null
        var playWhenReady: Boolean? = null
        var mode: PlaybackMode? = null
        var addedTracks: List<Track> = emptyList()
        var nextTracks: List<Track> = emptyList()
        var removedId: QueueItemId? = null

        override fun connect() {
            commands += "connect"
        }

        override fun disconnect() {
            commands += "disconnect"
        }

        override fun replaceQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            replacedTracks = tracks
            this.startIndex = startIndex
            this.playWhenReady = playWhenReady
        }

        override fun play() {
            commands += "play"
        }

        override fun pause() {
            commands += "pause"
        }

        override fun skipToPrevious() {
            commands += "previous"
        }

        override fun skipToNext() {
            commands += "next"
        }

        override fun seekTo(positionMs: Long) {
            commands += "seek:$positionMs"
        }

        override fun setPlaybackMode(mode: PlaybackMode) {
            this.mode = mode
        }

        override fun addToQueue(tracks: List<Track>) {
            addedTracks = tracks
        }

        override fun playNext(tracks: List<Track>) {
            nextTracks = tracks
        }

        override fun removeFromQueue(queueItemId: QueueItemId) {
            removedId = queueItemId
        }
    }
}
