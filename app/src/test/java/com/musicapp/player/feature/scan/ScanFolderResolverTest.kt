package com.musicapp.player.feature.scan

import android.os.Build
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanFolderResolverTest {

    @Test
    fun primaryStorageResolvesCorrectlyOnApi29Plus() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "primary:Music/MyFolder",
            sdkInt = Build.VERSION_CODES.Q,
            externalVolumeNames = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY, "1b04-1207"),
        )
        assertNotNull(selection)
        assertEquals(MediaStore.VOLUME_EXTERNAL_PRIMARY, selection?.volumeName)
        assertEquals("Music/MyFolder", selection?.directory)
    }

    @Test
    fun primaryStorageResolvesCorrectlyOnLegacyApi() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "primary:Music",
            sdkInt = 28,
            externalVolumeNames = emptySet(),
        )
        assertNotNull(selection)
        assertEquals("external", selection?.volumeName)
        assertEquals("Music", selection?.directory)
    }

    @Test
    fun externalStorageUppercaseUuidMapsToLowercaseVolumeName() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "1B04-1207:Songs/Rock",
            sdkInt = Build.VERSION_CODES.Q,
            externalVolumeNames = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY, "1b04-1207"),
        )
        assertNotNull(selection)
        assertEquals("1b04-1207", selection?.volumeName)
        assertEquals("Songs/Rock", selection?.directory)
    }

    @Test
    fun externalStorageRootResolvesWithEmptyDirectory() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "1B04-1207:",
            sdkInt = Build.VERSION_CODES.Q,
            externalVolumeNames = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY, "1b04-1207"),
        )
        assertNotNull(selection)
        assertEquals("1b04-1207", selection?.volumeName)
        assertEquals("", selection?.directory)
    }

    @Test
    fun unrecognisedStorageReturnsNull() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "UNKNOWN-UUID:Music",
            sdkInt = Build.VERSION_CODES.Q,
            externalVolumeNames = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY, "1b04-1207"),
        )
        assertNull(selection)
    }

    @Test
    fun invalidDocumentIdFormatReturnsNull() {
        val selection = ScanFolderResolver.resolveFromDocumentId(
            documentId = "invalid_document_id_without_colon",
            sdkInt = Build.VERSION_CODES.Q,
            externalVolumeNames = setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        )
        assertNull(selection)
    }
}
