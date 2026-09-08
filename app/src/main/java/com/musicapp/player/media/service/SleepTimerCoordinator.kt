package com.musicapp.player.media.service

import com.musicapp.player.core.playback.timer.SleepTimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SleepTimerCoordinator(
    private val scope: CoroutineScope,
    private val isCurrentlyPlaying: () -> Boolean,
    private val onFadeAndPause: suspend () -> Unit,
    private val onStatusChanged: (SleepTimerStatus?) -> Unit,
    private val onManualCancellationNotice: (() -> Unit)? = null,
    private val tickIntervalMs: Long = TICK_INTERVAL_MS,
) {
    private var timerJob: Job? = null
    var currentStatus: SleepTimerStatus? = null
        private set

    fun start(durationMinutes: Int, extendToEndOfTrack: Boolean) {
        require(durationMinutes > 0) { "durationMinutes must be positive" }
        stop(notify = false)
        val totalMs = durationMinutes * 60_000L
        val initialStatus = SleepTimerStatus(
            remainingMs = totalMs,
            totalDurationMs = totalMs,
            extendToEndOfTrack = extendToEndOfTrack,
            isWaitingForTrackEnd = false,
            extendedElapsedMs = 0L,
        )
        currentStatus = initialStatus
        onStatusChanged(initialStatus)

        timerJob = scope.launch {
            var remaining = totalMs
            while (true) {
                delay(tickIntervalMs)
                val status = currentStatus ?: break

                if (!status.isWaitingForTrackEnd) {
                    remaining = (remaining - tickIntervalMs).coerceAtLeast(0L)
                    if (remaining <= 0L) {
                        if (status.extendToEndOfTrack && isCurrentlyPlaying()) {
                            val waitingStatus = status.copy(
                                remainingMs = 0L,
                                isWaitingForTrackEnd = true,
                                extendedElapsedMs = 0L,
                            )
                            currentStatus = waitingStatus
                            onStatusChanged(waitingStatus)
                        } else {
                            currentStatus = null
                            onStatusChanged(null)
                            onFadeAndPause()
                            break
                        }
                    } else {
                        val updated = status.copy(remainingMs = remaining)
                        currentStatus = updated
                        onStatusChanged(updated)
                    }
                } else {
                    val extended = status.extendedElapsedMs + tickIntervalMs
                    val updated = status.copy(extendedElapsedMs = extended)
                    currentStatus = updated
                    onStatusChanged(updated)
                }
            }
        }
    }

    fun stop(notify: Boolean = true) {
        timerJob?.cancel()
        timerJob = null
        val wasActive = currentStatus != null
        currentStatus = null
        if (wasActive && notify) {
            onStatusChanged(null)
        }
    }

    fun onTrackEndedNaturally() {
        if (currentStatus?.isWaitingForTrackEnd == true) {
            stop(notify = true)
            scope.launch {
                onFadeAndPause()
            }
        }
    }

    fun onManualInterruption() {
        if (currentStatus?.isWaitingForTrackEnd == true) {
            stop(notify = true)
            onManualCancellationNotice?.invoke()
        }
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
