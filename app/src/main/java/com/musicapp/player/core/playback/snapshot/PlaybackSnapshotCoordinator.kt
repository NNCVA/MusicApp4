package com.musicapp.player.core.playback.snapshot

import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PlaybackSnapshotTrigger {
    QUEUE_CHANGED,
    TRACK_CHANGED,
    SEEK_COMPLETED,
    PAUSED,
    DESTROYED,
    PLAYING_INTERVAL,
}

data class PlaybackSnapshotWrite(
    val trigger: PlaybackSnapshotTrigger,
    val snapshot: PlaybackSnapshot,
)

fun interface PlaybackSnapshotSink {
    suspend fun save(write: PlaybackSnapshotWrite)
}

fun interface PlaybackSnapshotClock {
    fun nowMs(): Long
}

fun interface PlaybackSnapshotScheduler {
    suspend fun wait(milliseconds: Long)
}

object CoroutinePlaybackSnapshotScheduler : PlaybackSnapshotScheduler {
    override suspend fun wait(milliseconds: Long) = delay(milliseconds)
}

class PlaybackSnapshotCoordinator(
    private val scope: CoroutineScope,
    private val snapshotProvider: () -> PlaybackSnapshot,
    private val sink: PlaybackSnapshotSink,
    private val clock: PlaybackSnapshotClock,
    private val scheduler: PlaybackSnapshotScheduler = CoroutinePlaybackSnapshotScheduler,
) {
    private val lock = Any()
    private val writeMutex = Mutex()
    private var periodicJob: Job? = null
    private var destroyed = false

    fun onQueueChanged(): Job? = saveNow(PlaybackSnapshotTrigger.QUEUE_CHANGED)

    fun onTrackChanged(): Job? = saveNow(PlaybackSnapshotTrigger.TRACK_CHANGED)

    fun onSeekCompleted(): Job? = saveNow(PlaybackSnapshotTrigger.SEEK_COMPLETED)

    fun onPaused(): Job? {
        stopPeriodicWrites()
        return saveNow(PlaybackSnapshotTrigger.PAUSED)
    }

    fun onPlaying() {
        synchronized(lock) {
            if (destroyed || periodicJob?.isActive == true || !scope.isActive) return
            periodicJob = scope.launch {
                while (isActive) {
                    scheduler.wait(POSITION_WRITE_INTERVAL_MS)
                    captureAndSave(PlaybackSnapshotTrigger.PLAYING_INTERVAL)
                }
            }
        }
    }

    fun onNotPlaying() {
        stopPeriodicWrites()
    }

    fun onDestroyed(): Job? {
        synchronized(lock) {
            if (destroyed) return null
            destroyed = true
            periodicJob?.cancel()
            periodicJob = null
        }
        return saveCaptured(PlaybackSnapshotTrigger.DESTROYED, capture())
    }

    private fun saveNow(trigger: PlaybackSnapshotTrigger): Job? {
        val snapshot = synchronized(lock) {
            if (destroyed || !scope.isActive) return null
            capture()
        }
        return saveCaptured(trigger, snapshot)
    }

    private fun capture(): PlaybackSnapshot =
        snapshotProvider().copy(updatedAtMs = clock.nowMs().also { require(it >= 0) })

    private fun saveCaptured(trigger: PlaybackSnapshotTrigger, snapshot: PlaybackSnapshot): Job =
        scope.launch {
            writeMutex.withLock { sink.save(PlaybackSnapshotWrite(trigger, snapshot)) }
        }

    private suspend fun captureAndSave(trigger: PlaybackSnapshotTrigger) {
        val snapshot = synchronized(lock) {
            if (destroyed) return
            capture()
        }
        writeMutex.withLock { sink.save(PlaybackSnapshotWrite(trigger, snapshot)) }
    }

    private fun stopPeriodicWrites() {
        synchronized(lock) {
            periodicJob?.cancel()
            periodicJob = null
        }
    }

    companion object {
        const val POSITION_WRITE_INTERVAL_MS: Long = 5_000
    }
}

data class RestoredPlaybackState(
    val snapshot: PlaybackSnapshot,
) {
    val playWhenReady: Boolean = false
}

object PlaybackSnapshotRestorer {
    fun restore(
        snapshot: PlaybackSnapshot?,
        existingQueueItemIds: Set<QueueItemId>,
    ): RestoredPlaybackState? = snapshot?.let { RestoredPlaybackState(it.prune(existingQueueItemIds)) }

    private fun PlaybackSnapshot.prune(existingIds: Set<QueueItemId>): PlaybackSnapshot {
        val remainingQueue = queue.originalQueue.filter { it.id in existingIds }
        if (remainingQueue.isEmpty()) {
            return copy(queue = PlaybackQueue(), positionMs = 0, playbackInstance = null)
        }
        val remainingIds = remainingQueue.map { it.id }.toSet()
        val stableSequence = queue.stableShuffleSequence.filter(remainingIds::contains)
        val oldPlaybackOrder = queue.playbackOrder.map { it.id }
        val currentSurvives = queue.currentItemId in remainingIds
        val currentId = when {
            queue.currentItemId == null -> null
            currentSurvives -> queue.currentItemId
            else -> {
                val oldIndex = oldPlaybackOrder.indexOf(queue.currentItemId)
                oldPlaybackOrder.drop(oldIndex + 1).firstOrNull(remainingIds::contains)
                    ?: oldPlaybackOrder.firstOrNull(remainingIds::contains)
            }
        }
        return copy(
            queue = PlaybackQueue(
                originalQueue = remainingQueue,
                stableShuffleSequence = stableSequence,
                currentItemId = currentId,
                shuffleRound = queue.shuffleRound,
                shuffleCursor = stableSequence.indexOf(currentId).takeIf { it >= 0 },
            ),
            positionMs = if (currentSurvives) positionMs else 0,
            playbackInstance = playbackInstance?.takeIf { currentSurvives && it.queueItemId == currentId },
        )
    }
}
