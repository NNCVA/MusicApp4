package com.musicapp.player.media.service

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.playback.PlaybackQueueReducer
import com.musicapp.player.core.playback.PlaybackQueueState
import com.musicapp.player.media.playback.PlaybackSessionProtocol
import com.musicapp.player.media.playback.PlaybackTrackPayload
import com.musicapp.player.media.playback.QueueMediaIdCodec

internal class PlaybackQueueCoordinator(
    private val player: QueuePlayer,
    randomSource: RandomSource,
) {
    private val reducer = PlaybackQueueReducer(randomSource)
    private val mediaItemsById = linkedMapOf<QueueItemId, MediaItem>()
    private var nextQueueItemId = 1L
    private var state = PlaybackQueueState()
    private var publishState: (android.os.Bundle) -> Unit = {}
    private var onQueueStateChanged: () -> Unit = {}
    private var rebuildingShuffleRound = false

    val currentState: PlaybackQueueState
        get() = state

    val canNavigate: Boolean
        get() = state.queue.originalQueue.size > 1

    fun attachStatePublisher(publisher: (android.os.Bundle) -> Unit) {
        publishState = publisher
        publish()
    }

    fun attachQueueStateListener(listener: () -> Unit) {
        onQueueStateChanged = listener
    }

    fun replaceQueue(
        tracks: List<PlaybackTrackPayload>,
        startIndex: Int,
        playWhenReady: Boolean,
    ) {
        require(tracks.isNotEmpty()) { "tracks must not be empty" }
        require(startIndex in tracks.indices) { "startIndex must be within tracks" }
        mediaItemsById.clear()
        val items = tracks.map { track ->
            QueueItem(QueueItemId(nextQueueItemId++), track.trackId).also { queueItem ->
                mediaItemsById[queueItem.id] = track.toMediaItem(queueItem.id)
            }
        }
        state = reducer.replaceQueue(items, items[startIndex].id, state.mode)
        applyTimeline(positionMs = 0, playWhenReady = playWhenReady)
    }

    /** Restores identities before Media3 applies the returned playback-resumption timeline. */
    fun restoreSnapshot(
        snapshot: PlaybackSnapshot,
        tracksByQueueItemId: Map<QueueItemId, PlaybackTrackPayload>,
    ) {
        require(snapshot.queue.originalQueue.isNotEmpty()) { "snapshot queue must not be empty" }
        require(snapshot.queue.originalQueue.all { it.id in tracksByQueueItemId }) {
            "every restored queue item must have track metadata"
        }
        mediaItemsById.clear()
        snapshot.queue.originalQueue.forEach { queueItem ->
            mediaItemsById[queueItem.id] = tracksByQueueItemId.getValue(queueItem.id).toMediaItem(queueItem.id)
        }
        nextQueueItemId = (snapshot.queue.originalQueue.maxOfOrNull { it.id.value } ?: 0L) + 1
        state = PlaybackQueueState(snapshot.queue, snapshot.playbackMode)
        publishState(stateExtras())
    }

    fun mediaItemsInPlaybackOrder(): List<MediaItem> =
        state.queue.playbackOrder.map { mediaItemsById.getValue(it.id) }

    fun advanceRestoredPastEnd() {
        if (state.queue.currentItemId == null) return
        state = reducer.naturalEnd(state)
        publishState(stateExtras())
    }

    fun setMode(mode: PlaybackMode) {
        if (state.mode == mode) return
        val position = player.currentPositionMs
        val shouldPlay = player.playWhenReady
        state = reducer.setMode(state, mode)
        applyTimeline(position, shouldPlay)
    }

    fun addToQueue(tracks: List<PlaybackTrackPayload>) {
        if (tracks.isEmpty()) return
        if (state.queue.originalQueue.isEmpty()) {
            replaceQueue(tracks, startIndex = 0, playWhenReady = false)
            return
        }
        val newItems = createQueueItems(tracks)
        state = reducer.append(state, newItems)
        applyTimeline(player.currentPositionMs, player.playWhenReady)
    }

    fun playNext(tracks: List<PlaybackTrackPayload>) {
        if (tracks.isEmpty()) return
        if (state.queue.originalQueue.isEmpty()) {
            replaceQueue(tracks, startIndex = 0, playWhenReady = false)
            return
        }
        val newItems = createQueueItems(tracks)
        state = reducer.playNext(state, newItems)
        applyTimeline(player.currentPositionMs, player.playWhenReady)
    }

    fun remove(queueItemId: QueueItemId) {
        val removingCurrent = state.queue.currentItemId == queueItemId
        val updated = reducer.remove(state, queueItemId)
        if (updated == state) return
        state = updated
        mediaItemsById.remove(queueItemId)
        if (state.queue.originalQueue.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            publish()
            return
        }
        applyTimeline(
            positionMs = if (removingCurrent) 0 else player.currentPositionMs,
            playWhenReady = player.playWhenReady,
        )
    }

    fun manualNext() {
        if (state.queue.currentItemId == null) return
        val previousRound = state.queue.shuffleRound
        state = reducer.manualNext(state)
        if (state.queue.shuffleRound != previousRound) {
            applyTimeline(positionMs = 0, playWhenReady = player.playWhenReady)
        } else {
            seekToCurrent()
        }
    }

    fun manualPrevious() {
        if (state.queue.currentItemId == null) return
        state = reducer.manualPrevious(state)
        seekToCurrent()
    }

    fun naturalNext() {
        if (state.queue.currentItemId == null) return
        val previousRound = state.queue.shuffleRound
        state = reducer.naturalEnd(state)
        if (state.queue.shuffleRound != previousRound) {
            applyTimeline(positionMs = 0, playWhenReady = player.playWhenReady)
        } else {
            seekToCurrent()
        }
    }

    fun recoverTo(queueItemId: QueueItemId): Boolean {
        if (state.queue.originalQueue.none { it.id == queueItemId }) return false
        state = state.copy(
            queue = state.queue.copy(
                currentItemId = queueItemId,
                shuffleCursor = if (state.mode == PlaybackMode.SHUFFLE) {
                    state.queue.stableShuffleSequence.indexOf(queueItemId).takeIf { it >= 0 }
                } else {
                    null
                },
            ),
        )
        seekToCurrent()
        return true
    }

    fun stopPlayback() {
        player.stop()
        publish()
    }

    fun onMediaItemTransition(mediaItem: MediaItem?) {
        if (rebuildingShuffleRound) return
        val transitioned = QueueMediaIdCodec.decode(mediaItem?.mediaId.orEmpty()) ?: return
        if (state.queue.originalQueue.none { it.id == transitioned.id }) return
        state = state.copy(
            queue = state.queue.copy(
                currentItemId = transitioned.id,
                shuffleCursor = if (state.mode == PlaybackMode.SHUFFLE) {
                    state.queue.stableShuffleSequence.indexOf(transitioned.id).takeIf { it >= 0 }
                } else {
                    null
                },
            ),
        )
        publish()
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED || state.mode != PlaybackMode.SHUFFLE) return
        if (state.queue.currentItemId == null || rebuildingShuffleRound) return
        rebuildingShuffleRound = true
        try {
            naturalNext()
        } finally {
            rebuildingShuffleRound = false
        }
    }

    fun stateExtras() = PlaybackSessionProtocol.stateExtras(state.mode, state.queue)

    private fun createQueueItems(tracks: List<PlaybackTrackPayload>): List<QueueItem> =
        tracks.map { track ->
            QueueItem(QueueItemId(nextQueueItemId++), track.trackId).also { queueItem ->
                mediaItemsById[queueItem.id] = track.toMediaItem(queueItem.id)
            }
        }

    private fun applyTimeline(
        positionMs: Long,
        playWhenReady: Boolean,
    ) {
        val order = state.queue.playbackOrder
        val currentIndex = order.indexOfFirst { it.id == state.queue.currentItemId }
        player.repeatMode = when (state.mode) {
            PlaybackMode.LIST_REPEAT -> Player.REPEAT_MODE_ALL
            PlaybackMode.SINGLE_REPEAT -> Player.REPEAT_MODE_ONE
            PlaybackMode.SHUFFLE -> Player.REPEAT_MODE_OFF
        }
        player.shuffleModeEnabled = false
        if (currentIndex < 0) {
            publish()
            return
        }
        player.setMediaItems(order.map { mediaItemsById.getValue(it.id) }, currentIndex, positionMs)
        player.prepare()
        if (playWhenReady) player.play() else player.pause()
        publish()
    }

    private fun seekToCurrent() {
        val targetIndex = state.queue.playbackOrder.indexOfFirst { it.id == state.queue.currentItemId }
        if (targetIndex < 0) return
        player.seekTo(targetIndex, 0)
        publish()
    }

    private fun publish() {
        publishState(stateExtras())
        onQueueStateChanged()
    }

    fun clearRuntimeQueue() {
        state = PlaybackQueueState(mode = state.mode)
        mediaItemsById.clear()
        player.stop()
        player.clearMediaItems()
        publish()
    }
}

