package com.musicapp.player.core.domain.model

@JvmInline
value class PlaylistId(val value: Long) {
    init {
        require(value > 0) { "PlaylistId must be positive" }
    }
}

data class Playlist(
    val id: PlaylistId,
    val displayName: String,
    val normalizedName: String,
    val trackIds: List<TrackId> = emptyList(),
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(displayName == displayName.trim()) {
            "displayName must not have leading or trailing whitespace"
        }
        require(normalizedName.isNotBlank()) { "normalizedName must not be blank" }
        require(trackIds.size == trackIds.distinct().size) { "a playlist must not contain duplicate tracks" }
        require(createdAtMs >= 0) { "createdAtMs must not be negative" }
        require(updatedAtMs >= createdAtMs) { "updatedAtMs must not be earlier than createdAtMs" }
    }
}
