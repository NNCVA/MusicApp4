package com.musicapp.player.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BounceOverscrollTest {
    @Test
    fun `drag limit uses ten percent of viewport up to 96dp cap`() {
        assertEquals(
            60f,
            BounceOverscrollPhysics.maxDragDisplacementPx(
                viewportHeightPx = 600f,
                capPx = 96f,
            ),
            0.001f,
        )
        assertEquals(
            96f,
            BounceOverscrollPhysics.maxDragDisplacementPx(
                viewportHeightPx = 1_200f,
                capPx = 96f,
            ),
            0.001f,
        )
    }

    @Test
    fun `drag resistance never exceeds adaptive displacement`() {
        val maxPx = 80f
        val offset =
            BounceOverscrollPhysics.resistedOffsetPx(
                currentOffsetPx = 75f,
                deltaY = 1_000f,
                maxDisplacementPx = maxPx,
            )

        assertEquals(maxPx, offset, 0.001f)
    }

    @Test
    fun `fling velocity is clamped from 24dp displacement budget`() {
        assertEquals(
            288f,
            BounceOverscrollPhysics.flingVelocityPx(
                remainingVelocityY = 10_000f,
                maxFlingDisplacementPx = 24f,
            ),
            0.001f,
        )
        assertEquals(
            -288f,
            BounceOverscrollPhysics.flingVelocityPx(
                remainingVelocityY = -10_000f,
                maxFlingDisplacementPx = 24f,
            ),
            0.001f,
        )
    }

    @Test
    fun `edge requires matching direction and supports short non-scrollable list`() {
        assertEquals(
            BounceEdge.START,
            BounceOverscrollPhysics.edgeFor(
                deltaY = 20f,
                canScrollBackward = false,
                canScrollForward = true,
            ),
        )
        assertEquals(
            BounceEdge.END,
            BounceOverscrollPhysics.edgeFor(
                deltaY = -20f,
                canScrollBackward = true,
                canScrollForward = false,
            ),
        )
        assertNull(
            BounceOverscrollPhysics.edgeFor(
                deltaY = -20f,
                canScrollBackward = false,
                canScrollForward = true,
            ),
        )
        // Short list (cannot scroll backward or forward) triggers START on pull-down and END on pull-up
        assertEquals(
            BounceEdge.START,
            BounceOverscrollPhysics.edgeFor(
                deltaY = 20f,
                canScrollBackward = false,
                canScrollForward = false,
            ),
        )
        assertEquals(
            BounceEdge.END,
            BounceOverscrollPhysics.edgeFor(
                deltaY = -20f,
                canScrollBackward = false,
                canScrollForward = false,
            ),
        )
    }

    @Test
    fun `queue can disable start edge without disabling end edge`() {
        assertNull(
            BounceOverscrollPhysics.edgeFor(
                deltaY = 20f,
                canScrollBackward = false,
                canScrollForward = true,
                allowStartEdge = false,
            ),
        )
        assertEquals(
            BounceEdge.END,
            BounceOverscrollPhysics.edgeFor(
                deltaY = -20f,
                canScrollBackward = true,
                canScrollForward = false,
                allowStartEdge = false,
            ),
        )
    }

    @Test
    fun `mid-list leftover delta is not converted into overscroll`() = runTest {
        val effect =
            BounceOverscrollEffect(
                canScrollBackward = { true },
                canScrollForward = { true },
                animationsEnabled = { true },
                maxDragCapPx = 96f,
                maxFlingDisplacementPx = 24f,
            )

        val consumed =
            effect.applyToScroll(
                delta = Offset(0f, -40f),
                source = NestedScrollSource.UserInput,
                performScroll = { Offset.Zero },
            )

        assertEquals(Offset.Zero, consumed)
        assertEquals(0f, effect.currentOffsetPx, 0.001f)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `disabled animation delegates input without consuming overscroll`() = runTest {
        val effect =
            BounceOverscrollEffect(
                canScrollBackward = { false },
                canScrollForward = { true },
                animationsEnabled = { false },
                maxDragCapPx = 96f,
                maxFlingDisplacementPx = 24f,
            )

        val consumed =
            effect.applyToScroll(
                delta = Offset(0f, 40f),
                source = NestedScrollSource.UserInput,
                performScroll = { Offset.Zero },
            )

        assertEquals(Offset.Zero, consumed)
        assertEquals(0f, effect.currentOffsetPx, 0.001f)
        assertFalse(effect.isInProgress)
    }
}
