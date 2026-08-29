package com.musicapp.player.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ThemeMode

private val LocalMusicDimensions = staticCompositionLocalOf { MusicDimensions.Compact }
private val LocalMusicShapes = staticCompositionLocalOf { DefaultMusicShapes }
private val LocalMusicTypography = staticCompositionLocalOf { DefaultMusicTypography }

object MusicTheme {
    val colors: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val dimensions: MusicDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalMusicDimensions.current

    val shapes: MusicShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalMusicShapes.current

    val typography: MusicTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMusicTypography.current
}

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    presetTheme: PresetTheme = PresetTheme.DEFAULT_BLUE,
    colorSource: ColorSource = ColorSource.DYNAMIC,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    windowWidthTier: MusicWindowWidthTier = MusicWindowWidthTier.COMPACT,
    content: @Composable () -> Unit,
) {
    val useDarkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> darkTheme
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val resolvedPreset =
        resolvePresetTheme(
            colorSource = colorSource,
            presetTheme = presetTheme,
            supportsDynamicColor = supportsDynamicColor,
        )
    val baseColorScheme =
        if (colorSource == ColorSource.DYNAMIC && supportsDynamicColor) {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            presetColorScheme(checkNotNull(resolvedPreset), useDarkTheme)
        }
    val colorScheme = applyTranslucentContainers(baseColorScheme)
    val dimensions = MusicDimensions.forTier(windowWidthTier)
    val shapes = DefaultMusicShapes
    val typography = DefaultMusicTypography

    CompositionLocalProvider(
        LocalMusicDimensions provides dimensions,
        LocalMusicShapes provides shapes,
        LocalMusicTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes.material,
            typography = typography.material,
            content = content,
        )
    }
}

internal fun resolvePresetTheme(
    colorSource: ColorSource,
    presetTheme: PresetTheme,
    supportsDynamicColor: Boolean,
): PresetTheme? =
    when {
        colorSource == ColorSource.DYNAMIC && supportsDynamicColor -> null
        colorSource == ColorSource.DYNAMIC -> PresetTheme.DEFAULT_BLUE
        else -> presetTheme
    }

const val SURFACE_CONTAINER_ALPHA = 0.5f

internal fun applyTranslucentContainers(colorScheme: ColorScheme): ColorScheme =
    colorScheme.copy(
        surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = SURFACE_CONTAINER_ALPHA),
        surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = SURFACE_CONTAINER_ALPHA),
        surfaceContainer = colorScheme.surfaceContainer.copy(alpha = SURFACE_CONTAINER_ALPHA),
        surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = SURFACE_CONTAINER_ALPHA),
        surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = SURFACE_CONTAINER_ALPHA),
    )
