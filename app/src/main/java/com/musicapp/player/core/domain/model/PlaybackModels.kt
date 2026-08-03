package com.musicapp.player.core.domain.model

enum class PlaybackContextSource {
    TRACKS,
    ALBUM,
    ARTIST,
    PLAYLIST,
    HISTORY,
    FOLDER,
}

data class PlaybackContext(
    val source: PlaybackContextSource,
    val orderedTrackIds: List<TrackId>,
    val selectedTrackId: TrackId,
    val sourceId: String? = null,
) {
    init {
        require(orderedTrackIds.isNotEmpty()) { "orderedTrackIds must not be empty" }
        require(orderedTrackIds.size == orderedTrackIds.distinct().size) {
            "orderedTrackIds must not contain duplicates"
        }
        require(selectedTrackId in orderedTrackIds) { "selectedTrackId must be in orderedTrackIds" }
        require(sourceId == null || sourceId.isNotBlank()) { "sourceId must be null or non-blank" }
    }
}

@JvmInline
value class QueueItemId(val value: Long) {
    init {
        require(value > 0) { "QueueItemId must be positive" }
    }
}

data class QueueItem(
    val id: QueueItemId,
    val trackId: TrackId,
)

data class PlaybackQueue(
    val originalQueue: List<QueueItem> = emptyList(),
    val stableShuffleSequence: List<QueueItemId> = emptyList(),
    val currentItemId: QueueItemId? = null,
    val shuffleRound: Long = 0,
    val shuffleCursor: Int? = null,
) {
    init {
        val originalIds = originalQueue.map(QueueItem::id)
        require(originalIds.size == originalIds.distinct().size) {
            "QueueItemId must be unique in originalQueue"
        }
        require(stableShuffleSequence.size == stableShuffleSequence.distinct().size) {
            "QueueItemId must be unique in stableShuffleSequence"
        }
        require(stableShuffleSequence.isEmpty() || stableShuffleSequence.toSet() == originalIds.toSet()) {
            "stableShuffleSequence must contain every original queue item exactly once"
        }
        require(currentItemId == null || currentItemId in originalIds) {
            "currentItemId must refer to an original queue item"
        }
        require(shuffleRound >= 0) { "shuffleRound must not be negative" }
        require(shuffleCursor == null || shuffleCursor in stableShuffleSequence.indices) {
            "shuffleCursor must refer to stableShuffleSequence"
        }
        require(shuffleCursor == null || currentItemId == stableShuffleSequence[shuffleCursor]) {
            "shuffleCursor must point at currentItemId"
        }
        require(originalQueue.isNotEmpty() || currentItemId == null) {
            "an empty queue cannot have a current item"
        }
    }

    val currentItem: QueueItem?
        get() = originalQueue.firstOrNull { it.id == currentItemId }

    val playbackOrder: List<QueueItem>
        get() = if (stableShuffleSequence.isEmpty()) {
            originalQueue
        } else {
            val itemsById = originalQueue.associateBy(QueueItem::id)
            stableShuffleSequence.map(itemsById::getValue)
        }
}

enum class PlaybackMode {
    LIST_REPEAT,
    SINGLE_REPEAT,
    SHUFFLE;

    companion object {
        val DEFAULT: PlaybackMode = LIST_REPEAT
    }
}

data class PlaybackInstance(
    val queueItemId: QueueItemId,
    val trackId: TrackId,
    val startedAtMs: Long,
    val actualPlayedDurationMs: Long = 0,
    val historyRecorded: Boolean = false,
) {
    init {
        require(startedAtMs >= 0) { "startedAtMs must not be negative" }
        require(actualPlayedDurationMs >= 0) { "actualPlayedDurationMs must not be negative" }
    }
}

data class PlaybackSnapshot(
    val queue: PlaybackQueue = PlaybackQueue(),
    val positionMs: Long = 0,
    val playbackMode: PlaybackMode = PlaybackMode.DEFAULT,
    val playbackInstance: PlaybackInstance? = null,
    val updatedAtMs: Long = 0,
    val playbackResumptionAllowed: Boolean = true,
) {
    init {
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(updatedAtMs >= 0) { "updatedAtMs must not be negative" }
        require(playbackInstance == null || playbackInstance.queueItemId == queue.currentItemId) {
            "playbackInstance queueItemId must match the current queue item"
        }
        require(playbackInstance == null || queue.currentItem?.trackId == playbackInstance.trackId) {
            "playbackInstance trackId must match the current queue item"
        }
        require(queue.currentItem != null || positionMs == 0L) {
            "a snapshot without a current item must have zero position"
        }
    }
}
