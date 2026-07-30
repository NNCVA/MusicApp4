package com.musicapp.player.media.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackFailureCode

internal object PlaybackSessionProtocol {
    private const val PREFIX = "com.musicapp.player.session.v1"

    val replaceQueueCommand = SessionCommand("$PREFIX.REPLACE_QUEUE", Bundle.EMPTY)
    val setModeCommand = SessionCommand("$PREFIX.SET_MODE", Bundle.EMPTY)
    val addToQueueCommand = SessionCommand("$PREFIX.ADD_TO_QUEUE", Bundle.EMPTY)
    val playNextCommand = SessionCommand("$PREFIX.PLAY_NEXT", Bundle.EMPTY)
    val jumpToQueueItemCommand = SessionCommand("$PREFIX.JUMP_TO_QUEUE_ITEM", Bundle.EMPTY)
    val removeFromQueueCommand = SessionCommand("$PREFIX.REMOVE_FROM_QUEUE", Bundle.EMPTY)

    val applicationCommands = listOf(
        replaceQueueCommand,
        setModeCommand,
        addToQueueCommand,
        playNextCommand,
        jumpToQueueItemCommand,
        removeFromQueueCommand,
    )

    fun tracksArgs(
        tracks: List<Track>,
        startIndex: Int? = null,
        playWhenReady: Boolean? = null,
    ): Bundle = Bundle().apply {
        putParcelableArrayList(KEY_TRACKS, ArrayList(tracks.map(::trackBundle)))
        startIndex?.let { putInt(KEY_START_INDEX, it) }
        playWhenReady?.let { putBoolean(KEY_PLAY_WHEN_READY, it) }
    }

    fun decodeTracks(args: Bundle): List<PlaybackTrackPayload>? {
        @Suppress("DEPRECATION")
        val trackBundles = args.getParcelableArrayList<Bundle>(KEY_TRACKS) ?: return null
        return trackBundles.map { decodeTrack(it) ?: return null }
    }

    fun startIndex(args: Bundle): Int? =
        args.getInt(KEY_START_INDEX, -1).takeIf { it >= 0 }

    fun playWhenReady(args: Bundle): Boolean = args.getBoolean(KEY_PLAY_WHEN_READY)

    fun modeArgs(mode: PlaybackMode): Bundle = Bundle().apply { putString(KEY_MODE, mode.name) }

    fun decodeMode(args: Bundle): PlaybackMode? =
        args.getString(KEY_MODE)?.let { encoded ->
            PlaybackMode.entries.firstOrNull { it.name == encoded }
        }

    fun queueItemArgs(queueItemId: QueueItemId): Bundle =
        Bundle().apply { putLong(KEY_QUEUE_ITEM_ID, queueItemId.value) }

    fun decodeQueueItemId(args: Bundle): QueueItemId? =
        args.getLong(KEY_QUEUE_ITEM_ID).takeIf { it > 0 }?.let(::QueueItemId)

    fun stateExtras(
        mode: PlaybackMode,
        queue: PlaybackQueue,
        playbackFailure: PlaybackFailure? = null,
    ): Bundle = Bundle().apply {
        putString(KEY_MODE, mode.name)
        putLongArray(KEY_QUEUE_ITEM_IDS, queue.originalQueue.map { it.id.value }.toLongArray())
        putStringArrayList(
            KEY_TRACK_VOLUMES,
            ArrayList(queue.originalQueue.map { it.trackId.volumeName }),
        )
        putLongArray(
            KEY_TRACK_MEDIA_STORE_IDS,
            queue.originalQueue.map { it.trackId.mediaStoreId }.toLongArray(),
        )
        putLongArray(KEY_SHUFFLE_SEQUENCE, queue.stableShuffleSequence.map { it.value }.toLongArray())
        putLong(KEY_CURRENT_QUEUE_ITEM_ID, queue.currentItemId?.value ?: 0L)
        putLong(KEY_SHUFFLE_ROUND, queue.shuffleRound)
        putInt(KEY_SHUFFLE_CURSOR, queue.shuffleCursor ?: -1)
        playbackFailure?.let { putString(KEY_PLAYBACK_FAILURE_CODE, it.code.name) }
    }

    fun decodePlaybackFailure(extras: Bundle): PlaybackFailure? {
        val encoded = extras.getString(KEY_PLAYBACK_FAILURE_CODE) ?: return null
        val code = PlaybackFailureCode.entries.firstOrNull { it.name == encoded }
            ?: PlaybackFailureCode.UNKNOWN
        return PlaybackFailure(code)
    }

