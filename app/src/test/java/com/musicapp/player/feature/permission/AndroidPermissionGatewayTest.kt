package com.musicapp.player.feature.permission

import android.app.Activity
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPermissionGatewayTest {
    @Test
    fun requestHistoryIsPersistedBeforeLaunchingTheSystemRequest() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var launchedPermission: String? = null
        val gateway =
            AndroidPermissionGateway(activity) { permission ->
                assertTrue(gatewayFor(activity).wasRequested(MediaPermission.READ_MEDIA_AUDIO))
                launchedPermission = permission
            }

        assertFalse(gateway.wasRequested(MediaPermission.READ_MEDIA_AUDIO))
        gateway.requestPermission(MediaPermission.READ_MEDIA_AUDIO)

        assertEquals(MediaPermission.READ_MEDIA_AUDIO.manifestName, launchedPermission)
        assertTrue(gatewayFor(activity).wasRequested(MediaPermission.READ_MEDIA_AUDIO))
    }

    @Test
    fun applicationSettingsUsesThisApplicationsDetailsPage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val gateway = gatewayFor(activity)

        gateway.openApplicationSettings()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${activity.packageName}", intent.data.toString())
    }

    private fun gatewayFor(activity: Activity): AndroidPermissionGateway =
        AndroidPermissionGateway(activity, launchPermissionRequest = {})
}
