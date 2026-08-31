package com.musicapp.player.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionIndexCoreTest {

    @Test
    fun `ascending and descending bucket definitions have exactly 28 items`() {
        assertEquals(28, SECTION_INDEX_ASCENDING_LABELS.size)
        assertEquals(28, SECTION_INDEX_DESCENDING_LABELS.size)
        assertEquals("0", SECTION_INDEX_ASCENDING_LABELS.first())
        assertEquals("#", SECTION_INDEX_ASCENDING_LABELS.last())
        assertEquals("#", SECTION_INDEX_DESCENDING_LABELS.first())
        assertEquals("0", SECTION_INDEX_DESCENDING_LABELS.last())
        assertEquals(SECTION_INDEX_ASCENDING_LABELS, SECTION_INDEX_DESCENDING_LABELS.reversed())
    }

    @Test
    fun `classify label handles digits ascii pinyin and special characters`() {
        assertEquals("0", classifySectionLabel("123 Song"))
        assertEquals("A", classifySectionLabel("Alpha"))
        assertEquals("B", classifySectionLabel("bravo"))
        assertEquals("Z", classifySectionLabel("周杰伦"))
        assertEquals("F", classifySectionLabel("方大同"))
        assertEquals("#", classifySectionLabel("!Special"))
        assertEquals("#", classifySectionLabel("@Music"))
        assertEquals("#", classifySectionLabel(""))
        assertEquals("#", classifySectionLabel(null))
    }

    @Test
    fun `map pointer y clamps and distributes evenly across 28 buckets`() {
        assertEquals(0, mapPointerYToBucketIndex(pointerY = 0f, indexTop = 100f, indexBottom = 500f))
        assertEquals(0, mapPointerYToBucketIndex(pointerY = 100f, indexTop = 100f, indexBottom = 500f))
        assertEquals(27, mapPointerYToBucketIndex(pointerY = 500f, indexTop = 100f, indexBottom = 500f))
        assertEquals(27, mapPointerYToBucketIndex(pointerY = 600f, indexTop = 100f, indexBottom = 500f))

        // Mid point at 300f (normalized = 0.5) -> index 14
        val mid = mapPointerYToBucketIndex(pointerY = 300f, indexTop = 100f, indexBottom = 500f)
        assertEquals(14, mid)
    }

    @Test
    fun `resolve nearest populated bucket returns exact match when present`() {
        val populated = setOf(1, 5, 10)
        assertEquals(5, resolveNearestPopulatedBucket(5, populated, dragDirection = 0))
    }

    @Test
    fun `resolve nearest populated bucket resolves to closest populated bucket`() {
        val populated = setOf(2, 8, 20)
        assertEquals(2, resolveNearestPopulatedBucket(3, populated, dragDirection = 0))
        assertEquals(8, resolveNearestPopulatedBucket(6, populated, dragDirection = 0))
        assertEquals(20, resolveNearestPopulatedBucket(25, populated, dragDirection = 0))
    }

    @Test
    fun `resolve nearest populated bucket breaks ties using drag direction`() {
        val populated = setOf(2, 6) // Target 4 is equidistant from 2 and 6 (distance = 2)

        // Downward drag (dragDirection > 0) prefers 6
        assertEquals(6, resolveNearestPopulatedBucket(4, populated, dragDirection = 1))

        // Upward drag (dragDirection < 0) prefers 2
        assertEquals(2, resolveNearestPopulatedBucket(4, populated, dragDirection = -1))
    }

    @Test
    fun `sample visible labels respects clamp and preserves boundaries`() {
        // Very small height: clamp to 4
        val smallSample = sampleVisibleLabels(availableHeightDp = 30f, itemSizeDp = 12f)
        assertTrue(smallSample.size in 4..6)
        assertEquals("0", smallSample.first())
        assertEquals("#", smallSample.last())

        // Large height: returns all 28
        val fullSample = sampleVisibleLabels(availableHeightDp = 400f, itemSizeDp = 12f)
        assertEquals(28, fullSample.size)
        assertEquals(SECTION_INDEX_ASCENDING_LABELS, fullSample)

        // Medium height: returns between 4 and 28
        val mediumSample = sampleVisibleLabels(availableHeightDp = 120f, itemSizeDp = 12f)
        assertTrue(mediumSample.size in 4..12)
        assertEquals("0", mediumSample.first())
        assertEquals("#", mediumSample.last())
        assertTrue("A" in mediumSample)
    }

    @Test
    fun `pinyin disk cache persists and restores sort keys and initial labels`() {
        val tempDir = java.nio.file.Files.createTempDirectory("pinyin_test").toFile()
        try {
            initPinyinDiskCache(tempDir)
            val expectedLabel = classifySectionLabel("周杰伦")
            val expectedKey = pinyinSortKey("周杰伦")
            savePinyinDiskCache()

            // Re-initialize with saved cache
            initPinyinDiskCache(tempDir)
            assertEquals(expectedLabel, classifySectionLabel("周杰伦"))
            assertEquals(expectedKey, pinyinSortKey("周杰伦"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
