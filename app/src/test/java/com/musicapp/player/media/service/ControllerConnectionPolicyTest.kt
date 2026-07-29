package com.musicapp.player.media.service

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ControllerConnectionPolicyTest {
    private val policy = ControllerConnectionPolicy(APP_PACKAGE, APP_UID)

    @Test
    fun acceptsOnlyTheApplicationPackageAtTheApplicationUidAsApplicationController() {
        assertEquals(
            ControllerAccess.APPLICATION,
            policy.accessFor(ControllerIdentity(APP_PACKAGE, APP_UID, isTrusted = false)),
        )
        assertEquals(
            ControllerAccess.REJECTED,
            policy.accessFor(ControllerIdentity(APP_PACKAGE, APP_UID + 1, isTrusted = false)),
        )
    }

    @Test
    fun acceptsAnExternalControllerOnlyWhenMedia3MarksItTrusted() {
        assertEquals(
            ControllerAccess.TRUSTED_SYSTEM,
            policy.accessFor(ControllerIdentity("android", 1_000, isTrusted = true)),
        )
        assertEquals(
            ControllerAccess.REJECTED,
            policy.accessFor(ControllerIdentity("example.untrusted", 20_000, isTrusted = false)),
        )
    }

    @Test
    fun trustedSystemCommandsContainOnlyImplementedBrowsingOperations() {
        val commands = TrustedSystemControllerCommands.sessionCommands

        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertFalse(commands.commands.any { it.commandCode == SessionCommand.COMMAND_CODE_CUSTOM })
    }

    @Test
    fun trustedSystemCommandsAllowBasicPlaybackAndBothTransportSkipVariants() {
        val commands = TrustedSystemControllerCommands.playerCommands

        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
        ).forEach { command -> assertTrue("missing player command $command", commands.contains(command)) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun trustedSystemCommandsExcludeQueueEditingModesAndDeviceVolume() {
        val commands = TrustedSystemControllerCommands.playerCommands

        listOf(
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME,
        ).forEach { command -> assertFalse("unexpected player command $command", commands.contains(command)) }
    }

    private companion object {
        const val APP_PACKAGE = "com.musicapp.player"
        const val APP_UID = 10_001
    }
}
