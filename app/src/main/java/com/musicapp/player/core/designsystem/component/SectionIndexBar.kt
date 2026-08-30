package com.musicapp.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 历史分组索引条，已由 [RightGutterOverlay] 取代。
 */
@Deprecated(
    message = "Use RightGutterOverlay instead",
    replaceWith = ReplaceWith("RightGutterOverlay(mode = GutterMode.Index(...), modifier = modifier)"),
)
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

@Deprecated(
    message = "Use RightGutterOverlay instead",
    replaceWith = ReplaceWith("RightGutterOverlay(mode = GutterMode.Index(...), modifier = modifier)"),
)
@Composable
fun SectionIndexBar(
    sections: List<String>,
    selectedSection: String?,
    onSectionClick: (String) -> Unit,
    onSectionDrag: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    sectionContentDescription: (String) -> String = { it },
) {
    RightGutterOverlay(
        mode = GutterMode.Index(
            activeSection = selectedSection,
            populatedBuckets = sections.toSet(),
            onSectionSelected = { label ->
                onSectionDrag?.invoke(label) ?: onSectionClick(label)
            },
        ),
        modifier = modifier,
    )
}
