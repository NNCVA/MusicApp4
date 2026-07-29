package com.musicapp.player

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.musicapp.player.data.settings.AppSettings
import com.musicapp.player.data.settings.ColorSource
import com.musicapp.player.ui.main.MainScreen

@Preview(name = "compact_short", widthDp = 400, heightDp = 400, showBackground = true)
@Preview(name = "compact", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(name = "compact_tall", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "medium_short", widthDp = 610, heightDp = 400, showBackground = true)
@Preview(name = "medium", widthDp = 610, heightDp = 500, showBackground = true)
@Preview(name = "medium_tall", widthDp = 610, heightDp = 1000, showBackground = true)
@Preview(name = "expanded_short", widthDp = 900, heightDp = 400, showBackground = true)
@Preview(name = "expanded", widthDp = 900, heightDp = 500, showBackground = true)
@Preview(name = "expanded_tall", widthDp = 900, heightDp = 1000, showBackground = true)
@Preview(
  name = "compact_dark",
  widthDp = 400,
  heightDp = 500,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
)
@Preview(
  name = "compact_large_font",
  widthDp = 400,
  heightDp = 500,
  fontScale = 1.5f,
  showBackground = true,
)
annotation class WaveTwoRootPreviews

@Composable
private fun RootPreview(route: TopLevelRoute) {
  MainScreen(
    settings = AppSettings(colorSource = ColorSource.PRESET),
    initialTopLevel = route,
  )
}

@PreviewTest @WaveTwoRootPreviews @Composable
fun TracksRootScreenshots() = RootPreview(TracksRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun AlbumsRootScreenshots() = RootPreview(AlbumsRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun ArtistsRootScreenshots() = RootPreview(ArtistsRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun PlaylistsRootScreenshots() = RootPreview(PlaylistsRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun HistoryRootScreenshots() = RootPreview(HistoryRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun FoldersRootScreenshots() = RootPreview(FoldersRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun SettingsRootScreenshots() = RootPreview(SettingsRoute)

@PreviewTest @WaveTwoRootPreviews @Composable
fun AboutRootScreenshots() = RootPreview(AboutRoute)
