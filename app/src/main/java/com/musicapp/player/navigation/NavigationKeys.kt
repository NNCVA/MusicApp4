package com.musicapp.player.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MusicNavKey : NavKey

@Serializable
sealed interface TopLevelNavKey : MusicNavKey

@Serializable
@SerialName("tracks")
data object TracksRoute : TopLevelNavKey

@Serializable
@SerialName("albums")
data object AlbumsRoute : TopLevelNavKey

@Serializable
@SerialName("artists")
data object ArtistsRoute : TopLevelNavKey

@Serializable
@SerialName("playlists")
data object PlaylistsRoute : TopLevelNavKey

@Serializable
@SerialName("history")
data object HistoryRoute : TopLevelNavKey

@Serializable
@SerialName("folders")
data object FoldersRoute : TopLevelNavKey

@Serializable
@SerialName("settings")
data object SettingsRoute : TopLevelNavKey

@Serializable
@SerialName("about")
data object AboutRoute : TopLevelNavKey

@Serializable
@SerialName("scan_music")
data object ScanMusicRoute : TopLevelNavKey

@Serializable
@SerialName("track_info")
data class TrackInfoRoute(
    val volumeName: String,
    val mediaStoreId: Long,
) : MusicNavKey {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(mediaStoreId > 0) { "mediaStoreId must be positive" }
    }
}

@Serializable
@SerialName("album_detail")
data class AlbumDetailRoute(
    val volumeName: String,
    val mediaStoreId: Long,
) : MusicNavKey {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(mediaStoreId > 0) { "mediaStoreId must be positive" }
    }
}

@Serializable
@SerialName("artist_detail")
data class ArtistDetailRoute(val artistName: String) : MusicNavKey {
    init {
        require(artistName.isNotBlank()) { "artistName must not be blank" }
    }
}

@Serializable
@SerialName("playlist_detail")
data class PlaylistDetailRoute(val playlistId: Long) : MusicNavKey {
    init {
        require(playlistId > 0) { "playlistId must be positive" }
    }
}

@Serializable
@SerialName("folder_detail")
data class FolderDetailRoute(
    val volumeName: String,
    val relativePath: String,
) : MusicNavKey {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(
            relativePath.isEmpty() ||
                (!relativePath.startsWith('/') && !relativePath.endsWith('/') &&
                    "//" !in relativePath && '\\' !in relativePath),
        ) {
            "relativePath must be a normalized directory path without a trailing slash"
        }
    }
}

val topLevelNavKeys: List<TopLevelNavKey> =
    listOf(
        TracksRoute,
        AlbumsRoute,
        ArtistsRoute,
        PlaylistsRoute,
        HistoryRoute,
        FoldersRoute,
        ScanMusicRoute,
        SettingsRoute,
        AboutRoute,
    )

val homeTopLevelNavKeys: List<TopLevelNavKey> =
    listOf(
        TracksRoute,
        AlbumsRoute,
        ArtistsRoute,
        FoldersRoute,
        PlaylistsRoute,
    )

internal fun MusicNavKey.owner(): TopLevelNavKey =
    when (this) {
        is TopLevelNavKey -> this
        is TrackInfoRoute -> TracksRoute
        is AlbumDetailRoute -> AlbumsRoute
        is ArtistDetailRoute -> ArtistsRoute
        is PlaylistDetailRoute -> PlaylistsRoute
        is FolderDetailRoute -> FoldersRoute
    }
