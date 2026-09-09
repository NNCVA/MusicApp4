package com.musicapp.player.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.core.designsystem.component.InsetPillSlider
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactDrawerDoesNotStealSliderDrag() {
        var value by mutableFloatStateOf(500f)

        composeRule.setContent {
            MusicAppTheme {
                AppShell(
                    navigationContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                    content = { _, _, _ ->
                        InsetPillSlider(
                            value = value,
                            onValueChange = { value = it },
                            valueRange = 0f..2_000f,
                            steps = 7,
                            modifier = Modifier.width(300.dp).testTag(SETTINGS_SLIDER_TAG),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag(SETTINGS_SLIDER_TAG).performTouchInput {
            swipe(
                start = Offset(width * 0.25f, height / 2f),
                end = Offset(width * 0.75f, height / 2f),
                durationMillis = 500,
            )
        }

        composeRule.runOnIdle {
            assertTrue(value >= 1_250f)
        }
    }

    private companion object {
        const val SETTINGS_SLIDER_TAG = "settings-slider"
    }
}