internal interface QueuePlayer {
    val currentPositionMs: Long
    val playWhenReady: Boolean
    var repeatMode: Int
    var shuffleModeEnabled: Boolean

    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long)
    fun prepare()
    fun play()
    fun pause()
    fun seekTo(mediaItemIndex: Int, positionMs: Long)
    fun stop()
    fun clearMediaItems()
}

internal class Media3QueuePlayer(private val player: Player) : QueuePlayer {
    override val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0)
    override val playWhenReady: Boolean
        get() = player.playWhenReady
    override var repeatMode: Int
        get() = player.repeatMode
        set(value) { player.repeatMode = value }
    override var shuffleModeEnabled: Boolean
        get() = player.shuffleModeEnabled
        set(value) { player.shuffleModeEnabled = value }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) =
        player.setMediaItems(mediaItems, startIndex, startPositionMs)

    override fun prepare() = player.prepare()
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = player.seekTo(mediaItemIndex, positionMs)
    override fun stop() = player.stop()
    override fun clearMediaItems() = player.clearMediaItems()
}

internal fun PlaybackTrackPayload.toMediaItem(queueItemId: QueueItemId): MediaItem =
    MediaItem.Builder()
        .setMediaId(QueueMediaIdCodec.encode(queueItemId, trackId))
        .setUri(
            ContentUris.withAppendedId(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(trackId.volumeName)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                },
                trackId.mediaStoreId,
            ),
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistName)
                .setAlbumTitle(albumTitle)
                .setDurationMs(durationMs)
                .build(),
        )
        .build()
