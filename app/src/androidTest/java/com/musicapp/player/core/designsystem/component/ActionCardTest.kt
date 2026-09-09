package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun normalSuccessAndWarningStatesRenderAndNormalCardClicks() {
        val clicked = mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    ActionCard(
                        title = "Normal",
                        iconResId = R.drawable.ic_sidebar_scan,
                        onClick = { clicked.value = true },
                    )
                    ActionCard(
                        title = "Success",
                        status = ActionCardStatus.Success,
                    )
                    ActionCard(
                        title = "Warning",
                        status = ActionCardStatus.Warning,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Normal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Success").assertIsDisplayed()
        composeTestRule.onNodeWithText("Warning").assertIsDisplayed()
        composeTestRule.onNode(hasClickAction() and androidx.compose.ui.test.hasText("Normal")).performClick()
        assertTrue(clicked.value)
    }

    @Test
    fun nonClickableWarningCardDoesNotExposeClickAction() {
        composeTestRule.setContent {
            MaterialTheme {
                ActionCard(title = "Warning", status = ActionCardStatus.Warning)
            }
        }

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }
}
