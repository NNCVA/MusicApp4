package com.musicapp.player.media.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PlaybackServiceManifestTest {
    @Test
    fun manifestDeclaresTheExportedMediaPlaybackForegroundService() {
        val manifest = parseManifest()
        val services = manifest.getElementsByTagName("service")
        val playbackService =
            (0 until services.length)
                .map { services.item(it) as Element }
                .single { it.getAttribute("android:name") == ".media.service.MusicPlaybackService" }

        assertEquals("true", playbackService.getAttribute("android:exported"))
        assertEquals("mediaPlayback", playbackService.getAttribute("android:foregroundServiceType"))
        assertFalse(playbackService.hasAttribute("android:permission"))
        val actions = playbackService.getElementsByTagName("action")
        val actionNames =
            (0 until actions.length).map { (actions.item(it) as Element).getAttribute("android:name") }
        assertTrue("androidx.media3.session.MediaLibraryService" in actionNames)
        assertTrue("android.media.browse.MediaBrowserService" in actionNames)
    }

    @Test
    fun manifestHasForegroundPlaybackPermissionsWithoutNotificationPermission() {
        val permissions = parseManifest().getElementsByTagName("uses-permission")
        val permissionNames =
            (0 until permissions.length).map {
                (permissions.item(it) as Element).getAttribute("android:name")
            }

        assertEquals(1, permissionNames.count { it == "android.permission.FOREGROUND_SERVICE" })
        assertEquals(
            1,
            permissionNames.count { it == "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" },
        )
        assertFalse("android.permission.POST_NOTIFICATIONS" in permissionNames)
    }

    @Test
    fun noOtherNewApplicationComponentIsExported() {
        val manifest = parseManifest()
        val exportedComponents =
            listOf("activity", "service", "receiver", "provider").flatMap { tagName ->
                val nodes = manifest.getElementsByTagName(tagName)
                (0 until nodes.length).map { nodes.item(it) as Element }
            }.filter { it.getAttribute("android:exported") == "true" }
                .map { it.getAttribute("android:name") }

        assertEquals(listOf(".MainActivity", ".media.service.MusicPlaybackService"), exportedComponents)
    }

    private fun parseManifest() =
        File("src/main/AndroidManifest.xml").inputStream().use { input ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        }
}
