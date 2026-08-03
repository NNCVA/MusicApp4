package com.musicapp.player.manifest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class MediaPermissionManifestTest {
    @Test
    fun manifestDeclaresOnlyTheRequiredVersionedMediaReadPermissions() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val permissions = document.getElementsByTagName("uses-permission")
        val declarations =
            (0 until permissions.length).map { index ->
                val element = permissions.item(index) as Element
                PermissionDeclaration(
                    name = element.getAttribute("android:name"),
                    maxSdkVersion = element.getAttribute("android:maxSdkVersion").ifBlank { null },
                )
            }

        assertTrue(
            declarations.contains(
                PermissionDeclaration("android.permission.READ_MEDIA_AUDIO", maxSdkVersion = null),
            ),
        )
        assertTrue(
            declarations.contains(
                PermissionDeclaration("android.permission.READ_EXTERNAL_STORAGE", maxSdkVersion = "32"),
            ),
        )
        assertFalse(
            declarations.any { declaration ->
                declaration.name == "android.permission.POST_NOTIFICATIONS"
            },
        )
        assertEquals(1, declarations.count { it.name == "android.permission.READ_MEDIA_AUDIO" })
        assertEquals(1, declarations.count { it.name == "android.permission.READ_EXTERNAL_STORAGE" })
    }

    private data class PermissionDeclaration(
        val name: String,
        val maxSdkVersion: String?,
    )
}
