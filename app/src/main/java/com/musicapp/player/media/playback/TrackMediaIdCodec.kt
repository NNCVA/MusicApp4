package com.musicapp.player.media.playback

import com.musicapp.player.core.domain.model.TrackId

internal object TrackMediaIdCodec {
    private const val VERSION_PREFIX = "1:"

    fun encode(trackId: TrackId): String =
        "$VERSION_PREFIX${trackId.volumeName.length}:${trackId.volumeName}${trackId.mediaStoreId}"

    fun decode(mediaId: String): TrackId? {
        if (!mediaId.startsWith(VERSION_PREFIX)) return null
        val lengthSeparator = mediaId.indexOf(':', VERSION_PREFIX.length)
        if (lengthSeparator < 0) return null
        val volumeLength = mediaId.substring(VERSION_PREFIX.length, lengthSeparator).toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val volumeStart = lengthSeparator + 1
        val idStart = volumeStart + volumeLength
        if (idStart >= mediaId.length) return null
        val volumeName = mediaId.substring(volumeStart, idStart)
        val mediaStoreId = mediaId.substring(idStart).toLongOrNull()?.takeIf { it > 0 } ?: return null
        return runCatching { TrackId(volumeName, mediaStoreId) }.getOrNull()
    }
}
