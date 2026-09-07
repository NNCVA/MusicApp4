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

    @Test
    fun `lyrics content shares content ownership with slider and queue`() {
        assertEquals(PlayerGestureOwner.CONTENT, PlayerGesturePolicy.owner(PlayerGestureRegion.LYRICS_CONTENT, 0f, 40f))
        assertEquals(PlayerGestureOwner.CONTENT, PlayerGesturePolicy.owner(PlayerGestureRegion.LYRICS_CONTENT, 40f, 0f))
    }

    @Test
    fun `partially expanded sheet hands both downward and upward drag to sheet`() {
        // 下拉折叠
        val downward = PlayerGesturePolicy.scrollableContentDecision(
            deltaX = 0f,
            deltaY = 30f,
            canScrollBackward = false,
            isSheetExpanded = false,
        )
        assertEquals(QueueEdgeBehavior.DRAG_SHEET, downward.behavior)

        // 上推恢复展开
        val upward = PlayerGesturePolicy.scrollableContentDecision(
            deltaX = 0f,
            deltaY = -30f,
            canScrollBackward = false,
            isSheetExpanded = false,
        )
        assertEquals(QueueEdgeBehavior.DRAG_SHEET, upward.behavior)

        // 处于活跃拖拽状态时同样接管
        val activeDrag = PlayerGesturePolicy.scrollableContentDecision(
            deltaX = 0f,
            deltaY = -20f,
            canScrollBackward = true,
            isSheetExpanded = true,
            isSheetDragging = true,
        )
        assertEquals(QueueEdgeBehavior.DRAG_SHEET, activeDrag.behavior)
    }

    @Test
    fun `partially expanded sheet or active drag settles sheet on zero velocity and reverse velocity`() {
        // 慢速松手（速度为 0）
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.scrollableContentFlingDecision(
                velocityY = 0f,
                canScrollBackward = false,
                isSheetExpanded = false,
            ),
        )

        // 上推释放（反向速度）
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.scrollableContentFlingDecision(
                velocityY = -400f,
                canScrollBackward = false,
                isSheetExpanded = false,
            ),
        )

        // 活跃拖拽释放（速度为 0）
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.scrollableContentFlingDecision(
                velocityY = 0f,
                canScrollBackward = true,
                isSheetExpanded = true,
                isSheetDragging = true,
            ),
        )

        // 完全展开且未驱动抽屉时，列表内部零速不影响内容
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.scrollableContentFlingDecision(
                velocityY = 0f,
                canScrollBackward = false,
                isSheetExpanded = true,
                isSheetDragging = false,
            ),
        )
    }
}
