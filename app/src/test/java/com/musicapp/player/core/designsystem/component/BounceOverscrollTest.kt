package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class BounceOverscrollTest {

    @Test
    fun `math calculates initial resistance correctly at zero offset`() {
        val maxPx = 300f
        val delta = 100f
        val resisted = BounceOverscrollMath.calculateResistedDelta(
            currentOffsetPx = 0f,
            deltaY = delta,
            maxDisplacementPx = maxPx,
            dampingBase = 0.4f,
        )
        assertEquals(40f, resisted, 0.001f)
    }

    @Test
    fun `math dampens more as offset approaches max displacement`() {
        val maxPx = 300f
        val delta = 50f
        val lowOffsetResisted = BounceOverscrollMath.calculateResistedDelta(
            currentOffsetPx = 50f,
            deltaY = delta,
            maxDisplacementPx = maxPx,
        )
        val highOffsetResisted = BounceOverscrollMath.calculateResistedDelta(
            currentOffsetPx = 250f,
            deltaY = delta,
            maxDisplacementPx = maxPx,
        )
        assertTrue(lowOffsetResisted > highOffsetResisted)
        assertTrue(highOffsetResisted > 0f)
    }

    @Test
    fun `math clamps strictly to max displacement`() {
        val maxPx = 300f
        val delta = 500f
        val resisted = BounceOverscrollMath.calculateResistedDelta(
            currentOffsetPx = 280f,
            deltaY = delta,
            maxDisplacementPx = maxPx,
        )
        // 280 + resisted should not exceed 300
        assertEquals(20f, resisted, 0.001f)
    }

    @Test
    fun `math calculates fling impulse clamped to max fling distance`() {
        val maxFlingPx = 100f
        val moderateImpulse = BounceOverscrollMath.calculateFlingImpulse(
            availableVelocityY = 1000f,
            maxFlingPx = maxFlingPx,
            velocityFactor = 0.025f,
        )
        assertEquals(25f, moderateImpulse, 0.001f)

        val extremeImpulse = BounceOverscrollMath.calculateFlingImpulse(
            availableVelocityY = 10000f,
            maxFlingPx = maxFlingPx,
            velocityFactor = 0.025f,
        )
        assertEquals(100f, extremeImpulse, 0.001f)

        val negativeImpulse = BounceOverscrollMath.calculateFlingImpulse(
            availableVelocityY = -10000f,
            maxFlingPx = maxFlingPx,
            velocityFactor = 0.025f,
        )
        assertEquals(-100f, negativeImpulse, 0.001f)
    }

    @Test
    fun `preScroll consumes opposite delta when overscrolled`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher + BroadcastFrameClock())
        val animatable = Animatable(50f)
        val connection = BounceOverscrollConnection(
            scope = testScope,
            animatable = animatable,
            maxDisplacementPx = 300f,
            maxFlingPx = 100f,
        )

        // Pulling up while offset is +50px: should consume negative delta
        val consumed = connection.onPreScroll(
            available = Offset(0f, -30f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(-30f, consumed.y, 0.001f)

        // If delta is larger than current offset, only consume up to -50px
        val overConsumed = connection.onPreScroll(
            available = Offset(0f, -80f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(-50f, overConsumed.y, 0.001f)
    }

    @Test
    fun `preScroll does not consume when not overscrolled`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher + BroadcastFrameClock())
        val animatable = Animatable(0f)
        val connection = BounceOverscrollConnection(
            scope = testScope,
            animatable = animatable,
            maxDisplacementPx = 300f,
            maxFlingPx = 100f,
        )

        val consumed = connection.onPreScroll(
            available = Offset(0f, -30f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(0f, consumed.y, 0.001f)
    }

    @Test
    fun `postScroll consumes unhandled drag delta and triggers displacement`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher + BroadcastFrameClock())
        val animatable = Animatable(0f)
        val connection = BounceOverscrollConnection(
            scope = testScope,
            animatable = animatable,
            maxDisplacementPx = 300f,
            maxFlingPx = 100f,
        )

        val consumed = connection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 60f),
            source = NestedScrollSource.UserInput,
        )
        assertEquals(60f, consumed.y, 0.001f)

        testScheduler.advanceUntilIdle()
        assertTrue(animatable.value > 0f)
    }

    @Test
    fun `preFling absorbs velocity when overscrolled and settles`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val frameClock = BroadcastFrameClock()
        val testScope = TestScope(testDispatcher + frameClock)
        val animatable = Animatable(40f)
        val connection = BounceOverscrollConnection(
            scope = testScope,
            animatable = animatable,
            maxDisplacementPx = 300f,
            maxFlingPx = 100f,
        )

        val consumedVelocity = connection.onPreFling(Velocity(0f, 500f))
        assertEquals(500f, consumedVelocity.y, 0.001f)
        testScheduler.runCurrent()
        assertEquals(0f, animatable.targetValue, 0.001f)
    }
}
