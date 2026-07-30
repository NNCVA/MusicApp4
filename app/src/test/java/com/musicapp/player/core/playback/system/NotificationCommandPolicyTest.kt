package com.musicapp.player.core.playback.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCommandPolicyTest {
    @Test
    fun `system media panel exposes exactly previous play pause and next`() {
        assertEquals(
            listOf(
                SystemPlaybackCommand.PREVIOUS,
                SystemPlaybackCommand.PLAY_PAUSE,
                SystemPlaybackCommand.NEXT,
            ),
            NotificationCommandPolicy.visibleCommands(SystemPlaybackCommand.entries.toSet()),
        )
    }

    @Test
    fun `primary controls keep stable order when some commands are unavailable`() {
        assertEquals(
            listOf(SystemPlaybackCommand.PREVIOUS, SystemPlaybackCommand.NEXT),
            NotificationCommandPolicy.visibleCommands(
                setOf(
                    SystemPlaybackCommand.NEXT,
                    SystemPlaybackCommand.SEEK,
                    SystemPlaybackCommand.PREVIOUS,
                ),
            ),
        )
    }

    @Test
    fun `queue editing seek stop and custom commands are never visible`() {
        assertTrue(NotificationCommandPolicy.isVisible(SystemPlaybackCommand.PLAY_PAUSE))
        assertFalse(NotificationCommandPolicy.isVisible(SystemPlaybackCommand.SEEK))
        assertFalse(NotificationCommandPolicy.isVisible(SystemPlaybackCommand.STOP))
        assertFalse(NotificationCommandPolicy.isVisible(SystemPlaybackCommand.EDIT_QUEUE))
        assertFalse(NotificationCommandPolicy.isVisible(SystemPlaybackCommand.CUSTOM))
    }
}
