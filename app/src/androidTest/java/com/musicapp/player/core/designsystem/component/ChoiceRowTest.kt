package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChoiceRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fullRowTogglesFromBothEdgesAndExposesOneToggleNode() {
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                ChoiceRow(
                    title = "Shared choice",
                    subtitle = "Description",
                    interactionModifier = Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = {
                            changes += it
                            checked = it
                        },
                    ),
                    trailingContent = {
                        Switch(
                            checked = checked,
                            onCheckedChange = null,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    },
                )
            }
        }

        val row = composeTestRule.onNode(isToggleable() and hasText("Shared choice"))
        row.assertIsOff()
        row.performTouchInput { click(Offset(8f, height / 2f)) }
        row.assertIsOn()
        row.performTouchInput { click(Offset((width - 8).toFloat(), height / 2f)) }
        row.assertIsOff()
        assertEquals(listOf(true, false), changes)
        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun disabledRowDoesNotToggleAndKeepsMinimumTouchHeight() {
        val changes = mutableListOf<Boolean>()

        composeTestRule.setContent {
            MaterialTheme {
                ChoiceRow(
                    title = "Disabled choice",
                    enabled = false,
                    interactionModifier = Modifier.toggleable(
                        value = false,
                        enabled = false,
                        role = Role.Switch,
                        onValueChange = { changes += it },
                    ),
                    trailingContent = {
                        Switch(
                            checked = false,
                            onCheckedChange = null,
                            enabled = false,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    },
                )
            }
        }

        val row = composeTestRule.onNode(isToggleable() and hasText("Disabled choice"))
        row.assertIsNotEnabled()
        row.performTouchInput { click() }
        assertEquals(emptyList<Boolean>(), changes)
        assertTrue(row.fetchSemanticsNode().size.height >= with(composeTestRule.density) { 48.dp.roundToPx() })
    }

    @Test
    fun compactRowKeepsMinimumTouchHeight() {
        composeTestRule.setContent {
            MaterialTheme {
                ChoiceRow(
                    title = "Compact choice",
                    compact = true,
                )
            }
        }

        val row = composeTestRule.onNode(hasText("Compact choice"))
        assertTrue(row.fetchSemanticsNode().size.height >= with(composeTestRule.density) { 48.dp.roundToPx() })
    }
}
