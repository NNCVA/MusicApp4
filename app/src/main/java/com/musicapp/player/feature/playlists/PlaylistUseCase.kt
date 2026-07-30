package com.musicapp.player.feature.playlists

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.PlaylistTrackChangeResult
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class PlaylistName(
    val displayName: String,
    val normalizedName: String,
)

object PlaylistNameNormalizer {
    fun normalize(rawName: String): PlaylistName {
        val displayName = Normalizer.normalize(rawName.trim(), Normalizer.Form.NFC)
        require(displayName.isNotBlank()) { "playlist name must not be blank" }
        require(displayName.codePointCount(0, displayName.length) <= MAX_PLAYLIST_NAME_CODE_POINTS) {
            "playlist name must contain at most $MAX_PLAYLIST_NAME_CODE_POINTS Unicode code points"
        }
        return PlaylistName(
            displayName = displayName,
            normalizedName = displayName.lowercase(Locale.ROOT),
        )
    }
}

private const val MAX_PLAYLIST_NAME_CODE_POINTS = 50

class PlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository,
    private val clock: Clock,
) {
    suspend fun create(rawName: String): PlaylistId {
        val name = PlaylistNameNormalizer.normalize(rawName)
        return repository.createPlaylist(name.displayName, name.normalizedName, clock.currentTimeMillis())
    }

    suspend fun rename(playlistId: PlaylistId, rawName: String) {
        val name = PlaylistNameNormalizer.normalize(rawName)
        repository.renamePlaylist(
            playlistId = playlistId,
            displayName = name.displayName,
            normalizedName = name.normalizedName,
            updatedAtMs = clock.currentTimeMillis(),
        )
    }

    suspend fun delete(playlistId: PlaylistId) = repository.deletePlaylist(playlistId)

    suspend fun addTracks(playlistId: PlaylistId, trackIds: List<TrackId>): PlaylistTrackChangeResult =
        repository.addTracks(playlistId, trackIds, clock.currentTimeMillis())

    suspend fun removeTracks(playlistId: PlaylistId, trackIds: List<TrackId>): PlaylistTrackChangeResult =
        repository.removeTracks(playlistId, trackIds, clock.currentTimeMillis())
}
