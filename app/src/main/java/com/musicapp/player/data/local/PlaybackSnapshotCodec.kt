package com.musicapp.player.data.local

import com.musicapp.player.core.domain.model.PlaybackInstance
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import org.json.JSONArray
import org.json.JSONObject

internal object PlaybackSnapshotCodec {
    fun encode(snapshot: PlaybackSnapshot): PlaybackSnapshotEntity = PlaybackSnapshotEntity(
        originalQueueJson = JSONArray().apply {
            snapshot.queue.originalQueue.forEach { item ->
                put(
                    JSONObject()
                        .put("queueItemId", item.id.value)
                        .put("volumeName", item.trackId.volumeName)
                        .put("mediaStoreId", item.trackId.mediaStoreId),
                )
            }
        }.toString(),
        stableShuffleSequenceJson = JSONArray().apply {
            snapshot.queue.stableShuffleSequence.forEach { put(it.value) }
        }.toString(),
        currentQueueItemId = snapshot.queue.currentItemId?.value,
        shuffleRound = snapshot.queue.shuffleRound,
        shuffleCursor = snapshot.queue.shuffleCursor,
        positionMs = snapshot.positionMs,
        playbackMode = snapshot.playbackMode.name,
        playbackInstanceJson = snapshot.playbackInstance?.let { instance ->
            JSONObject()
                .put("queueItemId", instance.queueItemId.value)
                .put("volumeName", instance.trackId.volumeName)
                .put("mediaStoreId", instance.trackId.mediaStoreId)
                .put("startedAtMs", instance.startedAtMs)
                .put("actualPlayedDurationMs", instance.actualPlayedDurationMs)
                .put("historyRecorded", instance.historyRecorded)
                .toString()
        },
        updatedAtMs = snapshot.updatedAtMs,
        playbackResumptionAllowed = snapshot.playbackResumptionAllowed,
    )

    fun decode(entity: PlaybackSnapshotEntity): PlaybackSnapshot {
        val originalQueueArray = JSONArray(entity.originalQueueJson)
        val originalQueue = buildList {
            for (index in 0 until originalQueueArray.length()) {
                val item = originalQueueArray.getJSONObject(index)
                add(
                    QueueItem(
                        id = QueueItemId(item.getLong("queueItemId")),
                        trackId = TrackId(
                            volumeName = item.getString("volumeName"),
                            mediaStoreId = item.getLong("mediaStoreId"),
                        ),
                    ),
                )
            }
        }
        val stableSequenceArray = JSONArray(entity.stableShuffleSequenceJson)
        val stableSequence = buildList {
            for (index in 0 until stableSequenceArray.length()) {
                add(QueueItemId(stableSequenceArray.getLong(index)))
            }
        }
        val playbackInstance = entity.playbackInstanceJson?.let { encoded ->
            val instance = JSONObject(encoded)
            PlaybackInstance(
                queueItemId = QueueItemId(instance.getLong("queueItemId")),
                trackId = TrackId(
                    volumeName = instance.getString("volumeName"),
                    mediaStoreId = instance.getLong("mediaStoreId"),
                ),
                startedAtMs = instance.getLong("startedAtMs"),
                actualPlayedDurationMs = instance.getLong("actualPlayedDurationMs"),
                historyRecorded = instance.getBoolean("historyRecorded"),
            )
        }
        return PlaybackSnapshot(
            queue = PlaybackQueue(
                originalQueue = originalQueue,
                stableShuffleSequence = stableSequence,
                currentItemId = entity.currentQueueItemId?.let(::QueueItemId),
                shuffleRound = entity.shuffleRound,
                shuffleCursor = entity.shuffleCursor,
            ),
            positionMs = entity.positionMs,
            playbackMode = PlaybackMode.valueOf(entity.playbackMode),
            playbackInstance = playbackInstance,
            updatedAtMs = entity.updatedAtMs,
            playbackResumptionAllowed = entity.playbackResumptionAllowed,
        )
    }
}
