package com.musicapp.player.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicapp.player.R
import com.musicapp.player.data.background.BackgroundState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundImageSettingTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun defaultRowCanBeClickedAtEitherEndAndHasNoDeleteButton() {
        var picks = 0
        compose.setContent {
            MaterialTheme {
                BackgroundImageSetting(BackgroundState(), true, { picks++ }, {})
            }
        }
        val row = compose.onNodeWithText(text(R.string.settings_background_choose))
        row.performTouchInput { click(Offset(8f, height / 2f)) }
        row.performTouchInput { click(Offset(width - 8f, height / 2f)) }
        assertEquals(2, picks)
        compose.onNodeWithContentDescription(text(R.string.settings_background_delete)).assertDoesNotExist()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun unavailableSelectionCanBeDeletedWithoutLaunchingPicker() {
        var picks = 0
        var deletes = 0
        compose.setContent {
            MaterialTheme {
                BackgroundImageSetting(BackgroundState(hasSelection = true), true, { picks++ }, { deletes++ })
            }
        }
        compose.onNodeWithText(text(R.string.settings_background_unavailable)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.settings_background_delete)).performClick()
        assertEquals(0, picks)
        assertEquals(1, deletes)
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun processingDisablesBothSelectionAndDeletion() {
        compose.setContent {
            MaterialTheme {
                BackgroundImageSetting(BackgroundState("/saved.png", true), false, {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.settings_background_choose)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.settings_background_delete)).assertIsNotEnabled()
    }
}
