package com.musicapp.player.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.musicapp.player.core.domain.model.PresetTheme

private data class PresetPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

private val BlueLight =
    PresetPalette(
        primary = Color(0xFF0061A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),
        secondary = Color(0xFF535F70),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E3F7),
        onSecondaryContainer = Color(0xFF101C2B),
        tertiary = Color(0xFF6B5778),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF2DAFF),
        onTertiaryContainer = Color(0xFF251431),
    )

private val BlueDark =
    PresetPalette(
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4858),
        onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = Color(0xFFD6BEE4),
        onTertiary = Color(0xFF3B2948),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF2DAFF),
    )

private val EmeraldLight =
    PresetPalette(
        primary = Color(0xFF006C4C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF89F8C7),
        onPrimaryContainer = Color(0xFF002116),
        secondary = Color(0xFF4D6358),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCFE9DA),
        onSecondaryContainer = Color(0xFF092018),
        tertiary = Color(0xFF3D6473),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC1E9FB),
        onTertiaryContainer = Color(0xFF001F29),
    )

private val EmeraldDark =
    PresetPalette(
        primary = Color(0xFF6CDBAC),
        onPrimary = Color(0xFF003828),
        primaryContainer = Color(0xFF00513A),
        onPrimaryContainer = Color(0xFF89F8C7),
        secondary = Color(0xFFB3CCBE),
        onSecondary = Color(0xFF1F352B),
        secondaryContainer = Color(0xFF354B41),
        onSecondaryContainer = Color(0xFFCFE9DA),
        tertiary = Color(0xFFA5CDDF),
        onTertiary = Color(0xFF073542),
        tertiaryContainer = Color(0xFF244C5A),
        onTertiaryContainer = Color(0xFFC1E9FB),
    )

private val SunsetLight =
    PresetPalette(
        primary = Color(0xFF9A4521),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCC),
        onPrimaryContainer = Color(0xFF351000),
        secondary = Color(0xFF77574B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBCC),
        onSecondaryContainer = Color(0xFF2C160E),
        tertiary = Color(0xFF685F30),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E39A),
        onTertiaryContainer = Color(0xFF211C00),
    )

private val SunsetDark =
    PresetPalette(
        primary = Color(0xFFFFB596),
        onPrimary = Color(0xFF572000),
        primaryContainer = Color(0xFF7A2F0B),
        onPrimaryContainer = Color(0xFFFFDBCC),
        secondary = Color(0xFFE7BDAE),
        onSecondary = Color(0xFF442A20),
        secondaryContainer = Color(0xFF5D4035),
        onSecondaryContainer = Color(0xFFFFDBCC),
        tertiary = Color(0xFFD3C77F),
        onTertiary = Color(0xFF383306),
        tertiaryContainer = Color(0xFF504A1B),
        onTertiaryContainer = Color(0xFFF0E39A),
    )

private val VioletLight =
    PresetPalette(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D),
    )

private val VioletDark =
    PresetPalette(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
    )

internal fun presetColorScheme(preset: PresetTheme, darkTheme: Boolean): ColorScheme {
    val palette =
        when (preset) {
            PresetTheme.DEFAULT_BLUE -> if (darkTheme) BlueDark else BlueLight
            PresetTheme.EMERALD_GREEN -> if (darkTheme) EmeraldDark else EmeraldLight
            PresetTheme.SUNSET_ORANGE -> if (darkTheme) SunsetDark else SunsetLight
            PresetTheme.VIOLET -> if (darkTheme) VioletDark else VioletLight
        }
    return if (darkTheme) palette.darkScheme() else palette.lightScheme()
}

private fun PresetPalette.lightScheme(): ColorScheme =
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFDFE2EB),
        onSurfaceVariant = Color(0xFF43474E),
        surfaceTint = primary,
        inverseSurface = Color(0xFF2E3035),
        inverseOnSurface = Color(0xFFF0F0F7),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF73777F),
        outlineVariant = Color(0xFFC3C7CF),
        scrim = Color.Black,
        surfaceBright = Color(0xFFF9F9FF),
        surfaceDim = Color(0xFFD9D9E0),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF3F3FA),
        surfaceContainer = Color(0xFFEDEDF4),
        surfaceContainerHigh = Color(0xFFE7E8EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
        primaryFixed = primaryContainer,
        primaryFixedDim = primary,
        onPrimaryFixed = onPrimaryContainer,
        onPrimaryFixedVariant = primary,
        secondaryFixed = secondaryContainer,
        secondaryFixedDim = secondary,
        onSecondaryFixed = onSecondaryContainer,
        onSecondaryFixedVariant = secondary,
        tertiaryFixed = tertiaryContainer,
        tertiaryFixedDim = tertiary,
        onTertiaryFixed = onTertiaryContainer,
        onTertiaryFixedVariant = tertiary,
    )

private fun PresetPalette.darkScheme(): ColorScheme =
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF43474E),
        onSurfaceVariant = Color(0xFFC3C7CF),
        surfaceTint = primary,
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3035),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8D9199),
        outlineVariant = Color(0xFF43474E),
        scrim = Color.Black,
        surfaceBright = Color(0xFF37393E),
        surfaceDim = Color(0xFF111318),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
        primaryFixed = onPrimaryContainer,
        primaryFixedDim = primary,
        onPrimaryFixed = onPrimary,
        onPrimaryFixedVariant = primaryContainer,
        secondaryFixed = onSecondaryContainer,
        secondaryFixedDim = secondary,
        onSecondaryFixed = onSecondary,
        onSecondaryFixedVariant = secondaryContainer,
        tertiaryFixed = onTertiaryContainer,
        tertiaryFixedDim = tertiary,
        onTertiaryFixed = onTertiary,
        onTertiaryFixedVariant = tertiaryContainer,
    )
