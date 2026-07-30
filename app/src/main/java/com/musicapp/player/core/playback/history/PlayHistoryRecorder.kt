package com.musicapp.player.core.playback.history

import com.musicapp.player.core.domain.model.PlaybackInstance
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId

/**
 * Tracks actual playing time for one queue item and emits a history record at most once.
 *
 * Call [updateIsPlaying] whenever `Player.isPlaying` changes. Operations that must not count,
 * such as a seek, can be bracketed with [onSeekStarted] and [onSeekCompleted].
 */
class PlayHistoryRecorder(
    private val monotonicNowMs: () -> Long,
    private val wallClockNowMs: () -> Long,
    private val onHistoryThresholdReached: (trackId: TrackId, playedAtMs: Long) -> Unit,
) {
    private var current: ActivePlaybackInstance? = null

    fun startInstance(
        queueItemId: QueueItemId,
        trackId: TrackId,
        durationMs: Long,
        isPlaying: Boolean,
    ) {
        require(durationMs > 0) { "durationMs must be positive" }
        current?.takeIf { it.queueItemId == queueItemId }?.let { previous ->
            require(previous.trackId == trackId) { "a QueueItemId cannot change TrackId" }
        }
        val nowMs = monotonicNowMs()
        settleAt(nowMs)
        emitIfThresholdReached()

        val previous = current
        current = if (previous?.queueItemId == queueItemId) {
            previous.copy(
                durationMs = durationMs,
                playingSinceMs = nowMs.takeIf { isPlaying },
            )
        } else {
            ActivePlaybackInstance(
                queueItemId = queueItemId,
                trackId = trackId,
                durationMs = durationMs,
                startedAtMs = wallClockNowMs().also(::requireNonNegativeClock),
                playingSinceMs = nowMs.takeIf { isPlaying },
            )
        }
        emitIfThresholdReached()
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        val nowMs = monotonicNowMs()
        settleAt(nowMs)
        current = current?.copy(playingSinceMs = nowMs.takeIf { isPlaying })
        emitIfThresholdReached()
    }

    fun onSeekStarted() = updateIsPlaying(isPlaying = false)

    fun onSeekCompleted(isPlaying: Boolean) = updateIsPlaying(isPlaying)

    fun tick() {
        settleAt(monotonicNowMs())
        emitIfThresholdReached()
    }

    fun stopInstance() {
        settleAt(monotonicNowMs())
        emitIfThresholdReached()
        current = null
    }

    fun snapshot(): PlaybackInstance? {
        tick()
        return current?.toPlaybackInstance()
    }

    private fun settleAt(nowMs: Long) {
        requireNonNegativeClock(nowMs)
        val instance = current ?: return
        val playingSinceMs = instance.playingSinceMs ?: return
        require(nowMs >= playingSinceMs) { "monotonic clock moved backwards" }
        val elapsedMs = nowMs - playingSinceMs
        current = instance.copy(
            actualPlayedDurationMs = saturatedAdd(instance.actualPlayedDurationMs, elapsedMs),
            playingSinceMs = nowMs,
        )
    }

    private fun emitIfThresholdReached() {
        val instance = current ?: return
        if (instance.historyRecorded || instance.actualPlayedDurationMs < historyThresholdMs(instance.durationMs)) {
            return
        }
        current = instance.copy(historyRecorded = true)
        val playedAtMs = wallClockNowMs().also(::requireNonNegativeClock)
        onHistoryThresholdReached(instance.trackId, playedAtMs)
    }

    private fun requireNonNegativeClock(valueMs: Long) {
        require(valueMs >= 0) { "clock values must not be negative" }
    }

    private data class ActivePlaybackInstance(
        val queueItemId: QueueItemId,
        val trackId: TrackId,
        val durationMs: Long,
        val startedAtMs: Long,
        val actualPlayedDurationMs: Long = 0,
        val historyRecorded: Boolean = false,
        val playingSinceMs: Long? = null,
    ) {
        fun toPlaybackInstance() = PlaybackInstance(
            queueItemId = queueItemId,
            trackId = trackId,
            startedAtMs = startedAtMs,
            actualPlayedDurationMs = actualPlayedDurationMs,
            historyRecorded = historyRecorded,
        )
    }

    companion object {
        const val MAX_HISTORY_THRESHOLD_MS = 30_000L

        fun historyThresholdMs(durationMs: Long): Long {
            require(durationMs > 0) { "durationMs must be positive" }
            val roundedUpHalf = durationMs / 2 + durationMs % 2
            return minOf(MAX_HISTORY_THRESHOLD_MS, roundedUpHalf)
        }

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
