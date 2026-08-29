package com.musicapp.player.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicapp.player.theme.MusicTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Shared application shell placeholder.
 *
 * The shell lays out the navigation slot and business content, then places the
 * application-level player sheet above both.  It deliberately does not apply
 * inset padding: [content] and [playerSheetContent] receive the insets so the
 * concrete screen that owns a list, toolbar, or sheet can consume them once.
 * While Mini is visible, the shell reserves its fixed height for every business
 * destination before placing the player layer above it.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    drawerGesturesEnabled: Boolean = true,
    playerSheetVisible: Boolean = false,
    navigationContent: @Composable (WindowLayoutPolicy, closeDrawer: () -> Unit) -> Unit = { _, _ -> },
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (WindowInsets, WindowLayoutPolicy, openDrawer: () -> Unit) -> Unit,
    playerSheetContent: @Composable (WindowInsets) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val policy = WindowLayoutPolicy.forWidth(maxWidth)
        val dimensions = MusicTheme.dimensions
        val availableWidth = maxWidth

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
                val drawerWidth = availableWidth * policy.drawerFraction
                val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }
                val drawerOffset = remember(drawerWidthPx) { Animatable(-drawerWidthPx) }
                val scope = rememberCoroutineScope()
                val isDrawerVisible by
                    remember(drawerOffset, drawerWidthPx) {
                        derivedStateOf { drawerOffset.value > -drawerWidthPx }
                    }
                fun openDrawer() {
                    scope.launch {
                        drawerOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = CompactDrawerAnimationSpec,
                        )
                    }
                }
                fun closeDrawer() {
                    scope.launch {
                        drawerOffset.animateTo(
                            targetValue = -drawerWidthPx,
                            animationSpec = CompactDrawerAnimationSpec,
                        )
                    }
                }
                fun toggleDrawer() {
                    scope.launch {
                        drawerOffset.animateTo(
                            targetValue =
                                toggleCompactDrawerOffset(
                                    offset = drawerOffset.value,
                                    drawerWidth = drawerWidthPx,
                                    targetOffset = drawerOffset.targetValue,
                                ),
                            animationSpec = CompactDrawerAnimationSpec,
                        )
                    }
                }

                LaunchedEffect(drawerGesturesEnabled) {
                    if (!drawerGesturesEnabled) {
                        drawerOffset.animateTo(
                            targetValue =
                                compactDrawerSettledOffset(
                                    offset = drawerOffset.value,
                                    drawerWidth = drawerWidthPx,
                                ),
                            animationSpec = CompactDrawerAnimationSpec,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .compactDrawerDrag(
                                enabled = drawerGesturesEnabled,
                                drawerOffset = drawerOffset,
                                drawerWidthPx = drawerWidthPx,
                            ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .wrapContentWidth(
                                    align = Alignment.Start,
                                    unbounded = true,
                                )
                                .requiredWidth(drawerWidth + availableWidth)
                                .fillMaxHeight()
                                .offset {
                                    IntOffset(
                                        x = drawerOffset.value.roundToInt(),
                                        y = 0,
                                    )
                                },
                    ) {
                        Box(
                            modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                        ) {
                            navigationContent(policy, ::closeDrawer)
                        }
                        Box(
                            modifier = Modifier.width(availableWidth).fillMaxHeight(),
                        ) {
                            ShellContent(
                                contentInsets = contentInsets,
                                contentBottomPadding =
                                    if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                                content = { insets -> content(insets, policy, ::toggleDrawer) },
                            )
                        }
                    }
                }
                BackHandler(enabled = isDrawerVisible, onBack = ::closeDrawer)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.width(policy.sidebarWidth).fillMaxHeight(),
                    ) {
                        navigationContent(policy) {}
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ShellContent(
                            contentInsets = contentInsets,
                            contentBottomPadding =
                                if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                            content = { insets -> content(insets, policy) {} },
                        )
                    }
                }
            }

            // The application player sheet is above both navigation and content,
            // so Mini remains full-window-width while the compact drawer is open.
            Box(
                modifier = Modifier.fillMaxSize().zIndex(1f),
                propagateMinConstraints = true,
            ) {
                playerSheetContent(contentInsets)
            }
        }
    }
}

@Composable
private fun ShellContent(
    contentInsets: WindowInsets,
    contentBottomPadding: Dp,
    content: @Composable (WindowInsets) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding)) {
        content(contentInsets)
    }
}

