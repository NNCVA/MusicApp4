package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface GutterMode {
    data object Hidden : GutterMode
    data object Scrollbar : GutterMode
    data class Index(
        val sortOrder: SectionSortOrder = SectionSortOrder.ASCENDING,
        val activeSection: String? = null,
        val populatedBuckets: Set<String> = emptySet(),
        val onSectionSelected: (String) -> Unit = {},
    ) : GutterMode
}

/**
 * 通用右侧覆盖层 (RightGutterOverlay)
 *
 * 与列表并列覆盖，不参与列表布局测量。
 * 可见条宽 12dp，透明命中区 20dp（位于右边缘安全边距内，不覆盖列表更多操作等交互按钮）；
 * 视觉高度占列表中间 70%（上下各留白 15%）；
 * 空闲态透明度 40%，交互态 100%；
 * 支持轻按与滑动即时响应，并浮现 72dp 字母气泡与触发 TextHandleMove 震动反馈。
 */
@Composable
fun RightGutterOverlay(
    mode: GutterMode,
    modifier: Modifier = Modifier,
) {
    if (mode is GutterMode.Hidden) return

    when (mode) {
        is GutterMode.Scrollbar -> {
            // Scrollbar 模式在需要交互滑块时可拓展；默认列表由 LazyList 滚动条承载
        }
        is GutterMode.Index -> {
            IndexOverlay(
                indexMode = mode,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun IndexOverlay(
    indexMode: GutterMode.Index,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val labels = remember(indexMode.sortOrder) {
        sectionIndexLabelsForOrder(indexMode.sortOrder)
    }

    var isInteracting by remember { mutableStateOf(false) }
    var touchYInParent by remember { mutableFloatStateOf(0f) }
    var activeBucketIndex by remember { mutableIntStateOf(0) }
    var visualTopPx by remember { mutableFloatStateOf(0f) }
    var visualBottomPx by remember { mutableFloatStateOf(0f) }

    val currentSortOrder = indexMode.sortOrder
    val currentOnSectionSelected = rememberUpdatedState(indexMode.onSectionSelected)

    val populatedIndices = remember(labels, indexMode.populatedBuckets) {
        indexMode.populatedBuckets.mapNotNull { label ->
            val idx = labels.indexOf(label)
            if (idx >= 0) idx else null
        }.toSet()
    }

    // 选中的标签文字
    val selectedLabel by remember(activeBucketIndex, labels) {
        derivedStateOf {
            labels.getOrElse(activeBucketIndex) { labels.first() }
        }
    }

    // 40% 空闲态透明度，100% 交互态透明度
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0.4f,
        animationSpec = tween(durationMillis = 200),
        label = "gutterAlpha",
    )

    // TalkBack 语义文案
    val sortOrderDescription = if (currentSortOrder == SectionSortOrder.ASCENDING) {
        stringResource(R.string.section_index_sort_asc)
    } else {
        stringResource(R.string.section_index_sort_desc)
    }
    val barDescription = stringResource(R.string.section_index_bar_description)
    val stateDescriptionText = stringResource(
        R.string.section_index_state_description,
        selectedLabel,
        sortOrderDescription,
        activeBucketIndex + 1,
    )
    val actionPrevText = stringResource(R.string.section_index_action_prev)
    val actionNextText = stringResource(R.string.section_index_action_next)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val totalHeightDp = maxHeight
        val visualHeightDp = totalHeightDp * 0.7f

        // 动态等距采样可见字符
        val visibleLabels = remember(visualHeightDp, labels) {
            sampleVisibleLabels(
                availableHeightDp = visualHeightDp.value,
                itemSizeDp = dimensions.sectionIndexItemSize.value,
                labels = labels,
            )
        }

        // 20dp 命中区：位于右边缘安全边距内，不干扰左侧列表中的按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(dimensions.sectionIndexTouchTargetWidth)
                .fillMaxHeight(0.7f) // 视觉高度 70%
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInParent()
                    visualTopPx = bounds.top
                    visualBottomPx = bounds.bottom
                }
                .clearAndSetSemantics {
                    contentDescription = barDescription
                    stateDescription = stateDescriptionText
                    setProgress(
                        action = { targetValue ->
                            val targetIndex = targetValue.roundToInt().coerceIn(0, labels.lastIndex)
                            val resolvedIndex = resolveNearestPopulatedBucket(
                                targetBucketIndex = targetIndex,
                                populatedBucketIndices = populatedIndices,
                                dragDirection = 0,
                                bucketCount = labels.size,
                            )
                            activeBucketIndex = resolvedIndex
                            currentOnSectionSelected.value(labels[resolvedIndex])
                            true
                        }
                    )
                    customActions = listOf(
                        CustomAccessibilityAction(label = actionPrevText) {
                            val prevIndices = populatedIndices.filter { it < activeBucketIndex }
                            if (prevIndices.isNotEmpty()) {
                                val prevIndex = prevIndices.maxOrNull() ?: activeBucketIndex
                                activeBucketIndex = prevIndex
                                currentOnSectionSelected.value(labels[prevIndex])
                                true
                            } else false
                        },
                        CustomAccessibilityAction(label = actionNextText) {
                            val nextIndices = populatedIndices.filter { it > activeBucketIndex }
                            if (nextIndices.isNotEmpty()) {
                                val nextIndex = nextIndices.minOrNull() ?: activeBucketIndex
                                activeBucketIndex = nextIndex
                                currentOnSectionSelected.value(labels[nextIndex])
                                true
                            } else false
                        }
                    )
                }
                .gutterTouchHandler(
                    labels = labels,
                    populatedIndices = populatedIndices,
                    visualTopPx = visualTopPx,
                    onInteractingChanged = { isInteracting = it },
                    onTouchYInParentChanged = { touchYInParent = it },
                    onBucketChanged = { newBucketIndex, label ->
                        if (newBucketIndex != activeBucketIndex) {
                            activeBucketIndex = newBucketIndex
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnSectionSelected.value(label)
                        }
                    },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            // 可见索引条：宽 12dp，靠右对齐
            Column(
                modifier = Modifier
                    .width(dimensions.sectionIndexItemSize)
                    .fillMaxHeight()
                    .padding(end = dimensions.spaceExtraSmall)
                    .alpha(overlayAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                visibleLabels.forEach { label ->
                    val isCurrent = (label == selectedLabel)
                    Text(
                        text = label,
                        style = MusicTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                        ),
                        color = if (isCurrent) {
                            MusicTheme.colors.primary
                        } else {
                            MusicTheme.colors.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // 72dp 字母气泡：交互时显示在索引栏左侧，垂直居中对齐手指当前 Y 轴位置，松手 150ms 淡出
        val bubbleSizePx = with(density) { dimensions.sectionIndexBubbleSize.toPx() }
        val clampedBubbleY = remember(touchYInParent, visualTopPx, visualBottomPx, bubbleSizePx) {
            val minCenter = visualTopPx + bubbleSizePx / 2f
            val maxCenter = (visualBottomPx - bubbleSizePx / 2f).coerceAtLeast(minCenter)
            (touchYInParent.coerceIn(minCenter, maxCenter) - bubbleSizePx / 2f).roundToInt()
        }

        AnimatedVisibility(
            visible = isInteracting,
            enter = fadeIn(animationSpec = tween(durationMillis = 80)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = -with(density) { (dimensions.sectionIndexTouchTargetWidth + 16.dp).roundToPx() },
                        y = clampedBubbleY,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .size(dimensions.sectionIndexBubbleSize)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .background(
                        color = MusicTheme.colors.primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectedLabel,
                    style = MusicTheme.typography.headlineMedium.copy(
                        color = MusicTheme.colors.onPrimary,
                    ),
                )
            }
        }
    }
}

/**
 * 手势处理：
 * 1. 触摸或滑动索引栏时即刻响应并消费事件；
 * 2. 精确基于内部局部高度映射至 28 个逻辑桶；
 * 3. 实时计算父容器绝对坐标供字母气泡居中跟随。
 */
private fun Modifier.gutterTouchHandler(
    labels: List<String>,
    populatedIndices: Set<Int>,
    visualTopPx: Float,
    onInteractingChanged: (Boolean) -> Unit,
    onTouchYInParentChanged: (Float) -> Unit,
    onBucketChanged: (Int, String) -> Unit,
): Modifier = pointerInput(labels, populatedIndices, visualTopPx) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val pointerId = down.id
        var lastPositionY = down.position.y
        var lastBucketIndex = -1

        onInteractingChanged(true)
        onTouchYInParentChanged(visualTopPx + down.position.y)

        val initialBucket = mapPointerYToBucketIndex(
            pointerY = down.position.y,
            indexTop = 0f,
            indexBottom = size.height.toFloat(),
            bucketCount = labels.size,
        )
        val initialResolved = resolveNearestPopulatedBucket(
            targetBucketIndex = initialBucket,
            populatedBucketIndices = populatedIndices,
            dragDirection = 0,
            bucketCount = labels.size,
        )
        if (initialResolved in labels.indices) {
            lastBucketIndex = initialResolved
            onBucketChanged(initialResolved, labels[initialResolved])
        }
        down.consume()

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            val deltaY = change.position.y - lastPositionY
            lastPositionY = change.position.y

            if (change.pressed) {
                change.consume()
                onTouchYInParentChanged(visualTopPx + change.position.y)

                val targetBucket = mapPointerYToBucketIndex(
                    pointerY = change.position.y,
                    indexTop = 0f,
                    indexBottom = size.height.toFloat(),
                    bucketCount = labels.size,
                )
                val direction = if (deltaY > 0.5f) 1 else if (deltaY < -0.5f) -1 else 0

                val resolvedBucket = resolveNearestPopulatedBucket(
                    targetBucketIndex = targetBucket,
                    populatedBucketIndices = populatedIndices,
                    dragDirection = direction,
                    bucketCount = labels.size,
                )

                if (resolvedBucket != lastBucketIndex && resolvedBucket in labels.indices) {
                    lastBucketIndex = resolvedBucket
                    onBucketChanged(resolvedBucket, labels[resolvedBucket])
                }
            } else {
                onInteractingChanged(false)
                break
            }
        }
    }
}
