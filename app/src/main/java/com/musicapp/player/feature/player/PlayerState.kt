package com.musicapp.player.feature.player

import com.musicapp.player.core.domain.model.PlaybackMode
import kotlin.math.abs

enum class PlayerSheetValue { COLLAPSED, EXPANDED }

data class PlayerSheetState(
    val expansionProgress: Float = 0f,
) {
    init {
        require(expansionProgress in 0f..1f)
    }

    val value: PlayerSheetValue
        get() = if (expansionProgress >= 1f) PlayerSheetValue.EXPANDED else PlayerSheetValue.COLLAPSED

    fun dragBy(deltaYPx: Float, travelPx: Float): PlayerSheetState {
        require(travelPx > 0f)
        return copy(expansionProgress = (expansionProgress - deltaYPx / travelPx).coerceIn(0f, 1f))
    }

    fun settle(velocityYPxPerSecond: Float): PlayerSheetState =
        when {
            velocityYPxPerSecond <= -MIN_SETTLE_VELOCITY -> expanded()
            velocityYPxPerSecond >= MIN_SETTLE_VELOCITY -> collapsed()
            expansionProgress >= SETTLE_THRESHOLD -> expanded()
            else -> collapsed()
        }

    fun expanded() = copy(expansionProgress = 1f)
    fun collapsed() = copy(expansionProgress = 0f)

    companion object {
        const val SETTLE_THRESHOLD = 0.5f
        const val MIN_SETTLE_VELOCITY = 600f
        const val BASE_SETTLE_MIN_DURATION_MS = 250f
        const val BASE_SETTLE_VARIABLE_DURATION_MS = 250f
        const val VELOCITY_DAMPING_FACTOR = 0.3f
        const val MIN_SETTLE_DURATION_MS = 200
        const val MAX_SETTLE_DURATION_MS = 500

        fun calculateSettleDurationMs(
            currentProgress: Float,
            targetProgress: Float,
            velocityYPxPerSecond: Float = 0f,
            travelPx: Float = 1000f,
        ): Int {
            val distance = abs(targetProgress - currentProgress).coerceIn(0f, 1f)
            if (distance <= 0.0001f) return 0
            val velocityProgressPerSec = if (travelPx > 0f) {
                abs(velocityYPxPerSecond) / travelPx
            } else {
                0f
            }
            val baseDuration = BASE_SETTLE_MIN_DURATION_MS + BASE_SETTLE_VARIABLE_DURATION_MS * distance
            val velocityFactor = (1f / (1f + velocityProgressPerSec * VELOCITY_DAMPING_FACTOR)).coerceIn(0.35f, 1f)
            return (baseDuration * velocityFactor).toInt().coerceIn(MIN_SETTLE_DURATION_MS, MAX_SETTLE_DURATION_MS)
        }
    }
}

object PlayerLayerAlpha {
    fun mini(progress: Float): Float = (1f - progress / 0.25f).coerceIn(0f, 1f)
    fun full(progress: Float): Float = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
}

enum class FullPlayerPage { ARTWORK, LYRICS, QUEUE }

data class FullPlayerState(val page: FullPlayerPage = FullPlayerPage.ARTWORK) {
    fun select(page: FullPlayerPage) = copy(page = page)
    fun next() = copy(page = FullPlayerPage.entries[(page.ordinal + 1).coerceAtMost(FullPlayerPage.entries.lastIndex)])
    fun previous() = copy(page = FullPlayerPage.entries[(page.ordinal - 1).coerceAtLeast(0)])
}

enum class PlayerGestureRegion { SHEET_BACKGROUND, HORIZONTAL_PAGER, PROGRESS_SLIDER, QUEUE_CONTENT, LYRICS_CONTENT }
enum class PlayerGestureOwner { SHEET, CONTENT }
enum class QueueEdgeBehavior { SCROLL_CONTENT, DRAG_SHEET }

data class QueueGestureDecision(
    val behavior: QueueEdgeBehavior,
)

