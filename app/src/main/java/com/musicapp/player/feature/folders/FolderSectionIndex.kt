package com.musicapp.player.feature.folders

import com.ibm.icu.text.Transliterator
import java.util.Locale

internal const val FOLDER_SECTION_DIGIT_LABEL = "0"
internal const val FOLDER_SECTION_OTHER_LABEL = "#"

/** The folder index remains stable even when a label has no matching folder. */
internal val FOLDER_SECTION_INDEX_LABELS: List<String> =
    listOf(FOLDER_SECTION_DIGIT_LABEL) + ('A'..'Z').map(Char::toString) + FOLDER_SECTION_OTHER_LABEL

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

/**
 * Returns list positions for every fixed label. [leadingItemCount] accounts for storage cards that
 * are rendered before the searchable folder shortcuts.
 *
 * Empty labels point to the next populated group, or the nearest list boundary when there is no
 * later group. Positions are safe for a list containing the leading cards and folder items.
 */
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
    return buildMap {
        FOLDER_SECTION_INDEX_LABELS.forEach { label ->
            val position =
                starts[label]
                    ?: ordered
                        .firstOrNull { sectionOrder(it.label) > sectionOrder(label) }
                        ?.let { nextSection -> starts[nextSection.label] }
                    ?: lastPosition
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

internal fun folderSectionLabel(value: String?): String {
    val firstCodePoint = value.orEmpty().trim().firstCodePointOrNull() ?: return FOLDER_SECTION_OTHER_LABEL
    return when {
        Character.isDigit(firstCodePoint.codePoint) -> FOLDER_SECTION_DIGIT_LABEL
        firstCodePoint.codePoint in 'A'.code..'Z'.code ||
            firstCodePoint.codePoint in 'a'.code..'z'.code ->
            firstCodePoint.text.uppercase(Locale.ROOT)
        else -> pinyinInitial(firstCodePoint)?.toString() ?: FOLDER_SECTION_OTHER_LABEL
    }
}

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

private data class CodePointText(
    val text: String,
    val codePoint: Int,
)

private fun String.firstCodePointOrNull(): CodePointText? {
    if (isEmpty()) return null
    val codePoint = codePointAt(0)
    return CodePointText(String(Character.toChars(codePoint)), codePoint)
}

private fun pinyinInitial(value: CodePointText): Char? =
    HAN_TO_LATIN.transliterate(value.text)
        .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}
