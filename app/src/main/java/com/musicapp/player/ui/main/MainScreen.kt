package com.musicapp.player.ui.main

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.MainNavigation
import com.musicapp.player.TopLevelRoute
import com.musicapp.player.TracksRoute
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.data.settings.AppSettings
import com.musicapp.player.data.settings.ColorSource
import com.musicapp.player.data.settings.DarkMode
import com.musicapp.player.data.settings.PresetTheme
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicThemePreset

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  settings: AppSettings = AppSettings(),
  initialTopLevel: TopLevelRoute = TracksRoute,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val windowSizeClass = MusicWindowSizeClass.fromWidthDp(maxWidth.value.toInt())
    val dynamicColor = settings.colorSource == ColorSource.SYSTEM_DYNAMIC
    val darkTheme =
      when (settings.darkMode) {
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
      }
    MusicAppTheme(
      darkTheme = darkTheme,
      dynamicColor = dynamicColor,
      themePreset = if (dynamicColor) MusicThemePreset.Dynamic else settings.presetTheme.toMusicThemePreset(),
      windowSizeClass = windowSizeClass,
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
      ) {
        MainNavigation(windowSizeClass = windowSizeClass, initialTopLevel = initialTopLevel)
      }
    }
  }
}

private fun PresetTheme.toMusicThemePreset(): MusicThemePreset =
  when (this) {
    PresetTheme.DEFAULT_BLUE -> MusicThemePreset.DefaultBlue
    PresetTheme.EMERALD_GREEN -> MusicThemePreset.EmeraldGreen
    PresetTheme.SUNSET_ORANGE -> MusicThemePreset.SunsetOrange
    PresetTheme.VIOLET -> MusicThemePreset.Violet
  }

@Preview(name = "Compact", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(name = "Medium", widthDp = 610, heightDp = 500, showBackground = true)
@Preview(name = "Expanded", widthDp = 900, heightDp = 500, showBackground = true)
@Composable
fun MainScreenPreview() {
  MainScreen()
}