    fun decodeState(extras: Bundle): Pair<PlaybackMode, PlaybackQueue>? = runCatching {
        val mode = decodeMode(extras) ?: return null
        val queueIds = extras.getLongArray(KEY_QUEUE_ITEM_IDS) ?: return null
        val volumes = extras.getStringArrayList(KEY_TRACK_VOLUMES) ?: return null
        val trackIds = extras.getLongArray(KEY_TRACK_MEDIA_STORE_IDS) ?: return null
        if (queueIds.size != volumes.size || queueIds.size != trackIds.size) return null
        val original = queueIds.indices.map { index ->
            QueueItem(
                id = QueueItemId(queueIds[index]),
                trackId = TrackId(volumes[index], trackIds[index]),
            )
        }
        val current = extras.getLong(KEY_CURRENT_QUEUE_ITEM_ID).takeIf { it > 0 }?.let(::QueueItemId)
        val shuffle = (extras.getLongArray(KEY_SHUFFLE_SEQUENCE) ?: longArrayOf()).map(::QueueItemId)
        mode to PlaybackQueue(
            originalQueue = original,
            stableShuffleSequence = shuffle,
            currentItemId = current,
            shuffleRound = extras.getLong(KEY_SHUFFLE_ROUND),
            shuffleCursor = extras.getInt(KEY_SHUFFLE_CURSOR, -1).takeIf { it >= 0 },
        )
    }.getOrNull()

    private fun trackBundle(track: Track): Bundle = Bundle().apply {
        putString(KEY_VOLUME, track.id.volumeName)
        putLong(KEY_MEDIA_STORE_ID, track.id.mediaStoreId)
        putString(KEY_TITLE, track.title)
        putString(KEY_ARTIST, track.artistName)
        putString(KEY_ALBUM, track.albumTitle)
        putLong(KEY_DURATION, track.durationMs)
    }

    private fun decodeTrack(bundle: Bundle): PlaybackTrackPayload? {
        val volume = bundle.getString(KEY_VOLUME)?.takeIf(String::isNotBlank) ?: return null
        val mediaStoreId = bundle.getLong(KEY_MEDIA_STORE_ID).takeIf { it > 0 } ?: return null
        val title = bundle.getString(KEY_TITLE)?.takeIf(String::isNotBlank) ?: return null
        val artist = bundle.getString(KEY_ARTIST)?.takeIf(String::isNotBlank) ?: return null
        val durationMs = bundle.getLong(KEY_DURATION).takeIf { it > 0 } ?: return null
        return PlaybackTrackPayload(
            trackId = TrackId(volume, mediaStoreId),
            title = title,
            artistName = artist,
            albumTitle = bundle.getString(KEY_ALBUM),
            durationMs = durationMs,
        )
    }

    private const val KEY_TRACKS = "$PREFIX.tracks"
    private const val KEY_START_INDEX = "$PREFIX.start_index"
    private const val KEY_PLAY_WHEN_READY = "$PREFIX.play_when_ready"
    private const val KEY_MODE = "$PREFIX.mode"
    private const val KEY_QUEUE_ITEM_ID = "$PREFIX.queue_item_id"
    private const val KEY_VOLUME = "$PREFIX.volume"
    private const val KEY_MEDIA_STORE_ID = "$PREFIX.media_store_id"
    private const val KEY_TITLE = "$PREFIX.title"
    private const val KEY_ARTIST = "$PREFIX.artist"
    private const val KEY_ALBUM = "$PREFIX.album"
    private const val KEY_DURATION = "$PREFIX.duration"
    private const val KEY_QUEUE_ITEM_IDS = "$PREFIX.queue_item_ids"
    private const val KEY_TRACK_VOLUMES = "$PREFIX.track_volumes"
    private const val KEY_TRACK_MEDIA_STORE_IDS = "$PREFIX.track_media_store_ids"
    private const val KEY_SHUFFLE_SEQUENCE = "$PREFIX.shuffle_sequence"
    private const val KEY_CURRENT_QUEUE_ITEM_ID = "$PREFIX.current_queue_item_id"
    private const val KEY_SHUFFLE_ROUND = "$PREFIX.shuffle_round"
    private const val KEY_SHUFFLE_CURSOR = "$PREFIX.shuffle_cursor"
    private const val KEY_PLAYBACK_FAILURE_CODE = "$PREFIX.playback_failure_code"
}

internal data class PlaybackTrackPayload(
    val trackId: TrackId,
    val title: String,
    val artistName: String,
    val albumTitle: String?,
    val durationMs: Long,
)

internal object QueueMediaIdCodec {
    private const val PREFIX = "q1:"

    fun encode(queueItemId: QueueItemId, trackId: TrackId): String =
        "$PREFIX${queueItemId.value}:${TrackMediaIdCodec.encode(trackId)}"

    fun decode(mediaId: String): QueueItem? {
        if (!mediaId.startsWith(PREFIX)) return null
        val separator = mediaId.indexOf(':', PREFIX.length)
        if (separator < 0) return null
        val queueItemId = mediaId.substring(PREFIX.length, separator).toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let(::QueueItemId)
            ?: return null
        val trackId = TrackMediaIdCodec.decode(mediaId.substring(separator + 1)) ?: return null
        return QueueItem(queueItemId, trackId)
    }
}
