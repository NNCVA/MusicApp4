package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import kotlin.math.abs

private const val APP_DROPDOWN_MENU_INITIAL_SCALE = 0.8f
private const val APP_DROPDOWN_MENU_ENTER_DURATION_MS = 150
private const val APP_DROPDOWN_MENU_EXIT_DURATION_MS = 100

private val LocalAppDropdownMenuInteractionEnabled = compositionLocalOf { true }

/**
 * 通用行级与页级下拉管理菜单。
 *
 * 遵循设计系统规范：
 * - 移除容器内部默认的 8dp 垂直 Padding，使首项与末项的高亮完全贴合卡片顶部和底部；
 * - 外层 Surface 配合显式 `.clip(shape)`，使首末菜单项按压/选中高亮自动沿用外层圆角；
 * - 菜单项保证至少 48dp 触控热区与规范水波纹反馈；
 * - 风格与通用弹窗和消息气泡保持统一。
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MusicTheme.shapes.large,
    containerColor: Color = MusicTheme.colors.surface,
    tonalElevation: Dp = MusicTheme.dimensions.spaceExtraSmall,
    shadowElevation: Dp = MusicTheme.dimensions.spaceExtraSmall,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motionDurationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor
    val animationsEnabled = appDropdownMenuAnimationsEnabled(motionDurationScale)
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = expanded

    val popupVisible =
        if (animationsEnabled) {
            transitionState.currentState || transitionState.targetState || !transitionState.isIdle
        } else {
            expanded
        }

    if (popupVisible) {
        val popupPositionProvider =
            remember(offset, density) {
                AppDropdownMenuPositionProvider(offset, density)
            }
        var currentTransformOrigin by remember(layoutDirection) {
            mutableStateOf(defaultAppDropdownMenuTransformOrigin(layoutDirection))
        }
        val transition = rememberTransition(transitionState, label = "AppDropdownMenu")
        val scale by transition.animateFloat(
            transitionSpec = {
                if (!animationsEnabled) {
                    snap()
                } else {
                    tween(
                        durationMillis =
                            if (targetState) {
                                APP_DROPDOWN_MENU_ENTER_DURATION_MS
                            } else {
                                APP_DROPDOWN_MENU_EXIT_DURATION_MS
                            },
                        easing = FastOutSlowInEasing,
                    )
                }
            },
            label = "AppDropdownMenuScale",
        ) { visible ->
            if (visible) 1f else APP_DROPDOWN_MENU_INITIAL_SCALE
        }
        val alpha by transition.animateFloat(
            transitionSpec = {
                if (!animationsEnabled) {
                    snap()
                } else {
                    tween(
                        durationMillis =
                            if (targetState) {
                                APP_DROPDOWN_MENU_ENTER_DURATION_MS
                            } else {
                                APP_DROPDOWN_MENU_EXIT_DURATION_MS
                            },
                        easing = LinearEasing,
                    )
                }
            },
            label = "AppDropdownMenuAlpha",
        ) { visible ->
            if (visible) 1f else 0f
        }
        val interactionEnabled = transitionState.targetState
        val interactionModifier =
            if (interactionEnabled) {
                Modifier
            } else {
                Modifier
                    .clearAndSetSemantics {}
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                                    change.consume()
                                }
                            }
                        }
                    }
            }

        Popup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
        ) {
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                border = border,
                modifier =
                    Modifier
                        .width(MusicTheme.dimensions.dropdownMenuWidth)
                        .then(modifier)
                        .clip(shape)
                        .onGloballyPositioned {
                            popupPositionProvider.lastPlacement?.transformOrigin?.let {
                                currentTransformOrigin = it
                            }
                        }
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = currentTransformOrigin
                        }
                        .focusProperties {
                            canFocus = interactionEnabled
                        }
                        .then(interactionModifier),
            ) {
                CompositionLocalProvider(
                    LocalAppDropdownMenuInteractionEnabled provides interactionEnabled,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 菜单项语义图标调色板。
 *
 * 颜色值统一由 [com.musicapp.player.theme.AppAccentPalette] 管理，
 * 此对象通过 Composable 属性转发，保持调用方兼容性。
 */
object MenuIconPalette {
    val Play: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.play
    val Add: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.add
    val Rename: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.rename
    val Info: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.info
    val SelectAll: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.selectAll
    val Hide: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.hide
    val Delete: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.delete
    val Artist: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.mediaIconColors.getOrElse(2) { androidx.compose.ui.graphics.Color(0xFFE45F91) }
    val Album: androidx.compose.ui.graphics.Color
        @Composable get() = com.musicapp.player.theme.MusicTheme.accentPalette.mediaIconColors.getOrElse(1) { androidx.compose.ui.graphics.Color(0xFF9B6BE8) }
}

/**
 * 下拉菜单项组件。
 */
