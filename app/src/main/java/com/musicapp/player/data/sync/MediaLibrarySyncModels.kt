package com.musicapp.player.data.sync

import com.musicapp.player.core.media.MediaAudioCandidate

enum class MediaLibrarySyncMode {
    FULL,
    INCREMENTAL,
}

enum class MediaLibrarySyncFailure {
    QUERY_FAILED,
    PERMISSION_LOST,
}

data class CompleteMediaLibraryScan(
    val mountedVolumeNames: Set<String>,
    val candidates: List<MediaAudioCandidate>,
    val volumeSignatures: Map<String, String?> = emptyMap(),
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

data class SyncReport(
    val generation: Long,
    val upsertedTrackCount: Int,
    val removedTrackCount: Int,
    val temporarilyUnavailableVolumeNames: Set<String>,
    val failure: MediaLibrarySyncFailure? = null,
) {
    val succeeded: Boolean = failure == null
}

typealias MediaLibrarySyncResult = SyncReport
