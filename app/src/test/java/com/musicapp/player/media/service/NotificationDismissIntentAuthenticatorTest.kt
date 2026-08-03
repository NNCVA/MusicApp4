package com.musicapp.player.media.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDismissIntentAuthenticatorTest {
    private val authenticator = NotificationDismissIntentAuthenticator("instance-token")

    @Test
    fun acceptsOnlyItsInstanceSpecificDismissAction() {
        assertTrue(
            authenticator.authenticatesAction(
                "com.musicapp.player.action.DISMISS_PLAYBACK_NOTIFICATION.instance-token",
            ),
        )
        assertFalse(
            authenticator.authenticatesAction(
                "com.musicapp.player.action.DISMISS_PLAYBACK_NOTIFICATION",
            ),
        )
        assertFalse(
            authenticator.authenticatesAction(
                "com.musicapp.player.action.DISMISS_PLAYBACK_NOTIFICATION.other-token",
            ),
        )
        assertFalse(authenticator.authenticatesAction(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankToken() {
        NotificationDismissIntentAuthenticator(" ")
    }
}
