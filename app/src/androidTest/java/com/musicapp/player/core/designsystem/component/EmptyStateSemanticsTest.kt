package com.musicapp.player.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyStateWithoutActionExposesAccessibleTitleAndDescription() {
        composeTestRule.setContent {
            MusicAppTheme {
                EmptyState(
                    title = "Empty Title",
                    description = "Empty Description",
                )
            }
        }

        composeTestRule.onNode(
            hasContentDescription("Empty Title\nEmpty Description"),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Empty Title").assertDoesNotExist()
        composeTestRule.onNodeWithText("Empty Description").assertDoesNotExist()
    }

    @Test
    fun emptyStateWithActionInvokesCallbackOnClick() {
        var clicked = false

        composeTestRule.setContent {
            MusicAppTheme {
                EmptyState(
                    title = "No tracks found",
                    description = "Scan again after adding files.",
                    actionLabel = "Scan music",
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("No tracks found").assertDoesNotExist()
        composeTestRule.onNodeWithText("Scan again after adding files.").assertDoesNotExist()
        val actionButton = composeTestRule.onNodeWithText("Scan music")
        actionButton.assertIsDisplayed()
        actionButton.performClick()

        assertTrue("Expected action callback to be invoked", clicked)
    }
}
