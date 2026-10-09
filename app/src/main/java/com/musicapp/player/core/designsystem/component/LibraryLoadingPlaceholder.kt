package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.musicapp.player.theme.MusicTheme

/** Keeps Room-backed pages on a solid canvas until their first projection is ready. */
@Composable
fun Modifier.libraryLoadingBackground(isLoaded: Boolean): Modifier =
    if (isLoaded) this else background(MusicTheme.colors.background)
