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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ThemeMode

private val LocalMusicDimensions = staticCompositionLocalOf { MusicDimensions.Compact }
private val LocalMusicShapes = staticCompositionLocalOf { DefaultMusicShapes }
private val LocalMusicTypography = staticCompositionLocalOf { DefaultMusicTypography }
private val LocalAeroCardTransparencyEnabled = staticCompositionLocalOf { false }

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

    /** Container color for persistent page cards that intentionally reveal the active Aero background. */
    val aeroCardContainerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = resolveAeroCardContainerColor(
            surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
            transparencyEnabled = LocalAeroCardTransparencyEnabled.current,
        )
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
    val dimensions = MusicDimensions.forTier(windowWidthTier)
    val shapes = DefaultMusicShapes
    val typography = DefaultMusicTypography

    CompositionLocalProvider(
        LocalMusicDimensions provides dimensions,
        LocalMusicShapes provides shapes,
        LocalMusicTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = baseColorScheme,
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

const val AERO_CARD_CONTAINER_ALPHA = 0.5f

@Composable
internal fun ProvideAeroCardTransparency(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAeroCardTransparencyEnabled provides enabled,
        content = content,
    )
}

internal fun resolveAeroCardContainerColor(
    surfaceContainer: Color,
    transparencyEnabled: Boolean,
): Color = surfaceContainer.copy(alpha = if (transparencyEnabled) AERO_CARD_CONTAINER_ALPHA else 1f)