private fun Modifier.compactDrawerDrag(
    enabled: Boolean,
    drawerOffset: Animatable<Float, AnimationVector1D>,
    drawerWidthPx: Float,
): Modifier {
    if (!enabled) return this
    return pointerInput(drawerOffset, drawerWidthPx) {
        coroutineScope {
            val motionChannel = Channel<CompactDrawerMotion>(capacity = Channel.UNLIMITED)
            val processor =
                launch {
                    var motionJob: Job? = null
                    for (motion in motionChannel) {
                        motionJob?.cancel()
                        motionJob =
                            launch {
                                if (motion.animated) {
                                    drawerOffset.animateTo(
                                        targetValue = motion.targetOffset,
                                        animationSpec = CompactDrawerAnimationSpec,
                                    )
                                } else {
                                    drawerOffset.snapTo(motion.targetOffset)
                                }
                            }
                    }
                }

            try {
                val touchSlop = viewConfiguration.touchSlop
                this@pointerInput.awaitEachGesture {
                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                    val pointerId = down.id

                    val velocityTracker = VelocityTracker().apply {
                        addPosition(
                            timeMillis = down.uptimeMillis,
                            position = down.position,
                        )

                    }
                    var lastPosition = down.position
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var direction = CompactDrawerDragDirection.UNDECIDED
                    var horizontalDragStarted = false
                    var draggedOffset = drawerOffset.value

                    while (true) {
                        val change =
                            awaitPointerEvent(pass = PointerEventPass.Initial)
                                .changes
                                .firstOrNull { it.id == pointerId }
                                ?: break
                        velocityTracker.addPosition(
                            timeMillis = change.uptimeMillis,
                            position = change.position,
                        )
                        val delta = change.position - lastPosition
                        lastPosition = change.position
                        totalDragX += delta.x
                        totalDragY += delta.y

                        if (direction == CompactDrawerDragDirection.UNDECIDED) {
                            direction =
                                resolveCompactDrawerDragDirection(
                                    totalDragX = totalDragX,
                                    totalDragY = totalDragY,
                                    touchSlop = touchSlop,
                                )
                        }

                        when (direction) {
                            CompactDrawerDragDirection.UNDECIDED -> Unit
                            CompactDrawerDragDirection.VERTICAL -> return@awaitEachGesture
                            CompactDrawerDragDirection.HORIZONTAL -> {
                                horizontalDragStarted = true
                                change.consume()
                                draggedOffset =
                                    coerceCompactDrawerOffset(
                                        offset = draggedOffset + delta.x,
                                        drawerWidth = drawerWidthPx,
                                    )
                                motionChannel.trySend(
                                    CompactDrawerMotion(
                                        targetOffset = draggedOffset,
                                        animated = false,
                                    ),
                                )
                            }
                        }

                        if (!change.pressed) break
                    }

                    if (horizontalDragStarted) {
                        val velocityX =
                            velocityTracker
                                .calculateVelocity()
                                .x

                        motionChannel.trySend(
                            CompactDrawerMotion(
                                targetOffset =
                                    compactDrawerSettledOffset(
                                        offset = draggedOffset,
                                        drawerWidth = drawerWidthPx,
                                        velocityX = velocityX,
                                    ),
                                animated = true,
                            ),
                        )
                    }
                }
            } finally {
                motionChannel.close()
                processor.cancel()
            }
        }
    }
}

private data class CompactDrawerMotion(
    val targetOffset: Float,
    val animated: Boolean,
)

internal enum class CompactDrawerDragDirection {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL,
}

internal fun resolveCompactDrawerDragDirection(
    totalDragX: Float,
    totalDragY: Float,
    touchSlop: Float,
): CompactDrawerDragDirection {
    val horizontalDistance = abs(totalDragX)
    val verticalDistance = abs(totalDragY)
    if (max(horizontalDistance, verticalDistance) <= touchSlop) {
        return CompactDrawerDragDirection.UNDECIDED
    }
    return if (horizontalDistance > verticalDistance) {
        CompactDrawerDragDirection.HORIZONTAL
    } else {
        CompactDrawerDragDirection.VERTICAL
    }
}

internal fun coerceCompactDrawerOffset(
    offset: Float,
    drawerWidth: Float,
): Float = offset.coerceIn(-drawerWidth, 0f)

internal fun compactDrawerSettledOffset(
    offset: Float,
    drawerWidth: Float,
    velocityX: Float = 0f,
): Float {
    if (drawerWidth <= 0f) {
        return 0f
    }

    /*
     * 快速向右滑：
     * 无论当前拖动距离是否超过一半，都打开 Drawer。
     */
    if (velocityX >= CompactDrawerFlingVelocityThreshold) {
        return 0f
    }

    /*
     * 快速向左滑：
     * 无论当前打开比例如何，都关闭 Drawer。
     */
    if (velocityX <= -CompactDrawerFlingVelocityThreshold) {
        return -drawerWidth
    }

    /*
     * 速度不足时，再根据当前位置判断。
     */
    return if (offset >= -drawerWidth / 2f) {
        0f
    } else {
        -drawerWidth
    }
}

internal fun toggleCompactDrawerOffset(
    offset: Float,
    drawerWidth: Float,
    targetOffset: Float = offset,
): Float {
    if (drawerWidth <= 0f) {
        return 0f
    }
    return if (targetOffset == 0f || (targetOffset != -drawerWidth && offset > -drawerWidth / 2f)) {
        -drawerWidth
    } else {
        0f
    }
}

private const val CompactDrawerFlingVelocityThreshold = 800f

private val CompactDrawerAnimationSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/** A small named placeholder useful while the real player sheet is pending. */
@Composable
fun PlayerSheetPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) {
        content()
    }
}
