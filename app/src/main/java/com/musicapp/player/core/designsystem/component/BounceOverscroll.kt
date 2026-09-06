package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

internal enum class BounceEdge {
    START,
    END,
}

/** Pure boundary and resistance rules shared by lazy lists, grids, and Player Queue. */
internal object BounceOverscrollPhysics {
    const val MAX_VIEWPORT_FRACTION = 0.1f
    const val DRAG_DAMPING = 0.4f
    const val FLING_VELOCITY_DAMPING = 0.6f
    const val FLING_VELOCITY_MULTIPLIER = 12f
    const val MIN_EFFECT_DELTA_PX = 0.5f

    fun maxDragDisplacementPx(
        viewportHeightPx: Float,
        capPx: Float,
    ): Float = min(
        viewportHeightPx.coerceAtLeast(0f) * MAX_VIEWPORT_FRACTION,
        capPx.coerceAtLeast(0f),
    )

    fun resistedOffsetPx(
        currentOffsetPx: Float,
        deltaY: Float,
        maxDisplacementPx: Float,
        damping: Float = DRAG_DAMPING,
    ): Float {
        if (maxDisplacementPx <= 0f) return 0f
        val ratio = (abs(currentOffsetPx) / maxDisplacementPx).coerceIn(0f, 1f)
        val resistance = (1f - ratio * ratio) * damping
        return (currentOffsetPx + deltaY * resistance)
            .coerceIn(-maxDisplacementPx, maxDisplacementPx)
    }

    fun flingVelocityPx(
        remainingVelocityY: Float,
        maxFlingDisplacementPx: Float,
        damping: Float = FLING_VELOCITY_DAMPING,
    ): Float {
        if (maxFlingDisplacementPx <= 0f) return 0f
        val maxVelocity = maxFlingDisplacementPx * FLING_VELOCITY_MULTIPLIER
        return (remainingVelocityY * damping).coerceIn(-maxVelocity, maxVelocity)
    }

    fun edgeFor(
        deltaY: Float,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
        allowStartEdge: Boolean = true,
        allowEndEdge: Boolean = true,
    ): BounceEdge? =
        when {
            deltaY > MIN_EFFECT_DELTA_PX && !canScrollBackward && allowStartEdge -> BounceEdge.START
            deltaY < -MIN_EFFECT_DELTA_PX && !canScrollForward && allowEndEdge -> BounceEdge.END
            else -> null
        }
}

/**
 * A state-aware vertical overscroll effect.
 *
 * The scrolling container remains the source of truth: visual displacement is created only from
 * input that the container did not consume while its state confirms the matching edge. The effect
 * node is installed inside LazyColumn/LazyVerticalGrid's stationary clipped viewport.
 */
