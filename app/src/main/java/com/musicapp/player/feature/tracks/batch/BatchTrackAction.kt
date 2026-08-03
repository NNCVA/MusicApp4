package com.musicapp.player.feature.tracks.batch

import com.musicapp.player.core.domain.model.PlaylistId

sealed interface BatchTrackAction {
    data class AddToPlaylist(val playlistId: PlaylistId) : BatchTrackAction

    data object AddToQueue : BatchTrackAction

    data object PlayNext : BatchTrackAction

    data object Hide : BatchTrackAction
}

sealed interface BatchTrackActionResult {
    data object EmptySelection : BatchTrackActionResult

    data class Completed(
        val action: BatchTrackAction,
        val selectedCount: Int,
        val affectedCount: Int,
        val skippedCount: Int,
    ) : BatchTrackActionResult {
        init {
            require(selectedCount > 0) { "selectedCount must be positive" }
            require(affectedCount >= 0) { "affectedCount must not be negative" }
            require(skippedCount >= 0) { "skippedCount must not be negative" }
            require(affectedCount + skippedCount == selectedCount) {
                "affectedCount and skippedCount must account for every selected track"
            }
        }
    }

    data class Failed(
        val action: BatchTrackAction,
        val selectedCount: Int,
    ) : BatchTrackActionResult {
        init {
            require(selectedCount > 0) { "selectedCount must be positive" }
        }
    }
}
