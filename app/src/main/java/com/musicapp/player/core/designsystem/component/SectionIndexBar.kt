package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import com.musicapp.player.theme.MusicTheme
import kotlin.math.abs

/**
 * 可复用的分组索引条。
 *
 * 页面负责提供当前实际存在的分组、选中分组和点击后的定位动作；索引条只负责展示与选中态。
 */
@Composable
fun SectionIndexBar(
    sections: List<String>,
    selectedSection: String?,
    onSectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    sectionContentDescription: (String) -> String = { it },
) {
    SectionIndexBar(
        sections = sections,
        selectedSection = selectedSection,
        onSectionClick = onSectionClick,
        onSectionDrag = null,
        modifier = modifier,
        sectionContentDescription = sectionContentDescription,
    )
}

/**
 * Same index bar with optional continuous drag selection.  The original overload above is kept so
 * existing source and binary callers that only support click selection remain valid.
 */
@Composable
fun SectionIndexBar(
    sections: List<String>,
    selectedSection: String?,
    onSectionClick: (String) -> Unit,
    onSectionDrag: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    sectionContentDescription: (String) -> String = { it },
) {
    if (sections.isEmpty()) return

    val dimensions = MusicTheme.dimensions
    val density = LocalDensity.current
    val currentOnSectionDrag = rememberUpdatedState(onSectionDrag)
    Column(
        modifier = modifier
            .width(dimensions.sectionIndexItemSize)
            .sectionIndexDrag(
                sections = sections,
                onSectionDrag = currentOnSectionDrag,
                verticalPaddingPx = with(density) { dimensions.spaceMedium.toPx() },
                itemSizePx = with(density) { dimensions.sectionIndexItemSize.toPx() },
                itemGapPx = with(density) { dimensions.sectionIndexItemGap.toPx() },
            )
            .padding(vertical = dimensions.spaceMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionIndexItemGap),
    ) {
        sections.forEach { section ->
            val isSelected = section == selectedSection
            Box(
                modifier = Modifier
                    .size(dimensions.sectionIndexItemSize)
                    .background(
                        color = if (isSelected) MusicTheme.colors.primary else Color.Transparent,
                        shape = MusicTheme.shapes.small,
                    )
                    .clickable { onSectionClick(section) }
                    .semantics {
                        contentDescription = sectionContentDescription(section)
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section,
                    style = MusicTheme.typography.labelSmall,
                    color = if (isSelected) MusicTheme.colors.onPrimary else MusicTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Modifier.sectionIndexDrag(
    sections: List<String>,
    onSectionDrag: State<((String) -> Unit)?>,
    verticalPaddingPx: Float,
    itemSizePx: Float,
    itemGapPx: Float,
): Modifier {
    if (onSectionDrag.value == null) return this
    return pointerInput(sections, verticalPaddingPx, itemSizePx, itemGapPx) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val pointerId = down.id
            var lastPosition = down.position
            var dragDistance = 0f
            var dragging = false
            var lastSection: String? = null

            while (true) {
                val change =
                    awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == pointerId }
                        ?: break
                val delta = change.position - lastPosition
                lastPosition = change.position
                dragDistance += maxOf(abs(delta.x), abs(delta.y))
                if (!dragging && dragDistance >= viewConfiguration.touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    val section = sectionAtPosition(
                        y = change.position.y,
                        verticalPaddingPx = verticalPaddingPx,
                        itemSizePx = itemSizePx,
                        itemGapPx = itemGapPx,
                        sections = sections,
                    )
                    if (section != null && section != lastSection) {
                        onSectionDrag.value?.invoke(section)
                        lastSection = section
                    }
                    change.consume()
                }
                if (!change.pressed) break
            }
        }
    }
}

private fun sectionAtPosition(
    y: Float,
    verticalPaddingPx: Float,
    itemSizePx: Float,
    itemGapPx: Float,
    sections: List<String>,
): String? {
    if (sections.isEmpty()) return null
    val slotPx = (itemSizePx + itemGapPx).coerceAtLeast(1f)
    val index = ((y - verticalPaddingPx) / slotPx).toInt().coerceIn(0, sections.lastIndex)
    return sections[index]
}
