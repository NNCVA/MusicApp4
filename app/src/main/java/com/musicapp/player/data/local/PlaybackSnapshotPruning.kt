package com.musicapp.player.data.local

import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.TrackId

internal fun PlaybackSnapshot.withoutTracks(removedTrackIds: Set<TrackId>): PlaybackSnapshot {
    val remainingQueue = queue.originalQueue.filterNot { it.trackId in removedTrackIds }
    if (remainingQueue.isEmpty()) {
        return copy(
            queue = PlaybackQueue(),
            positionMs = 0,
            playbackInstance = null,
        )
    }
    val remainingIds = remainingQueue.map { it.id }.toSet()
    val stableSequence = queue.stableShuffleSequence.filter(remainingIds::contains)
    val currentId = if (queue.currentItemId == null) {
        null
    } else {
        queue.currentItemId.takeIf(remainingIds::contains)
            ?: queue.playbackOrder.dropWhile { it.id != queue.currentItemId }
                .drop(1)
                .firstOrNull { it.id in remainingIds }
                ?.id
            ?: (if (stableSequence.isNotEmpty()) stableSequence.first() else remainingQueue.first().id)
    }
    val shuffleCursor = stableSequence.indexOf(currentId).takeIf { it >= 0 }
    return copy(
        queue = PlaybackQueue(
            originalQueue = remainingQueue,
            stableShuffleSequence = stableSequence,
            currentItemId = currentId,
            shuffleRound = queue.shuffleRound,
            shuffleCursor = shuffleCursor,
        ),
        positionMs = if (currentId == queue.currentItemId) positionMs else 0,
        playbackInstance = playbackInstance?.takeIf { it.queueItemId == currentId },
    )
}
