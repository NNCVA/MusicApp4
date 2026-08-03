package com.musicapp.player.media.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceShutdownCoordinatorTest {
    @Test
    fun `full exit persists then clears runtime queue then stops service`() = runTest {
        val calls = mutableListOf<String>()

        PlaybackServiceShutdownCoordinator().shutdown(
            persistFinalSnapshot = { calls += "persist" },
            clearRuntimeQueue = { calls += "clear" },
            stopPlaybackService = { calls += "stop" },
        )

        assertEquals(listOf("persist", "clear", "stop"), calls)
    }
}
