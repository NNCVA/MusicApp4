package com.musicapp.player.media.service

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * Gives the notification delete PendingIntent an instance-specific action so that the exported
 * MediaLibraryService cannot be stopped by another app replaying a public, fixed action.
 */
internal class NotificationDismissIntentAuthenticator(
    token: String,
) {
    private val expectedAction: String

    init {
        require(token.isNotBlank()) { "token must not be blank" }
        expectedAction = "$ACTION_PREFIX.$token"
    }

    fun createIntent(context: Context): Intent =
        Intent(context, MusicPlaybackService::class.java).setAction(expectedAction)

    fun authenticates(intent: Intent?): Boolean = authenticatesAction(intent?.action)

    internal fun authenticatesAction(action: String?): Boolean = action == expectedAction

    companion object {
        private const val ACTION_PREFIX =
            "com.musicapp.player.action.DISMISS_PLAYBACK_NOTIFICATION"

        fun create(): NotificationDismissIntentAuthenticator =
            NotificationDismissIntentAuthenticator(UUID.randomUUID().toString())
    }
}
