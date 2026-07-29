package com.musicapp.player.core.domain

import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackIdTest {
    @Test
    fun identityContainsBothVolumeNameAndMediaStoreId() {
        val id = TrackId(volumeName = "external_primary", mediaStoreId = 42L)

        assertEquals(id, TrackId(volumeName = "external_primary", mediaStoreId = 42L))
        assertNotEquals(id, TrackId(volumeName = "sdcard", mediaStoreId = 42L))
        assertNotEquals(id, TrackId(volumeName = "external_primary", mediaStoreId = 43L))
    }

    @Test
    fun blankVolumeNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackId(volumeName = " \t", mediaStoreId = 42L)
        }
    }

    @Test
    fun nonPositiveMediaStoreIdIsRejected() {
        listOf(0L, -1L).forEach { mediaStoreId ->
            assertThrows(IllegalArgumentException::class.java) {
                TrackId(volumeName = "external_primary", mediaStoreId = mediaStoreId)
            }
        }
    }
}
