package com.musicapp.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SharedComponentsTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun trackRowKeepsSharedHeightAtFontScaleOnePointFive() {
    composeRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
        MusicAppTheme(dynamicColor = false, windowSizeClass = MusicWindowSizeClass.Compact) {
          TrackRow(
            title = "A long track title that must remain bounded to two lines",
            artist = "Artist name that must stay on one line",
            onClick = {},
            modifier = Modifier.testTag("track_row"),
          )
        }
      }
    }

    composeRule.onNodeWithTag("track_row").assertHeightIsEqualTo(80.dp)
    composeRule.onNodeWithText("A long track title that must remain bounded to two lines").assertExists()
  }

  @Test
  fun miniPlayerExposesCallbacksAndSharedHeight() {
    var opened = false
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false) {
        MiniPlayerPlaceholder(
          state = MiniPlayerState(title = "Title", artist = "Artist", isPlaying = false),
          onOpenPlayer = { opened = true },
          onPlayPause = {},
          onNext = {},
          modifier = Modifier.testTag("mini_player"),
        )
      }
    }

    composeRule.onNodeWithTag("mini_player").assertHeightIsEqualTo(80.dp).performClick()
    composeRule.runOnIdle { assertTrue(opened) }
  }

  @Test
  fun errorStateInvokesRetry() {
    var retried = false
    val retryLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_retry)
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false) {
        Box(Modifier.width(400.dp)) {
          MusicErrorState(titleRes = R.string.state_error_title, onRetry = { retried = true })
        }
      }
    }

    composeRule.onNodeWithText(retryLabel).performClick()
    composeRule.runOnIdle { assertTrue(retried) }
  }
}
