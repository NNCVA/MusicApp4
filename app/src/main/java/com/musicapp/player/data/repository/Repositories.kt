package com.musicapp.player.data.repository

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import kotlinx.coroutines.flow.Flow

interface MediaLibraryRepository {
    fun observeTracks(includeHidden: Boolean = false): Flow<List<Track>>
    fun observeAlbumTracks(albumId: AlbumId): Flow<List<Track>>
    fun observeArtistTracks(artistId: ArtistId): Flow<List<Track>>
    fun observeFolderTracks(volumeName: String, directoryPath: String): Flow<List<Track>>
    suspend fun getTrack(trackId: TrackId): Track?
    suspend fun mergeTracks(tracks: List<Track>)
    suspend fun replaceTracksForVolume(volumeName: String, tracks: List<Track>)
    suspend fun setVolumeAvailability(volumeName: String, availability: Availability)
    suspend fun setHidden(trackId: TrackId, hidden: Boolean, changedAtMs: Long)
    fun observePathRules(): Flow<List<PathRule>>
    suspend fun addPathRule(volumeName: String, directory: String, kind: PathRuleKind): PathRule
    suspend fun replacePathRules(rules: List<PathRule>)
    suspend fun removePathRule(ruleId: PathRuleId)
    suspend fun clearPathRules()
}

internal fun normalizeFolderDirectoryPath(path: String): String {
    val segments = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylist(playlistId: PlaylistId): Flow<Playlist?>
    suspend fun createPlaylist(
        displayName: String,
        normalizedName: String,
        createdAtMs: Long,
    ): PlaylistId
    suspend fun renamePlaylist(
        playlistId: PlaylistId,
        displayName: String,
        normalizedName: String,
        updatedAtMs: Long,
    )
    suspend fun deletePlaylist(playlistId: PlaylistId)
    suspend fun addTracks(playlistId: PlaylistId, trackIds: List<TrackId>, updatedAtMs: Long)
    suspend fun replaceTracks(playlistId: PlaylistId, trackIds: List<TrackId>, updatedAtMs: Long)
    suspend fun clearTracks(playlistId: PlaylistId, updatedAtMs: Long)
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<PlayHistory>>
    suspend fun recordPlayback(trackId: TrackId, playedAtMs: Long)
    suspend fun clearHistory()
}

interface PlaybackSnapshotRepository {
    fun observeSnapshot(): Flow<PlaybackSnapshot?>
    suspend fun getSnapshot(): PlaybackSnapshot?
    suspend fun saveSnapshot(snapshot: PlaybackSnapshot)
    suspend fun clearSnapshot()
}
