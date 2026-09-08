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
    fun `queue top hands downward drag to sheet only when allowed by debounce coordinator`() {
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueDecision(
                deltaX = 0f,
                deltaY = 40f,
                canScrollBackward = false,
                canDragSheetFromContent = false,
            ).behavior,
        )
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.queueDecision(
                deltaX = 0f,
                deltaY = 40f,
                canScrollBackward = false,
                canDragSheetFromContent = true,
            ).behavior,
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
    fun `gesture router transfers queue top only when debounce coordinator allows it`() {
        var sheetDelta = 0f
        val lockedConsumed = PlayerGestureRouter.routeQueueDrag(
            deltaX = 0f,
            deltaY = 40f,
            canScrollBackward = false,
            canDragSheetFromContent = false,
            dragSheet = { delta -> sheetDelta = delta; delta },
        )
        assertEquals(0f, lockedConsumed)
        assertEquals(0f, sheetDelta)

        val allowedConsumed = PlayerGestureRouter.routeQueueDrag(
            deltaX = 0f,
            deltaY = 40f,
            canScrollBackward = false,
            canDragSheetFromContent = true,
            dragSheet = { delta -> sheetDelta = delta; delta },
        )
        assertEquals(40f, allowedConsumed)
        assertEquals(40f, sheetDelta)

        val endConsumed = PlayerGestureRouter.routeQueueDrag(
            deltaX = 0f,
            deltaY = -40f,
            canScrollBackward = true,
            dragSheet = { it },
        )
        assertEquals(0f, endConsumed)
    }

    @Test
    fun `content fling never settles sheet unless user is already actively dragging sheet`() {
        // 列表内部冲顶（未处于主动拖拽详情页状态），坚决不拉动详情页，留给阻尼回弹吸收
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = 800f,
                canScrollBackward = false,
                isSheetDragging = false,
            ),
        )
        // 处于主动拖拽详情页状态时的松手，正常触发详情页 Settle
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = 800f,
                canScrollBackward = false,
                isSheetDragging = true,
            ),
        )
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = -800f,
                canScrollBackward = false,
                isSheetDragging = false,
            ),
        )
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueFlingDecision(
                velocityY = 800f,
                canScrollBackward = true,
                isSheetDragging = false,
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

    @Test
    fun `debounce coordinator blocks collapse when pre-idle time is less than 1000ms`() {
        var simulatedTime = 10_000L
        val coordinator = ScrollableContentDebounceState(
            debounceDurationMs = 1_000L,
            timeProvider = { simulatedTime },
        )

        // 刚刚进入页面 300ms，在顶部向下拉
        simulatedTime += 300L
        coordinator.onScrollDelta(40f)

        // 前置静止不足 1000ms，禁止拖动抽屉
        assertEquals(false, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
        assertEquals(
            QueueEdgeBehavior.SCROLL_CONTENT,
            PlayerGesturePolicy.queueDecision(
                deltaX = 0f,
                deltaY = 40f,
                canScrollBackward = false,
                canDragSheetFromContent = coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false),
            ).behavior,
        )
    }

    @Test
    fun `debounce coordinator allows collapse when pre-idle time is at least 1000ms`() {
        var simulatedTime = 10_000L
        val coordinator = ScrollableContentDebounceState(
            debounceDurationMs = 1_000L,
            timeProvider = { simulatedTime },
        )

        // 已经静止了 1500ms
        simulatedTime += 1_500L
        coordinator.onScrollDelta(40f)

        // 允许拉动抽屉收起
        assertEquals(true, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
        assertEquals(
            QueueEdgeBehavior.DRAG_SHEET,
            PlayerGesturePolicy.queueDecision(
                deltaX = 0f,
                deltaY = 40f,
                canScrollBackward = false,
                canDragSheetFromContent = coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false),
            ).behavior,
        )
    }

    @Test
    fun `debounce coordinator allows continuous drag from list body to top when pre-idle was sufficient`() {
        var simulatedTime = 10_000L
        val coordinator = ScrollableContentDebounceState(
            debounceDurationMs = 1_000L,
            timeProvider = { simulatedTime },
        )

        // 用户在列表内容中间静止了 2000ms
        simulatedTime += 2_000L

        // 开始向下滑动手势（此时列表还在中间，canScrollBackward = true）
        coordinator.onScrollDelta(20f)
        assertEquals(false, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = true, isSheetDragging = false))

        // 顺势滑动达到最顶部（canScrollBackward = false），但同一次触摸手势未抬手继续向下拉
        coordinator.onScrollDelta(20f)
        assertEquals(true, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
    }

    @Test
    fun `debounce coordinator resets cooldown whenever bounce settles`() {
        var simulatedTime = 10_000L
        val coordinator = ScrollableContentDebounceState(
            debounceDurationMs = 1_000L,
            timeProvider = { simulatedTime },
        )

        // 冲顶触发回弹，此时处于运动状态
        coordinator.onMovementChanged(isMoving = true)
        simulatedTime += 300L

        // 回弹完成归零（isMoving = false）
        coordinator.onMovementChanged(isMoving = false)

        // 500ms 后再次下拉（距离回弹归零不足 1000ms）
        simulatedTime += 500L
        coordinator.onScrollDelta(30f)
        assertEquals(false, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
        coordinator.onGestureEnd()

        // 再次触发回弹并归零，重新开始计时 1000ms
        coordinator.onMovementChanged(isMoving = true)
        simulatedTime += 200L
        coordinator.onMovementChanged(isMoving = false)

        // 600ms 后下拉依然不足 1000ms
        simulatedTime += 600L
        coordinator.onScrollDelta(30f)
        assertEquals(false, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
        coordinator.onGestureEnd()
        coordinator.onMovementChanged(isMoving = false)

        // 静止超过 1000ms 后，再次下拉放行
        simulatedTime += 1_100L
        coordinator.onScrollDelta(30f)
        assertEquals(true, coordinator.canDragSheet(isSheetExpanded = true, canScrollBackward = false, isSheetDragging = false))
    }
}
