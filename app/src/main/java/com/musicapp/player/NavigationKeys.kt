package com.musicapp.player

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface MusicNavKey : NavKey

sealed interface TopLevelRoute : MusicNavKey {
  val stableId: String
}

@Serializable
data object TracksRoute : TopLevelRoute {
  override val stableId = "tracks"
}

@Serializable
data object AlbumsRoute : TopLevelRoute {
  override val stableId = "albums"
}

@Serializable
data object ArtistsRoute : TopLevelRoute {
  override val stableId = "artists"
}

@Serializable
data object PlaylistsRoute : TopLevelRoute {
  override val stableId = "playlists"
}

@Serializable
data object HistoryRoute : TopLevelRoute {
  override val stableId = "history"
}

@Serializable
data object FoldersRoute : TopLevelRoute {
  override val stableId = "folders"
}

@Serializable
data object SettingsRoute : TopLevelRoute {
  override val stableId = "settings"
}

@Serializable
data object AboutRoute : TopLevelRoute {
  override val stableId = "about"
}

@Serializable data class AlbumDetailRoute(val volumeName: String, val albumId: Long) : MusicNavKey

@Serializable data class ArtistDetailRoute(val artistId: Long) : MusicNavKey

@Serializable data class PlaylistDetailRoute(val playlistId: String) : MusicNavKey

@Serializable data class FolderDetailRoute(val volumeName: String, val relativePath: String) : MusicNavKey

@Serializable data object FullPlayerRoute : MusicNavKey

val MusicTopLevelRoutes: List<TopLevelRoute> =
  listOf(
    TracksRoute,
    AlbumsRoute,
    ArtistsRoute,
    PlaylistsRoute,
    HistoryRoute,
    FoldersRoute,
    SettingsRoute,
    AboutRoute,
  )

fun topLevelRoute(stableId: String): TopLevelRoute =
  MusicTopLevelRoutes.firstOrNull { it.stableId == stableId } ?: TracksRoute
