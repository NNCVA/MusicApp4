package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDropdownMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exitKeepsPopupComposedUntilExitAnimationCompletes() {
        val expanded = mutableStateOf(false)
        val mounted = mutableStateOf(false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MusicAppTheme {
                AppDropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false },
                ) {
                    DisposableEffect(Unit) {
                        mounted.value = true
                        onDispose { mounted.value = false }
                    }
                    AppDropdownMenuItem(
                        text = { Text("Action") },
                        onClick = {},
                    )
                }
            }
        }

        expanded.value = true
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.runOnIdle {
            assertTrue(mounted.value)
            assertTrue(expanded.value)
        }

        expanded.value = false
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.runOnIdle { assertTrue(mounted.value) }

        composeTestRule.mainClock.advanceTimeBy(120)
        composeTestRule.runOnIdle { assertFalse(mounted.value) }
    }

    @Test
    fun reopeningDuringExitReversesTheSamePopupTransition() {
        val expanded = mutableStateOf(false)
        val mounted = mutableStateOf(false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MusicAppTheme {
                AppDropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false },
                ) {
                    DisposableEffect(Unit) {
                        mounted.value = true
                        onDispose { mounted.value = false }
                    }
                    AppDropdownMenuItem(
                        text = { Text("Action") },
                        onClick = {},
                    )
                }
            }
        }

        expanded.value = true
        composeTestRule.mainClock.advanceTimeBy(150)
        expanded.value = false
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.runOnIdle { assertTrue(mounted.value) }
        expanded.value = true
        composeTestRule.mainClock.advanceTimeBy(150)

        composeTestRule.runOnIdle {
            assertTrue(mounted.value)
            assertTrue(expanded.value)
        }
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
    }
}
