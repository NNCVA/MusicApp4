package com.musicapp.player.core.playback.system

enum class PlaybackRestoreOrigin {
    PROCESS_RECREATION,
    DEVICE_RESTART,
}

sealed interface PlaybackResumptionEvent {
    data object NotificationDismissed : PlaybackResumptionEvent

    data object PlaybackStopped : PlaybackResumptionEvent

    data object UserPlayRequested : PlaybackResumptionEvent

    data class Restored(
        val origin: PlaybackRestoreOrigin,
        val hasRestorableSnapshot: Boolean,
        val storedResumptionAllowed: Boolean,
    ) : PlaybackResumptionEvent
}

/**
 * Pure policy output consumed by the service and snapshot coordinator.
 *
 * [playbackResumptionAllowed] is the value persisted with the snapshot. [resumeEntryVisible]
 * controls whether the system may offer a passive resume entry; it never implies playback.
 */
data class PlaybackResumptionDecision(
    val playbackResumptionAllowed: Boolean,
    val resumeEntryVisible: Boolean,
    val playWhenReady: Boolean,
    val restoreOrigin: PlaybackRestoreOrigin? = null,
)

object PlaybackResumptionPolicy {
    fun decide(event: PlaybackResumptionEvent): PlaybackResumptionDecision =
        when (event) {
            PlaybackResumptionEvent.NotificationDismissed,
            PlaybackResumptionEvent.PlaybackStopped,
            -> inactiveDecision()

            PlaybackResumptionEvent.UserPlayRequested ->
                PlaybackResumptionDecision(
                    playbackResumptionAllowed = true,
                    resumeEntryVisible = false,
                    playWhenReady = true,
                )

            is PlaybackResumptionEvent.Restored ->
                PlaybackResumptionDecision(
                    playbackResumptionAllowed = event.storedResumptionAllowed,
                    resumeEntryVisible =
                        event.hasRestorableSnapshot && event.storedResumptionAllowed,
                    playWhenReady = false,
                    restoreOrigin = event.origin,
                )
        }

    private fun inactiveDecision() =
        PlaybackResumptionDecision(
            playbackResumptionAllowed = false,
            resumeEntryVisible = false,
            playWhenReady = false,
        )
}
