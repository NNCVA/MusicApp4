package com.musicapp.player.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MusicThemeTest {
  @Test
  fun dynamicColorFallsBackToBlueBelowApi31() {
    assertEquals(MusicThemePreset.DefaultBlue, resolveThemePreset(MusicThemePreset.Dynamic, 30))
    assertEquals(MusicThemePreset.Dynamic, resolveThemePreset(MusicThemePreset.Dynamic, 31))
  }

  @Test
  fun explicitPresetIsStableAcrossApiLevels() {
    assertEquals(MusicThemePreset.EmeraldGreen, resolveThemePreset(MusicThemePreset.EmeraldGreen, 26))
    assertEquals(MusicThemePreset.SunsetOrange, resolveThemePreset(MusicThemePreset.SunsetOrange, 36))
  }

  @Test
  fun everyPresetHasDistinctLightAndDarkPalettes() {
    val presets =
      listOf(
        MusicThemePreset.DefaultBlue,
        MusicThemePreset.EmeraldGreen,
        MusicThemePreset.SunsetOrange,
        MusicThemePreset.Violet,
      )

    assertEquals(presets.size, presets.map { presetColorScheme(it, false).primary }.distinct().size)
    presets.forEach { preset ->
      val light = presetColorScheme(preset, false)
      val dark = presetColorScheme(preset, true)
      assertNotEquals(light.primary, dark.primary)
      assertNotEquals(light.surface, dark.surface)
      assertNotEquals(light.onSurface, dark.onSurface)
    }
  }
}
