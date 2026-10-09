package com.musicapp.player.core.designsystem

import com.musicapp.player.core.designsystem.component.centerBackgroundCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCropTest {
    @Test
    fun landscapePhotoFillsPortraitWindowWithoutStretching() {
        val crop = requireNotNull(centerBackgroundCrop(2400f, 1200f, 400f, 800f))
        assertEquals(1600f, crop.width, 0.001f)
        assertEquals(800f, crop.height, 0.001f)
        assertEquals(-600f, crop.left, 0.001f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(crop.width / 2400f, crop.height / 1200f, 0.001f)
    }

    @Test
    fun rotationRecalculatesCropForTheWholeWindow() {
        val portrait = requireNotNull(centerBackgroundCrop(1200f, 2400f, 400f, 800f))
        val landscape = requireNotNull(centerBackgroundCrop(1200f, 2400f, 800f, 400f))
        assertEquals(0f, portrait.top, 0.001f)
        assertEquals(-600f, landscape.top, 0.001f)
        assertTrue(landscape.scale > portrait.scale)
    }

    @Test
    fun miniIncludingSystemInsetSamplesTheSameImageAsTheWindow() {
        val crop = requireNotNull(centerBackgroundCrop(800f, 1600f, 1200f, 900f))
        for (sheetOrigin in listOf(780f, 500f, 0f)) {
            for (localY in listOf(0f, 80f, 119f)) {
                val rootSourceY = (sheetOrigin + localY - crop.top) / crop.scale
                val sliceSourceY = (localY - (crop.top - sheetOrigin)) / crop.scale
                assertEquals(rootSourceY, sliceSourceY, 0.001f)
            }
        }
        // The crop stays based on all 1200px, even when the page is narrowed by a 300px sidebar.
        assertEquals(1200f, crop.width, 0.001f)
    }

    @Test
    fun unsetOrInvalidDimensionsDoNotProduceADrawTransform() {
        assertNull(centerBackgroundCrop(0f, 10f, 400f, 800f))
        assertNull(centerBackgroundCrop(Float.NaN, 10f, 400f, 800f))
        assertNull(centerBackgroundCrop(20f, 10f, Float.POSITIVE_INFINITY, 800f))
    }
}
