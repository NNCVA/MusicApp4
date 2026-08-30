package com.musicapp.player.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChoiceGroupTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingAnyPartOfChoiceRowSelectsItOnce() {
        val selections = mutableListOf<String>()
        var selected by mutableStateOf("Light")

        composeTestRule.setContent {
            MaterialTheme {
                ChoiceGroup(
                    title = "Theme",
                    values = listOf("Light", "Dark"),
                    selected = selected,
                    label = { it },
                    onSelect = { value ->
                        selections += value
                        selected = value
                    },
                )
            }
        }

        val darkRow = composeTestRule.onNode(isSelectable() and hasText("Dark"))
        darkRow.performTouchInput {
            click(Offset((width - 8).toFloat(), height / 2f))
        }

        darkRow.assertIsSelected()
        assertEquals(listOf("Dark"), selections)
    }

    @Test
    fun choiceGroupExposesOneSelectableNodePerOption() {
        composeTestRule.setContent {
            MaterialTheme {
                ChoiceGroup(
                    title = "Theme",
                    values = listOf("Light", "Dark"),
                    selected = "Light",
                    label = { it },
                    onSelect = {},
                )
            }
        }

        composeTestRule.onAllNodes(isSelectable(), useUnmergedTree = true)
            .assertCountEquals(2)
    }
}
