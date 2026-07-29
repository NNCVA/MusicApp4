package com.musicapp.player.feature.root

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.musicapp.player.R
import com.musicapp.player.TracksRoute
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeRootScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun allFakePresentationsExposeStableStateSemantics() {
    var state by mutableStateOf(FakeRootState.Loading)
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false) {
        FakeRootScreen(
          route = TracksRoute,
          state = state,
          contentPadding = PaddingValues(),
          onOpenNavigation = null,
          onRetry = {},
        )
      }
    }
    FakeRootState.entries.forEach { presentation ->
      composeRule.runOnIdle { state = presentation }
      composeRule.onNodeWithTag(ROOT_STATE_TAG).assertExists()
    }
  }

  @Test
  fun errorPresentationInvokesRetry() {
    var retried = false
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false) {
        FakeRootScreen(
          route = TracksRoute,
          state = FakeRootState.Error,
          contentPadding = PaddingValues(),
          onOpenNavigation = null,
          onRetry = { retried = true },
        )
      }
    }

    val retry = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_retry)
    composeRule.onNodeWithText(retry).performClick()
    composeRule.runOnIdle { assertTrue(retried) }
  }
}
