package com.musicapp.player.media.service

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/** Keeps Media3's notification rendering while routing a swipe dismissal through the service. */
@OptIn(UnstableApi::class)
internal class DismissAwareMediaNotificationProvider(
    private val context: Context,
    private val dismissIntentAuthenticator: NotificationDismissIntentAuthenticator,
) : MediaNotification.Provider {
    private val delegate = DefaultMediaNotificationProvider.Builder(context).build()

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification = delegate.createNotification(
        mediaSession,
        mediaButtonPreferences,
        actionFactory,
        onNotificationChangedCallback,
    ).also { mediaNotification ->
        mediaNotification.notification.deleteIntent = PendingIntent.getService(
            context,
            DISMISS_REQUEST_CODE,
            dismissIntentAuthenticator.createIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaNotification.notification.flags =
            mediaNotification.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT.inv()
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo

    private companion object {
        const val DISMISS_REQUEST_CODE = 101
    }
}