@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    iconTint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = MusicTheme.dimensions.spaceMedium),
) {
    val dimensions = MusicTheme.dimensions
    val menuInteractionEnabled = LocalAppDropdownMenuInteractionEnabled.current
    val itemEnabled = enabled && menuInteractionEnabled
    val contentColor =
        when {
            !enabled -> MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled)
            isDestructive -> MusicTheme.colors.error
            else -> MusicTheme.colors.onSurface
        }
    val iconColor =
        when {
            !enabled -> MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled)
            isDestructive -> MusicTheme.colors.error
            iconTint != null -> iconTint
            else -> MusicTheme.colors.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .clickable(
                    enabled = itemEnabled,
                    onClick = onClick,
                    role = Role.Button,
                )
                .let { itemModifier ->
                    if (menuInteractionEnabled) {
                        itemModifier.semantics(mergeDescendants = true) {}
                    } else {
                        itemModifier.clearAndSetSemantics {}
                    }
                }
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(
                LocalContentColor provides iconColor,
            ) {
                Box(
                    modifier = Modifier.size(dimensions.spaceLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingIcon()
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MusicTheme.typography.bodyLarge,
            ) {
                text()
            }
        }
        if (trailingIcon != null) {
            CompositionLocalProvider(
                LocalContentColor provides iconColor,
            ) {
                Box(
                    modifier = Modifier.size(dimensions.spaceLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * 下拉菜单分割线组件。
 */
@Composable
fun AppDropdownMenuDivider(
    modifier: Modifier = Modifier,
    color: Color = MusicTheme.colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
    thickness: Dp = 1.dp,
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        color = color,
        thickness = thickness,
    )
}

internal data class AppDropdownMenuPlacement(
    val position: IntOffset,
    val transformOrigin: TransformOrigin,
)

internal class AppDropdownMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
) : PopupPositionProvider {
    var lastPlacement: AppDropdownMenuPlacement? = null
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        calculateAppDropdownMenuPlacement(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            layoutDirection = layoutDirection,
            popupContentSize = popupContentSize,
            contentOffset = contentOffset,
            density = density,
        ).also { placement ->
            lastPlacement = placement
        }.position
}

internal fun calculateAppDropdownMenuPlacement(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
    contentOffset: DpOffset,
    density: Density,
): AppDropdownMenuPlacement {
    val contentOffsetPxX = with(density) { contentOffset.x.roundToPx() }
    val contentOffsetPxY = with(density) { contentOffset.y.roundToPx() }

    val x =
        if (layoutDirection == LayoutDirection.Ltr) {
            val rightAligned = anchorBounds.right - popupContentSize.width - contentOffsetPxX
            val leftAligned = anchorBounds.left + contentOffsetPxX
            if (rightAligned >= 0 && rightAligned + popupContentSize.width <= windowSize.width) {
                rightAligned
            } else if (leftAligned + popupContentSize.width <= windowSize.width) {
                leftAligned
            } else {
                (windowSize.width - popupContentSize.width).coerceAtLeast(0)
            }
        } else {
            val leftAligned = anchorBounds.left + contentOffsetPxX
            if (leftAligned >= 0 && leftAligned + popupContentSize.width <= windowSize.width) {
                leftAligned
            } else {
                anchorBounds.right - popupContentSize.width - contentOffsetPxX
            }
        }

    val spaceBelow = windowSize.height - anchorBounds.bottom
    val spaceAbove = anchorBounds.top
    val y =
        if (spaceBelow >= popupContentSize.height || spaceBelow >= spaceAbove) {
            anchorBounds.bottom + contentOffsetPxY
        } else {
            anchorBounds.top - popupContentSize.height - contentOffsetPxY
        }

    val position =
        IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    val popupBounds =
        IntRect(
            left = position.x,
            top = position.y,
            right = position.x + popupContentSize.width,
            bottom = position.y + popupContentSize.height,
        )
    return AppDropdownMenuPlacement(
        position = position,
        transformOrigin = calculateAppDropdownMenuTransformOrigin(anchorBounds, popupBounds),
    )
}

internal fun calculateAppDropdownMenuTransformOrigin(
    anchorBounds: IntRect,
    popupBounds: IntRect,
): TransformOrigin =
    TransformOrigin(
        pivotFractionX =
            if (abs(popupBounds.right - anchorBounds.right) <= abs(popupBounds.left - anchorBounds.left)) {
                1f
            } else {
                0f
            },
        pivotFractionY =
            if (abs(popupBounds.top - anchorBounds.bottom) <= abs(popupBounds.bottom - anchorBounds.top)) {
                0f
            } else {
                1f
            },
    )

private fun defaultAppDropdownMenuTransformOrigin(layoutDirection: LayoutDirection): TransformOrigin =
    TransformOrigin(
        pivotFractionX = if (layoutDirection == LayoutDirection.Ltr) 1f else 0f,
        pivotFractionY = 0f,
    )

internal fun appDropdownMenuAnimationsEnabled(motionDurationScale: Float?): Boolean =
    motionDurationScale != 0f
