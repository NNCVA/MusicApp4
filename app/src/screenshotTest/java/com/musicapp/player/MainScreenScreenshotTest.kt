package com.musicapp.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.ui.main.MainScreen

@PreviewTest
@Preview(name = "wave0_compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
fun MainScreenWave0Screenshot() {
  MusicAppTheme(dynamicColor = false) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      MainScreen()
    }
  }
}
