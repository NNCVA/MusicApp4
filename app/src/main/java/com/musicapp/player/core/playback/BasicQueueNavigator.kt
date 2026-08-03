package com.musicapp.player.core.playback

/** Minimal list-repeat navigation. Advanced queue modes are introduced by process 14. */
object BasicQueueNavigator {
    fun nextIndex(currentIndex: Int, queueSize: Int): Int? =
        navigate(currentIndex, queueSize) { index -> (index + 1) % queueSize }

    fun previousIndex(currentIndex: Int, queueSize: Int): Int? =
        navigate(currentIndex, queueSize) { index -> if (index == 0) queueSize - 1 else index - 1 }

    private inline fun navigate(
        currentIndex: Int,
        queueSize: Int,
        resolve: (Int) -> Int,
    ): Int? {
        if (queueSize == 0) return null
        require(queueSize > 0) { "queueSize must not be negative" }
        require(currentIndex in 0 until queueSize) { "currentIndex must be within the queue" }
        return resolve(currentIndex)
    }
}