internal class BounceOverscrollEffect(
    private val canScrollBackward: () -> Boolean,
    private val canScrollForward: () -> Boolean,
    private val animationsEnabled: () -> Boolean,
    private val maxDragCapPx: Float,
    private val maxFlingDisplacementPx: Float,
    private val allowStartEdge: Boolean = true,
    private val allowEndEdge: Boolean = true,
) : OverscrollEffect {
    private var viewportHeightPx = 0f
    private var offsetPx by mutableFloatStateOf(0f)
    private var settleJob: Job? = null
    private var settleGeneration = 0L

    internal val currentOffsetPx: Float
        get() = offsetPx

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (!animationsEnabled()) {
            reset()
            return performScroll(delta)
        }
        if (source == NestedScrollSource.UserInput) cancelSettle()

        val consumedByRelaxationY = relaxationFor(delta.y)
        if (consumedByRelaxationY != 0f) {
            offsetPx += consumedByRelaxationY
            if (abs(offsetPx) <= BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX) offsetPx = 0f
        }

        val consumedByRelaxation = Offset(0f, consumedByRelaxationY)
        val availableForScroll = delta - consumedByRelaxation
        val consumedByScroll = performScroll(availableForScroll)
        val remaining = availableForScroll - consumedByScroll

        val edge =
            if (source == NestedScrollSource.UserInput) {
                resolveEdge(remaining.y)
            } else {
                null
            }
        val consumedByOverscroll =
            if (edge != null) {
                val maxDragPx =
                    BounceOverscrollPhysics.maxDragDisplacementPx(
                        viewportHeightPx = viewportHeightPx,
                        capPx = maxDragCapPx,
                    )
                offsetPx =
                    BounceOverscrollPhysics.resistedOffsetPx(
                        currentOffsetPx = offsetPx,
                        deltaY = remaining.y,
                        maxDisplacementPx = maxDragPx,
                    )
                Offset(0f, remaining.y)
            } else {
                Offset.Zero
            }

        return consumedByRelaxation + consumedByScroll + consumedByOverscroll
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val consumed = performFling(velocity)
        if (!animationsEnabled()) {
            reset()
            return
        }
        val remaining = velocity - consumed
        settleBounce(remaining.y)
    }

    internal val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!animationsEnabled()) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (canScrollBackward() || canScrollForward()) return Offset.Zero

                val consumedY = relaxationFor(available.y)
                if (consumedY != 0f) {
                    cancelSettle()
                    offsetPx += consumedY
                    if (abs(offsetPx) <= BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX) {
                        offsetPx = 0f
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!animationsEnabled()) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y == 0f) return Offset.Zero
                if (canScrollBackward() || canScrollForward()) return Offset.Zero

                cancelSettle()
                val edge = resolveEdge(available.y) ?: return Offset.Zero
                val maxDragPx =
                    BounceOverscrollPhysics.maxDragDisplacementPx(
                        viewportHeightPx = viewportHeightPx,
                        capPx = maxDragCapPx,
                    )
                offsetPx =
                    BounceOverscrollPhysics.resistedOffsetPx(
                        currentOffsetPx = offsetPx,
                        deltaY = available.y,
                        maxDisplacementPx = maxDragPx,
                    )
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!animationsEnabled()) return Velocity.Zero
                if (canScrollBackward() || canScrollForward()) return Velocity.Zero
                if (abs(offsetPx) > BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX) {
                    settleBounce(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (!animationsEnabled()) return Velocity.Zero
                if (canScrollBackward() || canScrollForward()) return Velocity.Zero
                settleBounce(available.y)
                return available
            }
        }

    override val isInProgress: Boolean
        get() = abs(offsetPx) > BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX

    override val node: DelegatableNode =
        object : Modifier.Node(), LayoutModifierNode {
            override fun MeasureScope.measure(
                measurable: Measurable,
                constraints: Constraints,
            ): MeasureResult {
                val placeable = measurable.measure(constraints)
                viewportHeightPx = placeable.height.toFloat()
                return layout(placeable.width, placeable.height) {
                    val offset = IntOffset(0, offsetPx.roundToInt())
                    placeable.placeRelativeWithLayer(offset.x, offset.y)
                }
            }
        }

    private suspend fun settleBounce(remainingVelocityY: Float) {
        if (!animationsEnabled()) {
            reset()
            return
        }
        val edge = resolveEdge(remainingVelocityY)
        val initialVelocity =
            if (edge != null) {
                BounceOverscrollPhysics.flingVelocityPx(
                    remainingVelocityY = remainingVelocityY,
                    maxFlingDisplacementPx = maxFlingDisplacementPx,
                )
            } else {
                0f
            }
        if (abs(offsetPx) <= BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX && initialVelocity == 0f) {
            offsetPx = 0f
            return
        }

        val activeJob = coroutineContext[Job]
        val generation = ++settleGeneration
        settleJob = activeJob
        val displacementLimit =
            if (abs(offsetPx) > BounceOverscrollPhysics.MIN_EFFECT_DELTA_PX) {
                BounceOverscrollPhysics.maxDragDisplacementPx(viewportHeightPx, maxDragCapPx)
            } else {
                maxFlingDisplacementPx
            }
        try {
            AnimationState(
                initialValue = offsetPx,
                initialVelocity = initialVelocity,
            ).animateTo(
                targetValue = 0f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = 100f,
                    ),
            ) {
                offsetPx = value.coerceIn(-displacementLimit, displacementLimit)
            }
        } finally {
            if (settleGeneration == generation) {
                settleJob = null
                offsetPx = 0f
            }
        }
    }

    private fun resolveEdge(deltaY: Float): BounceEdge? =
        BounceOverscrollPhysics.edgeFor(
            deltaY = deltaY,
            canScrollBackward = canScrollBackward(),
            canScrollForward = canScrollForward(),
            allowStartEdge = allowStartEdge,
            allowEndEdge = allowEndEdge,
        )

    private fun relaxationFor(deltaY: Float): Float =
        when {
            offsetPx > 0f && deltaY < 0f -> deltaY.coerceAtLeast(-offsetPx)
            offsetPx < 0f && deltaY > 0f -> deltaY.coerceAtMost(-offsetPx)
            else -> 0f
        }

    private fun cancelSettle() {
        settleGeneration += 1
        settleJob?.cancel()
        settleJob = null
    }

    private fun reset() {
        cancelSettle()
        offsetPx = 0f
    }
}

@Composable
internal fun rememberBounceOverscrollEffect(
    state: LazyListState,
    allowStartEdge: Boolean = true,
    allowEndEdge: Boolean = true,
): BounceOverscrollEffect =
    rememberBounceOverscrollEffect(
        canScrollBackward = { state.canScrollBackward },
        canScrollForward = { state.canScrollForward },
        stateKey = state,
        allowStartEdge = allowStartEdge,
        allowEndEdge = allowEndEdge,
    )

@Composable
internal fun rememberBounceOverscrollEffect(
    state: LazyGridState,
    allowStartEdge: Boolean = true,
    allowEndEdge: Boolean = true,
): BounceOverscrollEffect =
    rememberBounceOverscrollEffect(
        canScrollBackward = { state.canScrollBackward },
        canScrollForward = { state.canScrollForward },
        stateKey = state,
        allowStartEdge = allowStartEdge,
        allowEndEdge = allowEndEdge,
    )

@Composable
private fun rememberBounceOverscrollEffect(
    canScrollBackward: () -> Boolean,
    canScrollForward: () -> Boolean,
    stateKey: Any,
    allowStartEdge: Boolean,
    allowEndEdge: Boolean,
): BounceOverscrollEffect {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxDragCapPx = with(density) { 96.dp.toPx() }
    val maxFlingDisplacementPx = with(density) { 24.dp.toPx() }
    return remember(
        scope,
        stateKey,
        maxDragCapPx,
        maxFlingDisplacementPx,
        allowStartEdge,
        allowEndEdge,
    ) {
        BounceOverscrollEffect(
            canScrollBackward = canScrollBackward,
            canScrollForward = canScrollForward,
            animationsEnabled = {
                scope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
            },
            maxDragCapPx = maxDragCapPx,
            maxFlingDisplacementPx = maxFlingDisplacementPx,
            allowStartEdge = allowStartEdge,
            allowEndEdge = allowEndEdge,
        )
    }
}

/**
 * Attaches nested scroll gesture routing so that short non-scrollable lists
 * can also trigger bounce overscroll.
 */
internal fun Modifier.bounceOverscroll(effect: BounceOverscrollEffect): Modifier =
    this.nestedScroll(effect.nestedScrollConnection)
