package com.musicapp.player.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.theme.MusicAppTheme

@Composable
fun MainScreen(modifier: Modifier = Modifier) = Box(modifier = modifier)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  MusicAppTheme { MainScreen() }
}
