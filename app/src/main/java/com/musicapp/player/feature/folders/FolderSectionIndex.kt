package com.musicapp.player.feature.folders

import com.ibm.icu.text.Transliterator
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_ASCENDING_LABELS
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_DIGIT_LABEL
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_OTHER_LABEL
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import java.util.Locale

internal val FOLDER_SECTION_INDEX_LABELS: List<String> = SECTION_INDEX_ASCENDING_LABELS

internal data class FolderSection(
    val label: String,
    val folders: List<FolderNode>,
)

internal fun groupFoldersIntoSections(folders: List<FolderNode>): List<FolderSection> {
    val byLabel = linkedMapOf<String, MutableList<FolderNode>>()
    sortFoldersByIndexedName(folders).forEach { folder ->
        byLabel.getOrPut(folderSectionLabel(folder.displayName), ::mutableListOf) += folder
    }
    return FOLDER_SECTION_INDEX_LABELS.mapNotNull { label ->
        byLabel[label]?.let { FolderSection(label = label, folders = it.toList()) }
    }
}

internal fun sortFoldersByIndexedName(folders: List<FolderNode>): List<FolderNode> =
    folders.sortedWith(folderNameComparator)

internal fun sectionIndexLabels(): List<String> = FOLDER_SECTION_INDEX_LABELS

internal fun sectionStartPositions(
    sections: List<FolderSection>,
    leadingItemCount: Int = 0,
): Map<String, Int> {
    val ordered = orderedSections(sections)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = leadingItemCount.coerceAtLeast(0)
    ordered.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.folders.size
    }
    val itemCount = itemPosition
    val lastPosition = (itemCount - 1).coerceAtLeast(0)

    val populatedIndices = ordered.mapNotNull { section ->
        val idx = FOLDER_SECTION_INDEX_LABELS.indexOf(section.label)
        if (idx >= 0) idx else null
    }.toSet()

    return buildMap {
        FOLDER_SECTION_INDEX_LABELS.forEachIndexed { bucketIndex, label ->
            val position = starts[label] ?: run {
                val nearestIndex = resolveNearestPopulatedBucket(
                    targetBucketIndex = bucketIndex,
                    populatedBucketIndices = populatedIndices,
                    dragDirection = 0,
                    bucketCount = FOLDER_SECTION_INDEX_LABELS.size,
                )
                val nearestLabel = FOLDER_SECTION_INDEX_LABELS.getOrNull(nearestIndex)
                nearestLabel?.let { starts[it] } ?: lastPosition
            }
            put(label, position.coerceIn(0, lastPosition))
        }
    }
}

internal fun sectionLabelAtPosition(
    sections: List<FolderSection>,
    itemPosition: Int,
    leadingItemCount: Int = 0,
): String? {
    val ordered = orderedSections(sections).filter { it.folders.isNotEmpty() }
    if (ordered.isEmpty()) return null
    val safeLeading = leadingItemCount.coerceAtLeast(0)
    if (itemPosition < safeLeading) return ordered.first().label
    val folderPosition = itemPosition - safeLeading
    val itemCount = ordered.sumOf { it.folders.size }
    val safePosition = folderPosition.coerceIn(0, itemCount - 1)
    var sectionStart = 0
    ordered.forEach { section ->
        val sectionEnd = sectionStart + section.folders.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return ordered.last().label
}

internal fun folderSectionLabel(value: String?): String =
    classifySectionLabel(value)

internal fun folderSearchKey(value: String): String =
    HAN_TO_LATIN.transliterate(value.trim()).lowercase(Locale.ROOT)

private val folderNameComparator =
    compareBy<FolderNode>(
        { folderSortKey(it.displayName) },
        { it.displayName.lowercase(Locale.ROOT) },
        { it.displayName },
        { it.id.sourceId },
    )

private fun folderSortKey(value: String): String = folderSearchKey(value)

private fun orderedSections(sections: List<FolderSection>): List<FolderSection> =
    sections.sortedWith(compareBy { sectionOrder(it.label) })

private fun sectionOrder(label: String): Int =
    FOLDER_SECTION_INDEX_LABELS.indexOf(label).takeIf { it >= 0 } ?: FOLDER_SECTION_INDEX_LABELS.lastIndex

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}
