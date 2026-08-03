package com.musicapp.player.media.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.media.playback.PlaybackSessionProtocol
import com.musicapp.player.media.playback.PlaybackTrackPayload
import com.musicapp.player.media.playback.QueueMediaIdCodec
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackQueueCoordinatorTest {
    private val player = RecordingQueuePlayer()
    private val coordinator = PlaybackQueueCoordinator(player) { 0 }

    @Test
    fun `single repeat uses one for natural end but manual navigation changes item`() {
        coordinator.replaceQueue(tracks(1, 2, 3), startIndex = 1, playWhenReady = true)

        coordinator.setMode(PlaybackMode.SINGLE_REPEAT)
        coordinator.manualNext()

        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        assertEquals(2, player.currentIndex)
        assertEquals(TrackId("external", 3), coordinator.currentState.queue.currentItem?.trackId)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `shuffle timeline is stable and manual next at its end creates a new round`() {
        coordinator.replaceQueue(tracks(1, 2, 3), startIndex = 0, playWhenReady = true)
        coordinator.setMode(PlaybackMode.SHUFFLE)
        val firstRound = coordinator.currentState.queue.stableShuffleSequence
        val firstRoundNumber = coordinator.currentState.queue.shuffleRound
        coordinator.onMediaItemTransition(player.items.last())

        coordinator.manualNext()

        val nextRound = coordinator.currentState.queue.stableShuffleSequence
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(firstRoundNumber + 1, coordinator.currentState.queue.shuffleRound)
        assertNotEquals(firstRound, nextRound)
        assertEquals(nextRound.first(), coordinator.currentState.queue.currentItemId)
        assertEquals(0, player.currentIndex)
    }

    @Test
    fun `shuffle natural end rebuilds the timeline and continues playing`() {
        coordinator.replaceQueue(tracks(1, 2, 3), startIndex = 0, playWhenReady = true)
        coordinator.setMode(PlaybackMode.SHUFFLE)
        coordinator.onMediaItemTransition(player.items.last())
        val previousRound = coordinator.currentState.queue.shuffleRound

        coordinator.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(previousRound + 1, coordinator.currentState.queue.shuffleRound)
        assertEquals(0, player.currentIndex)
        assertTrue(player.playWhenReady)
        assertTrue(player.prepareCount >= 3)
    }

    @Test
    fun `queue edits update the same player timeline and removing current selects successor`() {
        coordinator.replaceQueue(tracks(1, 2), startIndex = 0, playWhenReady = true)
        coordinator.playNext(tracks(3))
        coordinator.addToQueue(tracks(4))

        assertEquals(listOf(1L, 3L, 2L, 4L), coordinator.currentState.queue.originalQueue.map { it.trackId.mediaStoreId })
        val currentId = requireNotNull(coordinator.currentState.queue.currentItemId)
        coordinator.remove(currentId)

        assertEquals(TrackId("external", 3), coordinator.currentState.queue.currentItem?.trackId)
        assertEquals(0, player.currentPositionMs)
        assertEquals(3, player.items.size)
    }

    @Test
    fun `removing the only item stops and clears the real timeline`() {
        coordinator.replaceQueue(tracks(1), startIndex = 0, playWhenReady = true)

        coordinator.remove(requireNotNull(coordinator.currentState.queue.currentItemId))

        assertTrue(player.stopped)
        assertTrue(player.items.isEmpty())
        assertTrue(coordinator.currentState.queue.originalQueue.isEmpty())
    }

    @Test
    fun `adding to an empty queue selects the first item without autoplay`() {
        coordinator.addToQueue(tracks(5, 6))

        assertEquals(TrackId("external", 5), coordinator.currentState.queue.currentItem?.trackId)
        assertEquals(0, player.currentIndex)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `mode change on an empty queue is published as shared session state`() {
        var publishedMode: PlaybackMode? = null
        coordinator.attachStatePublisher { extras ->
            publishedMode = PlaybackSessionProtocol.decodeState(extras)?.first
        }

        coordinator.setMode(PlaybackMode.SHUFFLE)

        assertEquals(PlaybackMode.SHUFFLE, coordinator.currentState.mode)
        assertEquals(PlaybackMode.SHUFFLE, publishedMode)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    @Test
    fun `duplicate tracks receive distinct media ids that restore both identities`() {
        val repeated = track(9)
        coordinator.replaceQueue(listOf(repeated, repeated), startIndex = 0, playWhenReady = false)

        val decoded = player.items.map { QueueMediaIdCodec.decode(it.mediaId) }

        assertNotEquals(player.items[0].mediaId, player.items[1].mediaId)
        assertEquals(listOf(TrackId("external", 9), TrackId("external", 9)), decoded.map { it?.trackId })
        assertNotEquals(decoded[0]?.id, decoded[1]?.id)
    }

    @Test
    fun `published extras atomically restore mode original queue stable sequence and current`() {
        coordinator.replaceQueue(tracks(1, 2, 3), startIndex = 1, playWhenReady = false)
        coordinator.setMode(PlaybackMode.SHUFFLE)
        val decoded = PlaybackSessionProtocol.decodeState(coordinator.stateExtras())

        assertEquals(coordinator.currentState.mode, decoded?.first)
        assertEquals(coordinator.currentState.queue, decoded?.second)
    }

    @Test
    fun `playback failure is published separately and cleared after recovery`() {
        var publishedFailure: PlaybackFailure? = null
        var queueChangeCount = 0
        coordinator.attachStatePublisher { extras ->
            publishedFailure = PlaybackSessionProtocol.decodePlaybackFailure(extras)
        }
        coordinator.attachQueueStateListener { queueChangeCount += 1 }

        val failure = PlaybackFailure(PlaybackFailureCode.IO_ERROR)
        coordinator.reportPlaybackFailure(failure)

        assertEquals(failure, publishedFailure)
        assertEquals(0, queueChangeCount)

        coordinator.clearPlaybackFailure()

        assertNull(publishedFailure)
        assertEquals(0, queueChangeCount)
    }

    @Test
    fun `forwarding player routes all four standard skip variants through the coordinator`() {
        coordinator.replaceQueue(tracks(1, 2), startIndex = 0, playWhenReady = false)
        val delegate = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                else -> null
            }
        } as Player
        val managed = QueueManagedPlayer(delegate, coordinator)

        managed.seekToNext()
        assertEquals(1, player.currentIndex)
        managed.seekToPreviousMediaItem()
        assertEquals(0, player.currentIndex)
        managed.seekToNextMediaItem()
        assertEquals(1, player.currentIndex)
        managed.seekToPrevious()
        assertEquals(0, player.currentIndex)
    }

    private fun tracks(vararg ids: Long) = ids.map(::track)

    private fun track(id: Long) = PlaybackTrackPayload(
        trackId = TrackId("external", id),
        title = "Track $id",
        artistName = "Artist",
        albumTitle = "Album",
        durationMs = 60_000,
    )
}

private class RecordingQueuePlayer : QueuePlayer {
    var items: List<MediaItem> = emptyList()
    var currentIndex = 0
    override var currentPositionMs: Long = 0
    override var playWhenReady: Boolean = false
        private set
    override var repeatMode: Int = Player.REPEAT_MODE_OFF
    override var shuffleModeEnabled: Boolean = false
    var prepareCount = 0
    var stopped = false

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        items = mediaItems
        currentIndex = startIndex
        currentPositionMs = startPositionMs
    }

    override fun prepare() {
        prepareCount += 1
    }

    override fun play() {
        playWhenReady = true
    }

    override fun pause() {
        playWhenReady = false
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        currentIndex = mediaItemIndex
        currentPositionMs = positionMs
    }

    override fun stop() {
        stopped = true
        playWhenReady = false
    }

    override fun clearMediaItems() {
        items = emptyList()
        currentPositionMs = 0
    }
}
