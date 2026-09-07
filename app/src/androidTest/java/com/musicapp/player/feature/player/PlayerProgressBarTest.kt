package com.musicapp.player.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerProgressBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backwardReleaseKeepsTargetUntilPlaybackReportsIt() {
        var positionMs by mutableLongStateOf(60_000L)
        val seekCalls = mutableListOf<Long>()
        val durationMs = 100_000L
        val initialRange = ProgressBarRangeInfo(0.6f, 0f..1f)
        val targetRange = ProgressBarRangeInfo(0.35f, 0f..1f)
        val acknowledgedRange = ProgressBarRangeInfo(0.354f, 0f..1f)

        composeRule.setContent {
            MaterialTheme {
                InteractiveThinProgressBar(
                    trackId = TrackId("external", 1L),
                    positionMs = positionMs,
                    durationMs = durationMs,
                    enabled = true,
                    onSeek = { target -> seekCalls += target },
                    modifier = Modifier.width(300.dp),
                )
            }
        }
        composeRule.onNode(hasProgressBarRangeInfo(initialRange)).assertExists()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNode(hasProgressBarRangeInfo(initialRange)).performTouchInput {
            swipe(
                start = Offset(width * 0.60f, height / 2f),
                end = Offset(width * 0.35f, height / 2f),
                durationMillis = 50,
            )
        }

        composeRule.runOnIdle {
            assertEquals(listOf(35_000L), seekCalls)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNode(hasProgressBarRangeInfo(targetRange)).assertExists()
        composeRule.onNodeWithText("0:35").assertExists()

        composeRule.runOnIdle { positionMs = 35_400L }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals(35_400L, positionMs)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNode(hasProgressBarRangeInfo(acknowledgedRange)).assertExists()
        composeRule.onNodeWithText("0:35").assertExists()
    }
}
