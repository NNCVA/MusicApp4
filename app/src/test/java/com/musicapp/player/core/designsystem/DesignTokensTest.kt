package com.musicapp.player.core.designsystem

import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignTokensTest {
  @Test
  fun windowBreakpointsMatchAcceptedRanges() {
    assertEquals(MusicWindowSizeClass.Compact, MusicWindowSizeClass.fromWidthDp(599))
    assertEquals(MusicWindowSizeClass.Medium, MusicWindowSizeClass.fromWidthDp(600))
    assertEquals(MusicWindowSizeClass.Medium, MusicWindowSizeClass.fromWidthDp(839))
    assertEquals(MusicWindowSizeClass.Expanded, MusicWindowSizeClass.fromWidthDp(840))
  }

  @Test
  fun dimensionsMatchEachWindowClass() {
    val compact = musicDimensions(MusicWindowSizeClass.Compact)
    val medium = musicDimensions(MusicWindowSizeClass.Medium)
    val expanded = musicDimensions(MusicWindowSizeClass.Expanded)

    assertEquals(16.dp, compact.horizontalPadding)
    assertEquals(24.dp, medium.horizontalPadding)
    assertEquals(32.dp, expanded.horizontalPadding)
    assertEquals(12.dp, compact.cardSpacing)
    assertEquals(16.dp, medium.cardSpacing)
    assertEquals(20.dp, expanded.cardSpacing)
    assertEquals(80.dp, compact.trackRowHeight)
    assertEquals(80.dp, compact.miniPlayerHeight)
    assertEquals(160.dp, compact.minimumGridCardWidth)
  }

  @Test
  fun typographyUsesScalableSpAndAcceptedSizes() {
    val compact = musicTypography(MusicWindowSizeClass.Compact)
    val expanded = musicTypography(MusicWindowSizeClass.Expanded)

    assertEquals(TextUnitType.Sp, compact.trackTitle.fontSize.type)
    assertEquals(16.sp, compact.trackTitle.fontSize)
    assertEquals(12.sp, compact.trackArtist.fontSize)
    assertEquals(20.sp, compact.cardTitle.fontSize)
    assertEquals(18.sp, expanded.trackTitle.fontSize)
    assertEquals(14.sp, expanded.trackArtist.fontSize)
    assertEquals(22.sp, expanded.cardTitle.fontSize)
  }
}
