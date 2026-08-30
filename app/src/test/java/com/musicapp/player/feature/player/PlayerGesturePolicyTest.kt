package com.musicapp.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturePolicyTest {
    @Test
    fun `vertical sheet and horizontal pager gestures have distinct owners`() {
        assertEquals(PlayerGestureOwner.SHEET, PlayerGesturePolicy.owner(PlayerGestureRegion.SHEET_BACKGROUND, 2f, 20f))
        assertEquals(PlayerGestureOwner.CONTENT, PlayerGesturePolicy.owner(PlayerGestureRegion.HORIZONTAL_PAGER, 20f, 2f))
        assertEquals(PlayerGestureOwner.SHEET, PlayerGesturePolicy.owner(PlayerGestureRegion.HORIZONTAL_PAGER, 2f, 20f))
    }

    @Test
    fun `slider retains gestures and a scrolling queue consumes vertical input`() {
        assertEquals(PlayerGestureOwner.CONTENT, PlayerGesturePolicy.owner(PlayerGestureRegion.PROGRESS_SLIDER, 0f, 40f))
        assertEquals(PlayerGestureOwner.CONTENT, PlayerGesturePolicy.owner(PlayerGestureRegion.QUEUE_CONTENT, 0f, 40f))
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueDecision(0f, 40f, canScrollBackward = true).behavior,
        )
    }

    @Test
    fun `queue top hands downward drag to sheet`() {
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.queueDecision(0f, 40f, canScrollBackward = false).behavior,
        )
    }

    @Test
    fun `queue end leaves upward drag to content overscroll`() {
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueDecision(
                deltaX = 0f,
                deltaY = -40f,
                canScrollBackward = true,
            ).behavior,
        )
    }

    @Test
    fun `horizontal queue input remains content owned at either edge`() {
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueDecision(40f, 2f, canScrollBackward = false).behavior,
        )
    }

    @Test
    fun `gesture router invokes sheet only for a pager vertical drag`() {
        var sheetDelta = 0f
        val horizontalConsumed =
            PlayerGestureRouter.routeSheetDrag(PlayerGestureRegion.HORIZONTAL_PAGER, 40f, 2f) { delta ->
                sheetDelta = delta
                delta
            }
        val verticalConsumed =
            PlayerGestureRouter.routeSheetDrag(PlayerGestureRegion.HORIZONTAL_PAGER, 2f, 40f) { delta ->
                sheetDelta = delta
                delta
            }

        assertEquals(0f, horizontalConsumed)
        assertEquals(40f, verticalConsumed)
        assertEquals(40f, sheetDelta)
    }

    @Test
    fun `gesture router transfers queue top and leaves queue end to content`() {
        var sheetDelta = 0f
        val topConsumed = PlayerGestureRouter.routeQueueDrag(
            deltaX = 0f,
            deltaY = 40f,
            canScrollBackward = false,
            dragSheet = { delta -> sheetDelta = delta; delta },
        )
        val endConsumed = PlayerGestureRouter.routeQueueDrag(
            deltaX = 0f,
            deltaY = -40f,
            canScrollBackward = true,
            dragSheet = { it },
        )

        assertEquals(40f, topConsumed)
        assertEquals(40f, sheetDelta)
        assertEquals(0f, endConsumed)
    }

    @Test
    fun `queue fling transfers only downward velocity at the top`() {
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = 800f,
                canScrollBackward = false,
            ),
        )
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = -800f,
                canScrollBackward = false,
            ),
        )
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = 800f,
                canScrollBackward = true,
            ),
        )
    }
}
