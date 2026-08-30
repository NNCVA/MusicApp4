package com.musicapp.player.feature.scan

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwitchCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingTitleAndSwitchAreaTogglesOncePerTap() {
        val title = "Use Android media library"
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                SwitchCard(
                    title = title,
                    checked = checked,
                    onCheckedChange = { value ->
                        changes += value
                        checked = value
                    },
                )
            }
        }

        val card = composeTestRule.onNode(isToggleable() and hasText(title))
        card.assertIsOff()
        card.performTouchInput {
            click(Offset(8f, height / 2f))
        }
        card.assertIsOn()
        assertEquals(listOf(true), changes)

        card.performTouchInput {
            click(Offset((width - 8).toFloat(), height / 2f))
        }
        card.assertIsOff()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun switchCardExposesOneSwitchSemanticsNode() {
        composeTestRule.setContent {
            MaterialTheme {
                SwitchCard(
                    title = "Skip short audio",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
