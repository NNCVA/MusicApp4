package com.musicapp.player.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class MusicThemePreset {
  Dynamic,
  DefaultBlue,
  EmeraldGreen,
  SunsetOrange,
  Violet,
}

fun resolveThemePreset(requested: MusicThemePreset, sdkInt: Int): MusicThemePreset =
  if (requested == MusicThemePreset.Dynamic && sdkInt < 31) MusicThemePreset.DefaultBlue else requested

private data class AccentPalette(
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
  AccentPalette(
    primary = Color(0xFF005AC1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF28132E),
  )

private val BlueDark =
  AccentPalette(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
  )

private val EmeraldLight =
  AccentPalette(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4D6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF0A1F17),
    tertiary = Color(0xFF3D6473),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E9FB),
    onTertiaryContainer = Color(0xFF001F29),
  )

private val EmeraldDark =
  AccentPalette(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF20352B),
    secondaryContainer = Color(0xFF364B40),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA5CDDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E9FB),
  )

private val SunsetLight =
  AccentPalette(
    primary = Color(0xFFA43B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF2C160F),
    tertiary = Color(0xFF685F30),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E3A8),
    onTertiaryContainer = Color(0xFF211C00),
  )

private val SunsetDark =
  AccentPalette(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF582000),
    primaryContainer = Color(0xFF7D2D00),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE7BEAD),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFD4C78E),
    onTertiary = Color(0xFF383005),
    tertiaryContainer = Color(0xFF50471B),
    onTertiaryContainer = Color(0xFFF1E3A8),
  )

private val VioletLight =
  AccentPalette(
    primary = Color(0xFF6F43C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF260059),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101D),
  )

private val VioletDark =
  AccentPalette(
    primary = Color(0xFFD4BBFF),
    onPrimary = Color(0xFF3F008D),
    primaryContainer = Color(0xFF5727A6),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E2),
  )

fun presetColorScheme(preset: MusicThemePreset, darkTheme: Boolean): ColorScheme {
  val effectivePreset = if (preset == MusicThemePreset.Dynamic) MusicThemePreset.DefaultBlue else preset
  val (lightAccent, darkAccent) =
    when (effectivePreset) {
      MusicThemePreset.DefaultBlue -> BlueLight to BlueDark
      MusicThemePreset.EmeraldGreen -> EmeraldLight to EmeraldDark
      MusicThemePreset.SunsetOrange -> SunsetLight to SunsetDark
      MusicThemePreset.Violet -> VioletLight to VioletDark
      MusicThemePreset.Dynamic -> error("Dynamic preset is resolved before palette selection")
    }
  return if (darkTheme) darkScheme(darkAccent, lightAccent.primary) else lightScheme(lightAccent, darkAccent.primary)
}

private fun lightScheme(accent: AccentPalette, inversePrimary: Color): ColorScheme =
  lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    tertiaryContainer = accent.tertiaryContainer,
    onTertiaryContainer = accent.onTertiaryContainer,
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = accent.primary,
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainer = Color(0xFFEEEEF5),
    surfaceContainerHigh = Color(0xFFE8E8EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color.White,
  )

private fun darkScheme(accent: AccentPalette, inversePrimary: Color): ColorScheme =
  darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = accent.tertiary,
    onTertiary = accent.onTertiary,
    tertiaryContainer = accent.tertiaryContainer,
    onTertiaryContainer = accent.onTertiaryContainer,
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = accent.primary,
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainerLowest = Color(0xFF0C0E13),
  )
