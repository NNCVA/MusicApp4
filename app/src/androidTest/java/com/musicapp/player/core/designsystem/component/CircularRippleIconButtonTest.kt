package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CircularRippleIconButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun buttonClickInvokesCallbackWhenEnabled() {
        var clickCount = 0

        composeTestRule.setContent {
            MusicAppTheme {
                CircularRippleIconButton(
                    onClick = { clickCount++ },
                    enabled = true,
                ) {
                    Text("Play")
                }
            }
        }

        val node = composeTestRule.onNodeWithText("Play")
        node.assertIsEnabled()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        node.performClick()

        assertEquals(1, clickCount)
    }

    @Test
    fun buttonDoesNotInvokeCallbackWhenDisabled() {
        var clicked = false

        composeTestRule.setContent {
            MusicAppTheme {
                CircularRippleIconButton(
                    onClick = { clicked = true },
                    enabled = false,
                ) {
                    Text("Skip")
                }
            }
        }

        val node = composeTestRule.onNodeWithText("Skip")
        node.assertIsNotEnabled()
        node.performClick()

        assertFalse(clicked)
    }
}
