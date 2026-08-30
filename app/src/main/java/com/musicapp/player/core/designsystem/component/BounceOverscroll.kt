package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 物理阻尼与过度滚动数学计算模型
 */
object BounceOverscrollMath {
    fun calculateResistedDelta(
        currentOffsetPx: Float,
        deltaY: Float,
        maxDisplacementPx: Float,
        dampingBase: Float = 0.4f,
    ): Float {
        if (maxDisplacementPx <= 0f) return 0f
        val ratio = (abs(currentOffsetPx) / maxDisplacementPx).coerceIn(0f, 1f)
        val resistanceFactor = (1f - ratio * ratio) * dampingBase
        val candidate = currentOffsetPx + deltaY * resistanceFactor
        val clamped = candidate.coerceIn(-maxDisplacementPx, maxDisplacementPx)
        return clamped - currentOffsetPx
    }

    fun calculateFlingVelocity(
        availableVelocityY: Float,
        maxDisplacementPx: Float,
        velocityDampingFactor: Float = 0.6f,
    ): Float {
        if (maxDisplacementPx <= 0f) return 0f
        val maxVelocity = maxDisplacementPx * 12f
        return (availableVelocityY * velocityDampingFactor).coerceIn(-maxVelocity, maxVelocity)
    }
}

/**
 * 弹性过度滚动状态与 NestedScroll 协调器
 */
class BounceOverscrollConnection(
    private val scope: CoroutineScope,
    private val animatable: Animatable<Float, *>,
    private val maxDisplacementPx: Float,
    private val maxFlingPx: Float,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val currentOffset = animatable.value
        if (currentOffset == 0f || available.y == 0f) return Offset.Zero

        // 当处于下拉拉伸状态 (currentOffset > 0) 且用户向上滑动 (available.y < 0)
        if (currentOffset > 0f && available.y < 0f) {
            val consumedY = available.y.coerceAtLeast(-currentOffset)
            scope.launch {
                animatable.snapTo(currentOffset + consumedY)
            }
            return Offset(0f, consumedY)
        }

        // 当处于上拉拉伸状态 (currentOffset < 0) 且用户向下滑动 (available.y > 0)
        if (currentOffset < 0f && available.y > 0f) {
            val consumedY = available.y.coerceAtMost(-currentOffset)
            scope.launch {
                animatable.snapTo(currentOffset + consumedY)
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
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

        val currentOffset = animatable.value
        val deltaToAdd = BounceOverscrollMath.calculateResistedDelta(
            currentOffsetPx = currentOffset,
            deltaY = available.y,
            maxDisplacementPx = maxDisplacementPx,
        )

        if (deltaToAdd != 0f) {
            scope.launch {
                animatable.snapTo(currentOffset + deltaToAdd)
            }
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val currentOffset = animatable.value
        if (currentOffset != 0f) {
            scope.launch {
                animatable.animateTo(
                    targetValue = 0f,
                    initialVelocity = available.y,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = 100f,
                    ),
                )
            }
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y != 0f) {
            val initialVelocity = BounceOverscrollMath.calculateFlingVelocity(
                availableVelocityY = available.y,
                maxDisplacementPx = maxDisplacementPx,
            )
            if (initialVelocity != 0f) {
                scope.launch {
                    animatable.animateTo(
                        targetValue = 0f,
                        initialVelocity = initialVelocity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = 100f,
                        ),
                    )
                }
                return available
            }
        }
        return Velocity.Zero
    }
}

/**
 * 通用物理阻尼弹性过度滚动 Modifier
 *
 * 适用于 LazyColumn, LazyVerticalGrid 以及可滚动 Column。
 */
fun Modifier.bounceOverscroll(
    enabled: Boolean = true,
    maxDisplacement: Dp = 200.dp,
    flingMaxDisplacement: Dp = 48.dp,
): Modifier = if (!enabled) this else composed {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(0f) }
    val maxDisplacementPx = with(density) { maxDisplacement.toPx() }
    val maxFlingPx = with(density) { flingMaxDisplacement.toPx() }

    val connection = remember(scope, animatable, maxDisplacementPx, maxFlingPx) {
        BounceOverscrollConnection(
            scope = scope,
            animatable = animatable,
            maxDisplacementPx = maxDisplacementPx,
            maxFlingPx = maxFlingPx,
        )
    }

    this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = animatable.value
        }
}
