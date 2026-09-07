package com.musicapp.player.media.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Media3PlaybackControllerConnectionPendingGuardTest {

    @Test
    fun replaceQueueImmediatelyPublishesPendingTrackAndPreservesPlayingIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = Media3PlaybackControllerConnection(context)

        val track1 = testTrack(1)
        val track2 = testTrack(2)

        connection.replaceQueue(
            tracks = listOf(track1, track2),
            startIndex = 1,
            playWhenReady = true,
        )

        val state = connection.state.value
        assertEquals(track2.id, state.currentTrackId)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackStatus.PREPARING, state.playbackStatus)
    }

    @Test
    fun replaceQueueWithPlayWhenReadyFalseKeepsIsPlayingFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = Media3PlaybackControllerConnection(context)

        val track1 = testTrack(1)

        connection.replaceQueue(
            tracks = listOf(track1),
            startIndex = 0,
            playWhenReady = false,
        )

        val state = connection.state.value
        assertEquals(track1.id, state.currentTrackId)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackStatus.PREPARING, state.playbackStatus)
    }

    @Test
    fun pauseWhilePendingImmediatelyReflectsPauseState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = Media3PlaybackControllerConnection(context)

        val track1 = testTrack(1)

        connection.replaceQueue(
            tracks = listOf(track1),
            startIndex = 0,
            playWhenReady = true,
        )
        assertTrue(connection.state.value.isPlaying)

        connection.pause()
        assertFalse(connection.state.value.isPlaying)
    }

    @Test
    fun playCommandImmediatelyPublishesPreparingWithPlayingTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connection = Media3PlaybackControllerConnection(context)

        connection.play()
        val state = connection.state.value
        assertTrue(state.isPlaying)
        assertEquals(PlaybackStatus.PREPARING, state.playbackStatus)
    }


    private fun testTrack(id: Long) = Track(
        id = TrackId("external_primary", id),
        title = "Track $id",
        artistName = "Artist",
        durationMs = 120_000,
        dateAddedMs = 1,
        dateModifiedMs = 1,
        relativePath = "Music/",
        displayName = "track-$id.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 2_048,
    )
}
