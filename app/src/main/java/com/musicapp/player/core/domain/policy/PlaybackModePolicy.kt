package com.musicapp.player.core.domain.policy

import com.musicapp.player.core.domain.model.PlaybackMode

object PlaybackModePolicy {
    val defaultMode: PlaybackMode = PlaybackMode.LIST_REPEAT

    fun indexAfterNaturalEnd(
        mode: PlaybackMode,
        currentIndex: Int,
        queueSize: Int,
    ): Int? = resolveIndex(mode, currentIndex, queueSize, Direction.NEXT, isNaturalEnd = true)

    fun indexAfterManualNext(
        mode: PlaybackMode,
        currentIndex: Int,
        queueSize: Int,
    ): Int? = resolveIndex(mode, currentIndex, queueSize, Direction.NEXT, isNaturalEnd = false)

    fun indexAfterManualPrevious(
        mode: PlaybackMode,
        currentIndex: Int,
        queueSize: Int,
    ): Int? = resolveIndex(mode, currentIndex, queueSize, Direction.PREVIOUS, isNaturalEnd = false)

    private fun resolveIndex(
        mode: PlaybackMode,
        currentIndex: Int,
        queueSize: Int,
        direction: Direction,
        isNaturalEnd: Boolean,
    ): Int? {
        if (queueSize == 0) return null
        require(queueSize > 0) { "queueSize must not be negative" }
        require(currentIndex in 0 until queueSize) { "currentIndex must be within the queue" }

        if (mode == PlaybackMode.SINGLE_REPEAT) {
            if (isNaturalEnd) return currentIndex
            return when (direction) {
                Direction.NEXT -> (currentIndex + 1).coerceAtMost(queueSize - 1)
                Direction.PREVIOUS -> (currentIndex - 1).coerceAtLeast(0)
            }
        }

        return when (direction) {
            Direction.NEXT -> (currentIndex + 1) % queueSize
            Direction.PREVIOUS -> if (currentIndex == 0) queueSize - 1 else currentIndex - 1
        }
    }

    private enum class Direction {
        NEXT,
        PREVIOUS,
    }
}
