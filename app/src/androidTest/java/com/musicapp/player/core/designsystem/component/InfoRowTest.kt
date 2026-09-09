package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfoRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersSupportingAndTrailingContentAndInvokesWholeRowClick() {
        val clicked = mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                InfoRow(
                    title = "Folder",
                    subtitle = "12 tracks",
                    trailingValue = "Value",
                    showChevron = true,
                    onClick = { clicked.value = true },
                    leadingContent = { androidx.compose.material3.Text("Lead") },
                )
            }
        }

        composeTestRule.onNodeWithText("Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Value").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lead").assertIsDisplayed()
        composeTestRule.onNode(hasClickAction() and hasText("Folder")).performClick()
        assertTrue(clicked.value)
    }
}
