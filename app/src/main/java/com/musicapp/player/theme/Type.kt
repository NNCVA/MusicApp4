package com.musicapp.player.theme

import androidx.compose.material3.Typography
import com.musicapp.player.core.designsystem.MusicTypography

internal fun materialTypography(tokens: MusicTypography): Typography =
  Typography(
    titleLarge = tokens.cardTitle,
    titleMedium = tokens.trackTitle,
    bodyLarge = tokens.cardArtist,
    bodyMedium = tokens.trackArtist,
    labelLarge = tokens.selectionTitle,
  )
