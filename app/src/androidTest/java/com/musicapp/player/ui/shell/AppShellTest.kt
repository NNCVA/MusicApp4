package com.musicapp.player.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.musicapp.player.AlbumsRoute
import com.musicapp.player.MainNavigation
import com.musicapp.player.R
import com.musicapp.player.TracksRoute
import com.musicapp.player.TopLevelRoute
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.data.settings.AppSettings
import com.musicapp.player.data.settings.ColorSource
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.ui.main.MainScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppShellTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun navigationUsesExactCompactMediumAndExpandedPresentations() {
    var windowSizeClass by mutableStateOf(MusicWindowSizeClass.Compact)
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false, windowSizeClass = windowSizeClass) {
        Box(Modifier.width(400.dp)) {
          AdaptiveNavigationShell(
            windowSizeClass = windowSizeClass,
            selectedRoute = TracksRoute,
            onSelectRoute = {},
          ) { Box(Modifier.fillMaxSize()) }
        }
      }
    }
    composeRule.onNodeWithTag(COMPACT_NAVIGATION_TAG).assertWidthIsEqualTo(200.dp)

    composeRule.runOnIdle { windowSizeClass = MusicWindowSizeClass.Medium }
    composeRule.onNodeWithTag(MEDIUM_NAVIGATION_TAG).assertExists()

    composeRule.runOnIdle { windowSizeClass = MusicWindowSizeClass.Expanded }
    composeRule.onNodeWithTag(EXPANDED_NAVIGATION_TAG).assertWidthIsEqualTo(256.dp)
  }

  @Test
  fun runtimeWidthChangeKeepsSelectedTopLevelStack() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val openNavigation = context.getString(R.string.action_open_navigation)
    val albums = context.getString(R.string.nav_albums)
    var width by mutableStateOf(400.dp)

    composeRule.setContent {
      Box(Modifier.requiredWidth(width)) {
        MainScreen(settings = AppSettings(colorSource = ColorSource.PRESET))
      }
    }
    composeRule.onNodeWithText(openNavigation).performClick()
    composeRule.onNodeWithText(albums).performClick()

    composeRule.runOnIdle { width = 610.dp }

    composeRule.onNodeWithTag(MEDIUM_NAVIGATION_TAG).assertExists()
    composeRule.onNodeWithTag("root_albums").assertExists()
  }

  @Test
  fun savedInstanceStateRestoresSelectedTopLevel() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val albums = context.getString(R.string.nav_albums)
    val restorationTester = StateRestorationTester(composeRule)

    restorationTester.setContent {
      MusicAppTheme(dynamicColor = false, windowSizeClass = MusicWindowSizeClass.Medium) {
        MainNavigation(windowSizeClass = MusicWindowSizeClass.Medium)
      }
    }
    composeRule.onNodeWithText(albums, useUnmergedTree = true).performClick()

    restorationTester.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag("root_albums").assertExists()
  }

  @Test
  fun selectingNavigationItemReportsItsRoute() {
    var selected: TopLevelRoute = TracksRoute
    composeRule.setContent {
      MusicAppTheme(dynamicColor = false, windowSizeClass = MusicWindowSizeClass.Medium) {
        AdaptiveNavigationShell(
          windowSizeClass = MusicWindowSizeClass.Medium,
          selectedRoute = selected,
          onSelectRoute = { selected = it },
        ) { Box(Modifier.fillMaxSize().testTag("content")) }
      }
    }

    composeRule.onNodeWithText(
      InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.nav_albums),
      useUnmergedTree = true,
    ).performClick()
    composeRule.runOnIdle { assertEquals(AlbumsRoute, selected) }
  }
}
