package com.musicapp.player

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.appcompat.app.AppCompatDelegate
import com.musicapp.player.data.settings.AppLanguage
import com.musicapp.player.data.settings.AppLanguageController
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.ui.shell.OPEN_NAVIGATION_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityLanguageRecreationTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var settingsRepository: SettingsRepository

  @Before
  fun inject() {
    hiltRule.inject()
  }

  @After
  fun restoreSystemLanguage() {
    runBlocking { settingsRepository.setAppLanguage(AppLanguage.FOLLOW_SYSTEM) }
    AppLanguageController.apply(AppLanguage.FOLLOW_SYSTEM)
  }

  @Test
  fun languageRecreationRestoresSelectedNavigationStack() {
    runBlocking { settingsRepository.setAppLanguage(AppLanguage.ENGLISH) }
    composeRule.waitUntil(timeoutMillis = ACTIVITY_RECREATION_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag("root_tracks").fetchSemanticsNodes().isNotEmpty() &&
        AppCompatDelegate.getApplicationLocales().toLanguageTags() == "en"
    }

    if (composeRule.onAllNodesWithTag(OPEN_NAVIGATION_TAG).fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag(OPEN_NAVIGATION_TAG).performClick()
    }
    composeRule.onNodeWithTag("nav_albums").performClick()
    composeRule.onNodeWithTag("root_albums").assertExists()
    val activityBeforeChinese = composeRule.activity

    runBlocking { settingsRepository.setAppLanguage(AppLanguage.SIMPLIFIED_CHINESE) }
    composeRule.waitUntil(timeoutMillis = ACTIVITY_RECREATION_TIMEOUT_MILLIS) {
      composeRule.activity !== activityBeforeChinese
    }

    composeRule.onNodeWithTag("root_albums").assertExists()
    val chineseAlbums = composeRule.activity.getString(R.string.nav_albums)
    composeRule.waitUntil(timeoutMillis = ACTIVITY_RECREATION_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithText(chineseAlbums, useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    }
  }

  private companion object {
    const val ACTIVITY_RECREATION_TIMEOUT_MILLIS = 10_000L
  }
}
