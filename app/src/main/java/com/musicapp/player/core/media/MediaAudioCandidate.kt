package com.musicapp.player.core.media

import com.musicapp.player.core.domain.model.TrackId

data class MediaAudioCandidate(
    val volumeName: String,
    val mediaStoreId: Long,
    val title: String? = null,
    val artistName: String? = null,
    val artistId: Long? = null,
    val albumTitle: String? = null,
    val albumId: Long? = null,
    val displayName: String,
    val mimeType: String?,
    val durationMs: Long,
    val dateAddedMs: Long = 0,
    val dateModifiedMs: Long = 0,
    val relativeDirectory: String = "",
    val sizeBytes: Long = 0,
    val isRingtone: Boolean = false,
    val isAlarm: Boolean = false,
    val isNotification: Boolean = false,
    val isRecording: Boolean = false,
    val isPodcast: Boolean = false,
    val isAudiobook: Boolean = false,
) {
    val id: TrackId = TrackId(volumeName = volumeName, mediaStoreId = mediaStoreId)
}
