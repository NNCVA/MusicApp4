package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import com.musicapp.player.theme.MusicTheme

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
    if (sections.isEmpty()) return

    val dimensions = MusicTheme.dimensions
    Column(
        modifier = modifier
            .width(dimensions.sectionIndexItemSize)
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
