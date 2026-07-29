package com.musicapp.player.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.musicapp.player.core.designsystem.MusicDimensions
import com.musicapp.player.core.designsystem.MusicShapes
import com.musicapp.player.core.designsystem.MusicTypography
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.core.designsystem.musicDimensions
import com.musicapp.player.core.designsystem.musicShapes
import com.musicapp.player.core.designsystem.musicTypography

private val LocalMusicDimensions = staticCompositionLocalOf { musicDimensions(MusicWindowSizeClass.Compact) }
private val LocalMusicShapes = staticCompositionLocalOf { musicShapes(MusicWindowSizeClass.Compact) }
private val LocalMusicTypography = staticCompositionLocalOf { musicTypography(MusicWindowSizeClass.Compact) }
private val LocalMusicWindowSizeClass = staticCompositionLocalOf { MusicWindowSizeClass.Compact }

object MusicTheme {
  val dimensions: MusicDimensions
    @Composable @ReadOnlyComposable get() = LocalMusicDimensions.current

  val shapes: MusicShapes
    @Composable @ReadOnlyComposable get() = LocalMusicShapes.current

  val typography: MusicTypography
    @Composable @ReadOnlyComposable get() = LocalMusicTypography.current

  val windowSizeClass: MusicWindowSizeClass
    @Composable @ReadOnlyComposable get() = LocalMusicWindowSizeClass.current
}

@Composable
fun MusicAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  themePreset: MusicThemePreset = if (dynamicColor) MusicThemePreset.Dynamic else MusicThemePreset.DefaultBlue,
  windowSizeClass: MusicWindowSizeClass = MusicWindowSizeClass.Compact,
  content: @Composable () -> Unit,
) {
  val resolvedPreset = resolveThemePreset(themePreset, Build.VERSION.SDK_INT)
  val context = LocalContext.current
  val colorScheme =
    when {
      resolvedPreset == MusicThemePreset.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      else -> presetColorScheme(resolvedPreset, darkTheme)
    }
  val dimensions = musicDimensions(windowSizeClass)
  val shapes = musicShapes(windowSizeClass)
  val typography = musicTypography(windowSizeClass)

  CompositionLocalProvider(
    LocalMusicDimensions provides dimensions,
    LocalMusicShapes provides shapes,
    LocalMusicTypography provides typography,
    LocalMusicWindowSizeClass provides windowSizeClass,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = materialTypography(typography),
      content = content,
    )
  }
}