object PlayerGesturePolicy {
    fun owner(region: PlayerGestureRegion, deltaX: Float, deltaY: Float): PlayerGestureOwner =
        when (region) {
            PlayerGestureRegion.PROGRESS_SLIDER,
            PlayerGestureRegion.QUEUE_CONTENT,
            PlayerGestureRegion.LYRICS_CONTENT,
            -> PlayerGestureOwner.CONTENT
            PlayerGestureRegion.HORIZONTAL_PAGER ->
                if (abs(deltaY) > abs(deltaX)) PlayerGestureOwner.SHEET else PlayerGestureOwner.CONTENT
            PlayerGestureRegion.SHEET_BACKGROUND ->
                if (abs(deltaY) >= abs(deltaX)) PlayerGestureOwner.SHEET else PlayerGestureOwner.CONTENT
        }

    fun scrollableContentDecision(
        deltaX: Float,
        deltaY: Float,
        canScrollBackward: Boolean,
        isSheetExpanded: Boolean = true,
        isSheetDragging: Boolean = false,
    ): QueueGestureDecision =
        when {
            abs(deltaX) > abs(deltaY) -> QueueGestureDecision(QueueEdgeBehavior.SCROLL_CONTENT)
            !isSheetExpanded || isSheetDragging -> QueueGestureDecision(QueueEdgeBehavior.DRAG_SHEET)
            deltaY > 0f && !canScrollBackward -> QueueGestureDecision(QueueEdgeBehavior.DRAG_SHEET)
            else -> QueueGestureDecision(QueueEdgeBehavior.SCROLL_CONTENT)
        }

    fun scrollableContentFlingDecision(
        velocityY: Float,
        canScrollBackward: Boolean,
        isSheetExpanded: Boolean = true,
        isSheetDragging: Boolean = false,
    ): QueueEdgeBehavior =
        when {
            !isSheetExpanded || isSheetDragging -> QueueEdgeBehavior.DRAG_SHEET
            velocityY > 0f && !canScrollBackward -> QueueEdgeBehavior.DRAG_SHEET
            else -> QueueEdgeBehavior.SCROLL_CONTENT
        }

    fun queueDecision(
        deltaX: Float,
        deltaY: Float,
        canScrollBackward: Boolean,
        isSheetExpanded: Boolean = true,
        isSheetDragging: Boolean = false,
    ): QueueGestureDecision = scrollableContentDecision(
        deltaX = deltaX,
        deltaY = deltaY,
        canScrollBackward = canScrollBackward,
        isSheetExpanded = isSheetExpanded,
        isSheetDragging = isSheetDragging,
    )

    fun queueFlingDecision(
        velocityY: Float,
        canScrollBackward: Boolean,
        isSheetExpanded: Boolean = true,
        isSheetDragging: Boolean = false,
    ): QueueEdgeBehavior = scrollableContentFlingDecision(
        velocityY = velocityY,
        canScrollBackward = canScrollBackward,
        isSheetExpanded = isSheetExpanded,
        isSheetDragging = isSheetDragging,
    )
}

object PlayerGestureRouter {
    fun routeSheetDrag(
        region: PlayerGestureRegion,
        deltaX: Float,
        deltaY: Float,
        dragSheet: (Float) -> Float,
    ): Float =
        if (PlayerGesturePolicy.owner(region, deltaX, deltaY) == PlayerGestureOwner.SHEET) {
            dragSheet(deltaY)
        } else {
            0f
        }

    fun routeQueueDrag(
        deltaX: Float,
        deltaY: Float,
        canScrollBackward: Boolean,
        isSheetExpanded: Boolean = true,
        isSheetDragging: Boolean = false,
        dragSheet: (Float) -> Float,
    ): Float {
        val decision = PlayerGesturePolicy.queueDecision(
            deltaX = deltaX,
            deltaY = deltaY,
            canScrollBackward = canScrollBackward,
            isSheetExpanded = isSheetExpanded,
            isSheetDragging = isSheetDragging,
        )
        return when (decision.behavior) {
            QueueEdgeBehavior.SCROLL_CONTENT -> 0f
            QueueEdgeBehavior.DRAG_SHEET -> dragSheet(deltaY)
        }
    }
}

fun PlaybackMode.nextMode(): PlaybackMode =
    when (this) {
        PlaybackMode.LIST_REPEAT -> PlaybackMode.SINGLE_REPEAT
        PlaybackMode.SINGLE_REPEAT -> PlaybackMode.SHUFFLE
        PlaybackMode.SHUFFLE -> PlaybackMode.LIST_REPEAT
    }
