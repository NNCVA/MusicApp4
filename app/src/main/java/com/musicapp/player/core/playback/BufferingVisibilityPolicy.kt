package com.musicapp.player.core.playback

/** Keeps short buffering changes invisible while making longer stalls observable. */
class BufferingVisibilityPolicy(
    private val delayMs: Long = DEFAULT_DELAY_MS,
) {
    private var bufferingStartedAtMs: Long? = null

    fun update(isBuffering: Boolean, nowMs: Long): Boolean {
        require(nowMs >= 0) { "nowMs must not be negative" }
        if (!isBuffering) {
            bufferingStartedAtMs = null
            return false
        }
        val startedAtMs = bufferingStartedAtMs ?: nowMs.also { bufferingStartedAtMs = it }
        require(nowMs >= startedAtMs) { "monotonic clock moved backwards" }
        return nowMs - startedAtMs >= delayMs
    }

    fun reset() {
        bufferingStartedAtMs = null
    }

    companion object {
        const val DEFAULT_DELAY_MS = 300L
    }
}
