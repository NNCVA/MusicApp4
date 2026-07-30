package com.musicapp.player.media.service

/**
 * Enforces the full-exit ordering independently from Android service lifecycle callbacks.
 */
internal class PlaybackServiceShutdownCoordinator {
    suspend fun shutdown(
        persistFinalSnapshot: suspend () -> Unit,
        clearRuntimeQueue: () -> Unit,
        stopPlaybackService: () -> Unit,
    ) {
        persistFinalSnapshot()
        clearRuntimeQueue()
        stopPlaybackService()
    }
}
