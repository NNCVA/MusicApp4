package com.musicapp.player.feature.folders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderSectionIndexTest {
    @Test
    fun `index keeps fixed labels and classifies numbers pinyin and other characters`() {
        val folders = listOf(
            folder(1, "Beta"),
            folder(2, "\u4e2d\u6587"),
            folder(3, "123 songs"),
            folder(4, "!Special"),
            folder(5, "alpha"),
            folder(6, "Zulu"),
        )

        val sections = groupFoldersIntoSections(folders)

        assertEquals(listOf("0", "A", "B", "Z", "#"), sections.map(FolderSection::label))
        assertEquals(
            listOf("0") + ('A'..'Z').map(Char::toString) + "#",
            sectionIndexLabels(),
        )
        assertEquals(
            listOf("123 songs", "alpha", "Beta", "\u4e2d\u6587", "Zulu", "!Special"),
            sections.flatMap(FolderSection::folders).map(FolderNode::displayName),
        )
        assertEquals("Z", folderSectionLabel("\u4e2d\u6587"))
    }

    @Test
    fun `empty labels resolve to insertion positions and include leading volume cards`() {
        val sections = listOf(
            FolderSection("A", listOf(folder(1, "Alpha"), folder(2, "Another"))),
            FolderSection("C", listOf(folder(3, "Charlie"))),
            FolderSection("#", listOf(folder(4, "!Special"))),
        )

        val positions = sectionStartPositions(sections, leadingItemCount = 2)

        assertEquals(2, positions.getValue("0"))
        assertEquals(2, positions.getValue("A"))
        assertEquals(4, positions.getValue("B"))
        assertEquals(4, positions.getValue("C"))
        assertEquals(5, positions.getValue("Z"))
        assertEquals(5, positions.getValue("#"))
        assertEquals("A", sectionLabelAtPosition(sections, 0, leadingItemCount = 2))
        assertEquals("A", sectionLabelAtPosition(sections, 2, leadingItemCount = 2))
        assertEquals("C", sectionLabelAtPosition(sections, 4, leadingItemCount = 2))
        assertEquals("#", sectionLabelAtPosition(sections, 99, leadingItemCount = 2))
    }

    @Test
    fun `empty sections return stable fallback positions and no selected label`() {
        assertEquals(
            FOLDER_SECTION_INDEX_LABELS.associateWith { 0 },
            sectionStartPositions(emptyList()),
        )
        assertNull(sectionLabelAtPosition(emptyList(), 0))
    }

    private fun folder(id: Long, name: String): FolderNode =
        FolderNode(
            id = FolderId(volumeName = "external", relativePath = "folder$id"),
            displayName = name,
            directTracks = emptyList(),
            children = emptyList(),
        )
}
