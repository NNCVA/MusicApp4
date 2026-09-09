package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsetPillSliderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesRangeSemanticsAndPreservesDragCallbacks() {
        var value by mutableFloatStateOf(0.25f)
        val changes = mutableListOf<Float>()
        val finished = mutableListOf<Unit>()

        composeRule.setContent {
            MusicAppTheme {
                InsetPillSlider(
                    value = value,
                    onValueChange = {
                        value = it
                        changes += it
                    },
                    onValueChangeFinished = { finished += Unit },
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        val initialNode = composeRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)),
        )
        initialNode.assertExists()
        initialNode.performTouchInput {
            swipe(
                start = Offset(width * 0.25f, height / 2f),
                end = Offset(width * 0.75f, height / 2f),
                durationMillis = 100,
            )
        }

        composeRule.runOnIdle {
            assertTrue(changes.isNotEmpty())
            assertTrue(changes.last() > 0.7f)
            assertEquals(1, finished.size)
        }
    }
}
