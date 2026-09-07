package com.musicapp.player.feature.player

import com.musicapp.player.core.domain.model.TrackId

internal data class PlayerSeekPending(
    val requestId: Long,
    val trackId: TrackId,
    val durationMs: Long,
    val targetMs: Long,
    val baselineMs: Long,
)

internal data class PlayerSeekCommit(
    val state: PlayerSeekState,
    val targetMs: Long,
)

internal data class PlayerSeekState(
    val dragFraction: Float? = null,
    val pending: PlayerSeekPending? = null,
    val nextRequestId: Long = 0L,
) {
    fun beginDrag(fraction: Float): PlayerSeekState =
        copy(dragFraction = fraction.coerceIn(0f, 1f))

    fun updateDrag(fraction: Float): PlayerSeekState =
        copy(dragFraction = fraction.coerceIn(0f, 1f))

    fun cancelDrag(): PlayerSeekState = copy(dragFraction = null)

    fun commit(
        trackId: TrackId,
        durationMs: Long,
        baselineMs: Long,
    ): PlayerSeekCommit {
        require(durationMs > 0) { "durationMs must be positive" }
        val fraction = checkNotNull(dragFraction) { "cannot commit without an active drag" }
        val targetMs = kotlin.math.round(durationMs.toDouble() * fraction)
            .toLong()
            .coerceIn(0, durationMs)
        val requestId = nextRequestId + 1
        return PlayerSeekCommit(
            state = copy(
                dragFraction = null,
                pending = PlayerSeekPending(
                    requestId = requestId,
                    trackId = trackId,
                    durationMs = durationMs,
                    targetMs = targetMs,
                    baselineMs = baselineMs.coerceIn(0, durationMs),
                ),
                nextRequestId = requestId,
            ),
            targetMs = targetMs,
        )
    }

    fun acknowledgePosition(
        requestId: Long,
        trackId: TrackId,
        durationMs: Long,
        positionMs: Long,
    ): PlayerSeekState {
        val activePending = pending ?: return this
        if (activePending.requestId != requestId) return this
        if (activePending.trackId != trackId || activePending.durationMs != durationMs) return this
        return if (PlayerSeekPolicy.isAcknowledged(activePending, positionMs)) {
            copy(pending = null)
        } else {
            this
        }
    }

    fun timeout(requestId: Long): PlayerSeekState =
        if (pending?.requestId == requestId) copy(pending = null) else this

    fun resetForSource(): PlayerSeekState =
        copy(dragFraction = null, pending = null)
}

internal object PlayerSeekPolicy {
    const val ACK_TOLERANCE_MS = 500L
    const val TIMEOUT_MS = 1_000L

    fun isAcknowledged(pending: PlayerSeekPending, observedPositionMs: Long): Boolean {
        val observed = observedPositionMs.coerceIn(0, pending.durationMs)
        val target = pending.targetMs
        val baseline = pending.baselineMs
        return when {
            target == baseline -> true
            target < baseline -> observed < baseline && observed <= target + ACK_TOLERANCE_MS
            else -> observed > baseline && observed >= target - ACK_TOLERANCE_MS
        }
    }
}
