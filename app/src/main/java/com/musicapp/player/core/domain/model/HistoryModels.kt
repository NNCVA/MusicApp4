package com.musicapp.player.core.domain.model

data class PlayHistory(
    val trackId: TrackId,
    val lastPlayedAtMs: Long,
    val playCount: Long,
) {
    init {
        require(lastPlayedAtMs >= 0) { "lastPlayedAtMs must not be negative" }
        require(playCount > 0) { "playCount must be positive" }
    }
}
