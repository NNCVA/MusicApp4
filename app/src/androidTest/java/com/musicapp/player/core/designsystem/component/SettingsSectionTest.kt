package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersSectionTitleAndContentSlot() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsSection(title = "Appearance") {
                    Text("Content")
                }
            }
        }

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Content").assertIsDisplayed()
    }
}
