package com.musicapp.player.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicColorSwitchRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingTextAndSwitchAreaTogglesOncePerTap() {
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                DynamicColorSwitchRow(
                    checked = checked,
                    enabled = true,
                    onCheckedChange = { value ->
                        changes += value
                        checked = value
                    },
                )
            }
        }

        val row = composeTestRule.onNode(isToggleable())
        row.assertIsOff()

        // 点击左侧文本区域
        row.performTouchInput {
            click(Offset(8f, height / 2f))
        }
        row.assertIsOn()
        assertEquals(listOf(true), changes)

        // 点击右侧开关区域
        row.performTouchInput {
            click(Offset((width - 8).toFloat(), height / 2f))
        }
        row.assertIsOff()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun disabledDynamicColorSwitchDoesNotTriggerCallback() {
        val changes = mutableListOf<Boolean>()

        composeTestRule.setContent {
            MaterialTheme {
                DynamicColorSwitchRow(
                    checked = false,
                    enabled = false,
                    onCheckedChange = { changes += it },
                )
            }
        }

        val row = composeTestRule.onNode(isToggleable())
        row.assertIsNotEnabled()
        row.performTouchInput {
            click(Offset(8f, height / 2f))
        }
        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test
    fun dynamicColorSwitchExposesOneToggleableSemanticsNode() {
        composeTestRule.setContent {
            MaterialTheme {
                DynamicColorSwitchRow(
                    checked = true,
                    enabled = true,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun appearanceSettingsDisablesPresetThemesWhenDynamicColorIsActive() {
        var colorSource by mutableStateOf(ColorSource.DYNAMIC)

        composeTestRule.setContent {
            MaterialTheme {
                AppearanceSettings(
                    settings = AppSettings(colorSource = colorSource),
                    onColorSourceChange = { colorSource = it },
                    onPresetThemeChange = {},
                    onThemeModeChange = {},
                    supportsDynamicColor = true,
                )
            }
        }

        val switchNode = composeTestRule.onNode(isToggleable())
        switchNode.assertIsOn()

        // 切换为关闭（使用预设主题）
        switchNode.performTouchInput {
            click()
        }
        assertEquals(ColorSource.PRESET, colorSource)
    }
}
