package com.musicapp.player.data.background

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImportPolicyTest {
    @Test
    fun sampleSizeBoundsDecodedPixelsForPortraitLandscapeAndPanoramas() {
        listOf(6000 to 4000, 4000 to 6000, 65_536 to 1, 2560 to 2560, 1 to 1).forEach { (width, height) ->
            val sample = BackgroundImportPolicy.sampleSize(width, height)
            assertEquals(0, sample and (sample - 1))
            assertTrue((maxOf(width, height).toLong() + sample - 1) / sample <= BackgroundImportPolicy.MAX_LONG_EDGE)
        }
        assertEquals(1, BackgroundImportPolicy.sampleSize(2560, 1440))
        assertEquals(4, BackgroundImportPolicy.sampleSize(6000, 4000))
    }

    @Test
    fun malformedAndExcessiveDimensionsAreRejectedBeforePixelAllocation() {
        listOf(0 to 10, -1 to 10, 10 to 0, 65_537 to 1, 16_000 to 16_000, Int.MAX_VALUE to Int.MAX_VALUE)
            .forEach { (width, height) ->
                assertTrue(runCatching { BackgroundImportPolicy.sampleSize(width, height) }.exceptionOrNull() is IOException)
            }
        BackgroundImportPolicy.checkInputSize(BackgroundImportPolicy.MAX_INPUT_BYTES)
        assertTrue(
            runCatching { BackgroundImportPolicy.checkInputSize(BackgroundImportPolicy.MAX_INPUT_BYTES + 1) }
                .exceptionOrNull() is IOException,
        )
    }

    @Test
    fun storedSelectionRejectsTraversalAndMalformedRecords() {
        val ready = StoredBackgroundSelection.Ready(
            "background-8c674f29-692b-4ab5-a65d-d0ca6a95ae19.png",
            "a".repeat(64),
        )
        listOf(StoredBackgroundSelection.None, StoredBackgroundSelection.Unavailable, ready).forEach { selection ->
            assertEquals(selection, StoredBackgroundSelection.decode(selection.encode()))
        }
        listOf(
            "1\nready\n../outside.png\n${"a".repeat(64)}\n",
            "1\nready\n${ready.fileName}\nshort\n",
            "2\nnone\n",
            "1\nunavailable",
            "x".repeat(StoredBackgroundSelection.MAX_METADATA_BYTES + 1),
        ).forEach { metadata ->
            assertTrue(
                runCatching { StoredBackgroundSelection.decode(metadata.toByteArray()) }.exceptionOrNull() is IOException,
            )
        }
    }
}
