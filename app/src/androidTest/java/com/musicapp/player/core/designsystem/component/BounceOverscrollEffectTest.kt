package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BounceOverscrollEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun midListFastFlingDoesNotMoveHeaderOrStartBounce() {
        lateinit var effect: BounceOverscrollEffect
        lateinit var capturedState: LazyListState
        composeRule.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 20)
            val rememberedEffect = rememberBounceOverscrollEffect(listState)
            SideEffect {
                effect = rememberedEffect
                capturedState = listState
            }
            TestList(
                itemCount = 100,
                state = listState,
                effect = rememberedEffect,
            )
        }
        val headerBefore = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipeUp(durationMillis = 60)
        }
        composeRule.waitForIdle()

        val headerAfter = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertTrue(capturedState.firstVisibleItemIndex > 20)
            assertFalse(effect.isInProgress)
            assertEquals(0f, effect.currentOffsetPx, 0.001f)
        }
        assertEquals(headerBefore, headerAfter)
    }

    @Test
    fun startEdgeBounceLeavesHeaderFixedAndSettlesToZero() {
        lateinit var effect: BounceOverscrollEffect
        composeRule.setContent {
            val listState = rememberLazyListState()
            val rememberedEffect = rememberBounceOverscrollEffect(listState)
            SideEffect { effect = rememberedEffect }
            TestList(
                itemCount = 100,
                state = listState,
                effect = rememberedEffect,
            )
        }
        val headerBefore = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipeDown(durationMillis = 120)
        }

        val headerDuringBounce = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { assertTrue(effect.isInProgress) }
        assertEquals(headerBefore, headerDuringBounce)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(effect.isInProgress)
            assertEquals(0f, effect.currentOffsetPx, 0.001f)
        }
    }

    @Test
    fun endEdgeBounceLeavesHeaderFixedAndSettlesToZero() {
        lateinit var effect: BounceOverscrollEffect
        lateinit var capturedState: LazyListState
        composeRule.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 99)
            val rememberedEffect = rememberBounceOverscrollEffect(listState)
            SideEffect {
                effect = rememberedEffect
                capturedState = listState
            }
            TestList(
                itemCount = 100,
                state = listState,
                effect = rememberedEffect,
            )
        }
        composeRule.runOnIdle { assertFalse(capturedState.canScrollForward) }
        val headerBefore = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipeUp(durationMillis = 120)
        }

        val headerDuringBounce = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { assertTrue(effect.isInProgress) }
        assertEquals(headerBefore, headerDuringBounce)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(effect.isInProgress)
            assertEquals(0f, effect.currentOffsetPx, 0.001f)
        }
    }

    @Test
    fun shortListBouncesAndSettles() {
        lateinit var effect: BounceOverscrollEffect
        composeRule.setContent {
            val listState = rememberLazyListState()
            val rememberedEffect = rememberBounceOverscrollEffect(listState)
            SideEffect { effect = rememberedEffect }
            TestList(
                itemCount = 1,
                state = listState,
                effect = rememberedEffect,
            )
        }
        val headerBefore = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipeDown(durationMillis = 120)
        }

        val headerDuringBounce = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { assertTrue(effect.isInProgress) }
        assertEquals(headerBefore, headerDuringBounce)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(effect.isInProgress)
            assertEquals(0f, effect.currentOffsetPx, 0.001f)
        }
    }

    @Test
    fun shortListEndEdgeBouncesAndSettles() {
        lateinit var effect: BounceOverscrollEffect
        composeRule.setContent {
            val listState = rememberLazyListState()
            val rememberedEffect = rememberBounceOverscrollEffect(listState)
            SideEffect { effect = rememberedEffect }
            TestList(
                itemCount = 1,
                state = listState,
                effect = rememberedEffect,
            )
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipeUp(durationMillis = 120)
        }

        composeRule.runOnIdle { assertTrue(effect.isInProgress) }

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(effect.isInProgress)
            assertEquals(0f, effect.currentOffsetPx, 0.001f)
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestList(
        itemCount: Int,
        state: LazyListState,
        effect: BounceOverscrollEffect,
    ) {
        Column(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp).testTag(HEADER_TAG),
            )
            LazyColumn(
                state = state,
                overscrollEffect = effect,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .bounceOverscroll(effect)
                        .testTag(LIST_TAG),
            ) {
                items((0 until itemCount).toList()) { index ->
                    Text(
                        text = "Item $index",
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                }
            }
        }
    }

    private companion object {
        const val HEADER_TAG = "bounce-header"
        const val LIST_TAG = "bounce-list"
    }
}
