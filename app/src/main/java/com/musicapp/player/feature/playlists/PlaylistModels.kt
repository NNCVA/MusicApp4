package com.musicapp.player.feature.playlists

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId

data class PlaylistPlaybackPreparation(
    val context: PlaybackContext?,
    val playedCount: Int,
    val skippedCount: Int,
)

object PlaylistPlaybackContextFactory {
    fun create(
        playlist: Playlist,
        tracks: List<Track>,
        selectedTrackId: TrackId? = null,
    ): PlaybackContext? = prepare(playlist, tracks, selectedTrackId).context

    fun prepare(
        playlist: Playlist,
        tracks: List<Track>,
        selectedTrackId: TrackId? = null,
    ): PlaylistPlaybackPreparation {
        val tracksById = tracks.associateBy(Track::id)
        val orderedTrackIds = playlist.trackIds.filter { trackId ->
            tracksById[trackId]?.availability == Availability.AVAILABLE
        }
        return PlaylistPlaybackPreparation(
            context =
                orderedTrackIds.takeIf { it.isNotEmpty() }?.let {
                    PlaybackContext(
                        source = PlaybackContextSource.PLAYLIST,
                        sourceId = playlist.id.value.toString(),
                        orderedTrackIds = orderedTrackIds,
                        selectedTrackId = selectedTrackId?.takeIf(orderedTrackIds::contains) ?: orderedTrackIds.first(),
                    )
                },
            playedCount = orderedTrackIds.size,
            skippedCount = playlist.trackIds.size - orderedTrackIds.size,
        )
    }
}
