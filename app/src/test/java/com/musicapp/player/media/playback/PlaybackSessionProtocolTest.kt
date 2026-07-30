package com.musicapp.player.media.playback

import android.os.Bundle
import androidx.media3.common.Player
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.media.service.ApplicationControllerCommands
import com.musicapp.player.media.service.TrustedSystemControllerCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackSessionProtocolTest {
    @Test
    fun `custom commands are strictly namespaced and only application controllers receive them`() {
        PlaybackSessionProtocol.applicationCommands.forEach { command ->
            assertTrue(command.customAction.startsWith("com.musicapp.player.session.v1."))
            assertTrue(ApplicationControllerCommands.sessionCommands.contains(command))
            assertFalse(TrustedSystemControllerCommands.sessionCommands.contains(command))
        }
        assertFalse(ApplicationControllerCommands.playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(ApplicationControllerCommands.playerCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(ApplicationControllerCommands.playerCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }

    @Test
    fun `track command round trip preserves the playable fields and rejects malformed bundles`() {
        val track = track(7)
        val args = PlaybackSessionProtocol.tracksArgs(listOf(track), startIndex = 0, playWhenReady = true)

        assertEquals(track.id, PlaybackSessionProtocol.decodeTracks(args)?.single()?.trackId)
        assertEquals(track.title, PlaybackSessionProtocol.decodeTracks(args)?.single()?.title)
        assertEquals(0, PlaybackSessionProtocol.startIndex(args))
        assertTrue(PlaybackSessionProtocol.playWhenReady(args))
        assertNull(PlaybackSessionProtocol.decodeTracks(Bundle()))
    }

    @Test
    fun `state extras reject inconsistent arrays instead of exposing a partial queue`() {
        val queue = PlaybackQueue(
            originalQueue = listOf(QueueItem(QueueItemId(1), TrackId("external", 7))),
            currentItemId = QueueItemId(1),
        )
        val extras = PlaybackSessionProtocol.stateExtras(PlaybackMode.LIST_REPEAT, queue)

        assertEquals(PlaybackMode.LIST_REPEAT to queue, PlaybackSessionProtocol.decodeState(extras))
        extras.putLongArray("com.musicapp.player.session.v1.track_media_store_ids", longArrayOf())
        assertNull(PlaybackSessionProtocol.decodeState(extras))
    }

    @Test
    fun `queue media id preserves repeated track and unique queue item identities`() {
        val trackId = TrackId("external", 11)
        val first = QueueMediaIdCodec.encode(QueueItemId(1), trackId)
        val second = QueueMediaIdCodec.encode(QueueItemId(2), trackId)

        assertEquals(QueueItem(QueueItemId(1), trackId), QueueMediaIdCodec.decode(first))
        assertEquals(QueueItem(QueueItemId(2), trackId), QueueMediaIdCodec.decode(second))
        assertFalse(first == second)
    }

    private fun track(id: Long) = Track(
        id = TrackId("external", id),
        title = "Track $id",
        artistName = "Artist",
        durationMs = 60_000,
        dateAddedMs = 1,
        dateModifiedMs = 1,
        relativePath = "Music/",
        displayName = "$id.mp3",
    )
}
