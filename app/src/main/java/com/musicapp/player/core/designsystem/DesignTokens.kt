package com.musicapp.player.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MusicWindowSizeClass {
  Compact,
  Medium,
  Expanded,
  ;

  companion object {
    fun fromWidthDp(widthDp: Int): MusicWindowSizeClass =
      when {
        widthDp < 600 -> Compact
        widthDp < 840 -> Medium
        else -> Expanded
      }
  }
}

@Immutable
data class MusicDimensions(
  val horizontalPadding: Dp,
  val cardSpacing: Dp,
  val componentGrid: Dp = 4.dp,
  val minimumTouchTarget: Dp = 48.dp,
  val trackRowHeight: Dp = 80.dp,
  val miniPlayerHeight: Dp = 80.dp,
  val minimumGridCardWidth: Dp = 160.dp,
  val gridCardInfoHeight: Dp = 64.dp,
  val detailDialogMaxWidth: Dp = 640.dp,
  val contentMaxWidth: Dp = 720.dp,
  val permanentNavigationWidth: Dp = 256.dp,
)

@Immutable
data class MusicShapes(
  val artwork: Shape,
  val card: Shape,
  val container: Shape,
  val control: Shape,
)

@Immutable
data class MusicTypography(
  val trackTitle: TextStyle,
  val cardTitle: TextStyle,
  val trackArtist: TextStyle,
  val cardArtist: TextStyle,
  val selectionTitle: TextStyle,
  val stateTitle: TextStyle,
  val stateBody: TextStyle,
)

fun musicDimensions(windowSizeClass: MusicWindowSizeClass): MusicDimensions =
  when (windowSizeClass) {
    MusicWindowSizeClass.Compact -> MusicDimensions(horizontalPadding = 16.dp, cardSpacing = 12.dp)
    MusicWindowSizeClass.Medium -> MusicDimensions(horizontalPadding = 24.dp, cardSpacing = 16.dp)
    MusicWindowSizeClass.Expanded -> MusicDimensions(horizontalPadding = 32.dp, cardSpacing = 20.dp)
  }

fun musicShapes(windowSizeClass: MusicWindowSizeClass): MusicShapes {
  val cardRadius = if (windowSizeClass == MusicWindowSizeClass.Compact) 16.dp else 20.dp
  return MusicShapes(
    artwork = RoundedCornerShape(cardRadius),
    card = RoundedCornerShape(cardRadius),
    container = RoundedCornerShape(cardRadius),
    control = RoundedCornerShape(12.dp),
  )
}

fun musicTypography(windowSizeClass: MusicWindowSizeClass): MusicTypography {
  val isCompact = windowSizeClass == MusicWindowSizeClass.Compact
  return MusicTypography(
    trackTitle =
      TextStyle(
        fontSize = if (isCompact) 16.sp else 18.sp,
        lineHeight = if (isCompact) 20.sp else 22.sp,
        fontWeight = FontWeight.Medium,
      ),
    cardTitle =
      TextStyle(
        fontSize = if (isCompact) 20.sp else 22.sp,
        lineHeight = if (isCompact) 24.sp else 26.sp,
        fontWeight = FontWeight.SemiBold,
      ),
    trackArtist =
      TextStyle(
        fontSize = if (isCompact) 12.sp else 14.sp,
        lineHeight = if (isCompact) 16.sp else 18.sp,
      ),
    cardArtist =
      TextStyle(
        fontSize = if (isCompact) 16.sp else 18.sp,
        lineHeight = if (isCompact) 20.sp else 22.sp,
      ),
    selectionTitle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    stateTitle = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    stateBody = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
  )
}
