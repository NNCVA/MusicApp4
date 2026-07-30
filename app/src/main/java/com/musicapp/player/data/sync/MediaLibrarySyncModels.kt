package com.musicapp.player.data.sync

import com.musicapp.player.core.media.MediaAudioCandidate
import kotlinx.coroutines.flow.Flow

enum class MediaLibrarySyncMode {
    FULL,
    INCREMENTAL,
}

enum class MediaLibrarySyncFailure {
    QUERY_FAILED,
    PERMISSION_LOST,
}

enum class MediaLibrarySyncTrigger {
    COLD_START,
    PERMISSION_GRANTED,
    CONTENT_CHANGE,
    MANUAL,
}

enum class MediaLibrarySyncFeedback {
    SILENT,
    RESULT_DIALOG,
}

enum class MediaLibraryScanSkipReason {
    UNSUPPORTED_FORMAT,
    NON_POSITIVE_DURATION,
    SYSTEM_AUDIO,
    EXCLUDED_PATH,
    OUTSIDE_INCLUDED_PATHS,
    DUPLICATE_IDENTITY,
    UNREADABLE_ITEM,
}

data class SkippedMediaItem(
    val displayName: String?,
    val reason: MediaLibraryScanSkipReason,
)

data class MediaLibraryScanSummary(
    val queriedCandidateCount: Int,
    val acceptedCandidates: List<MediaAudioCandidate>,
    val skippedItems: List<SkippedMediaItem>,
) {
    init {
        require(queriedCandidateCount >= acceptedCandidates.size + skippedItems.size)
    }

    companion object {
        val EMPTY = MediaLibraryScanSummary(0, emptyList(), emptyList())
    }
}

data class CompleteMediaLibraryScan(
    val mountedVolumeNames: Set<String>,
    val candidates: List<MediaAudioCandidate>,
    val volumeSignatures: Map<String, String?> = emptyMap(),
    val summary: MediaLibraryScanSummary = MediaLibraryScanSummary(
        queriedCandidateCount = candidates.size,
        acceptedCandidates = candidates,
        skippedItems = emptyList(),
    ),
) {
    init {
        require(mountedVolumeNames.none(String::isBlank)) {
            "mountedVolumeNames must not contain blank values"
        }
        require(candidates.all { it.volumeName in mountedVolumeNames }) {
            "every candidate must belong to a mounted volume"
        }
        require(candidates.map(MediaAudioCandidate::id).distinct().size == candidates.size) {
            "candidates must have unique TrackIds"
        }
        require(volumeSignatures.keys.all { it in mountedVolumeNames }) {
            "volumeSignatures must only describe mounted volumes"
        }
        require(volumeSignatures.values.all { it == null || it.isNotBlank() }) {
            "volume signatures must be null or non-blank"
        }
    }
}

fun interface MediaLibraryScanSource {
    @Throws(Exception::class)
    suspend fun queryMountedAudio(): CompleteMediaLibraryScan
}

data class MediaStoreSnapshot(
    val mountedVolumeNames: Set<String>,
    val volumeSignatures: Map<String, String?>,
) {
    init {
        require(mountedVolumeNames.none(String::isBlank))
        require(volumeSignatures.keys == mountedVolumeNames)
        require(volumeSignatures.values.all { it == null || it.isNotBlank() })
    }
}

fun interface MediaStoreSnapshotSource {
    @Throws(Exception::class)
    fun currentSnapshot(): MediaStoreSnapshot
}

fun interface MediaStoreChangeSource {
    fun changes(): Flow<Unit>
}

data class MediaLibraryCacheSnapshot(
    val hasSuccessfulScan: Boolean,
    val mountedVolumeSignatures: Map<String, String?>,
)

interface MediaLibrarySynchronizer {
    suspend fun synchronize(
        mode: MediaLibrarySyncMode,
        source: MediaLibraryScanSource,
    ): MediaLibrarySyncResult

    suspend fun cacheSnapshot(): MediaLibraryCacheSnapshot
}

data class SyncReport(
    val generation: Long,
    val upsertedTrackCount: Int,
    val removedTrackCount: Int,
    val temporarilyUnavailableVolumeNames: Set<String>,
    val failure: MediaLibrarySyncFailure? = null,
    val scanSummary: MediaLibraryScanSummary? = null,
) {
    val succeeded: Boolean = failure == null
}

sealed interface LibrarySyncState {
    val hasSuccessfulScan: Boolean
    val pendingFeedback: PendingLibrarySyncFeedback?

    data class Idle(
        override val hasSuccessfulScan: Boolean,
        override val pendingFeedback: PendingLibrarySyncFeedback? = null,
    ) : LibrarySyncState

    data class Syncing(
        override val hasSuccessfulScan: Boolean,
        val trigger: MediaLibrarySyncTrigger,
        override val pendingFeedback: PendingLibrarySyncFeedback? = null,
    ) : LibrarySyncState

    data class Failed(
        override val hasSuccessfulScan: Boolean,
        val trigger: MediaLibrarySyncTrigger,
        val failure: MediaLibrarySyncFailure,
        override val pendingFeedback: PendingLibrarySyncFeedback? = null,
    ) : LibrarySyncState
}

data class PendingLibrarySyncFeedback(
    val eventId: Long,
    val event: LibrarySyncEvent,
)

sealed interface LibrarySyncEvent {
    val trigger: MediaLibrarySyncTrigger
    val feedback: MediaLibrarySyncFeedback

    data class Completed(
        override val trigger: MediaLibrarySyncTrigger,
        override val feedback: MediaLibrarySyncFeedback,
        val result: MediaLibrarySyncResult,
    ) : LibrarySyncEvent

    data class Failed(
        override val trigger: MediaLibrarySyncTrigger,
        override val feedback: MediaLibrarySyncFeedback,
        val failure: MediaLibrarySyncFailure,
    ) : LibrarySyncEvent
}

typealias MediaLibrarySyncResult = SyncReport

fun MediaAudioCandidate.scanResultTitle(): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val normalizedDisplayName = displayName.trim()
    if (normalizedDisplayName.isNotEmpty()) {
        return normalizedDisplayName.substringBeforeLast('.').ifBlank { normalizedDisplayName }
    }
    return mediaStoreId.toString()
}
