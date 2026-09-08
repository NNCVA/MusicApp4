package com.musicapp.player.feature.lyrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsSettingsSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingTextCenterRowTogglesOncePerTap() {
        val changes = mutableListOf<Boolean>()
        var isCentered by mutableStateOf(false)

        composeTestRule.setContent {
            MusicAppTheme {
                LyricsSettingsContent(
                    fontSizeSp = 20,
                    isTextCentered = isCentered,
                    fontWeight = 600,
                    onFontSizeChange = {},
                    onTextCenteredChange = { value ->
                        changes += value
                        isCentered = value
                    },
                    onFontWeightChange = {},
                )
            }
        }

        val toggleCard = composeTestRule.onNode(isToggleable())
        toggleCard.assertIsOff()

        // Click text area on the left
        toggleCard.performTouchInput {
            click(Offset(8f, height / 2f))
        }
        toggleCard.assertIsOn()
        assertEquals(listOf(true), changes)

        // Click switch area on the right
        toggleCard.performTouchInput {
            click(Offset((width - 8).toFloat(), height / 2f))
        }
        toggleCard.assertIsOff()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun lyricsSettingsContentExposesOneSwitchSemanticsNode() {
        composeTestRule.setContent {
            MusicAppTheme {
                LyricsSettingsContent(
                    fontSizeSp = 20,
                    isTextCentered = false,
                    fontWeight = 600,
                    onFontSizeChange = {},
                    onTextCenteredChange = {},
                    onFontWeightChange = {},
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
