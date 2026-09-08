package com.musicapp.player.feature.player

import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniArtworkMotionTest {
    private val firstTrack = TrackId(volumeName = "external_primary", mediaStoreId = 1L)
    private val secondTrack = TrackId(volumeName = "external_primary", mediaStoreId = 2L)

    @Test
    fun `initial composition never animates`() {
        assertFalse(
            MiniArtworkMotion.shouldAnimate(
                previousTrackId = null,
                targetTrackId = firstTrack,
                artworkReady = true,
                isVisible = true,
                animationsEnabled = true,
            ),
        )
    }

    @Test
    fun `ready visible track change animates`() {
        assertTrue(
            MiniArtworkMotion.shouldAnimate(
                previousTrackId = firstTrack,
                targetTrackId = secondTrack,
                artworkReady = true,
                isVisible = true,
                animationsEnabled = true,
            ),
        )
    }

    @Test
    fun `unready hidden or reduced motion track changes do not animate`() {
        listOf(
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false),
        ).forEach { (artworkReady, isVisible, animationsEnabled) ->
            assertFalse(
                MiniArtworkMotion.shouldAnimate(
                    previousTrackId = firstTrack,
                    targetTrackId = secondTrack,
                    artworkReady = artworkReady,
                    isVisible = isVisible,
                    animationsEnabled = animationsEnabled,
                ),
            )
        }
    }

    @Test
    fun `incoming and outgoing curves stay complementary and blur clamps`() {
        assertEquals(0f, MiniArtworkMotion.incomingAlpha(-1f), 0.001f)
        assertEquals(1f, MiniArtworkMotion.incomingAlpha(2f), 0.001f)
        assertEquals(1f, MiniArtworkMotion.outgoingAlpha(0f), 0.001f)
        assertEquals(0f, MiniArtworkMotion.outgoingAlpha(1f), 0.001f)
        assertEquals(MiniArtworkMotion.MAX_BLUR_DP, MiniArtworkMotion.blurRadiusDp(0f), 0.001f)
        assertEquals(0f, MiniArtworkMotion.blurRadiusDp(1f), 0.001f)
    }

    @Test
    fun `motion duration and maximum blur match the mini player contract`() {
        assertEquals(500, MiniArtworkMotion.DURATION_MS)
        assertEquals(20f, MiniArtworkMotion.MAX_BLUR_DP, 0.001f)
    }
}
