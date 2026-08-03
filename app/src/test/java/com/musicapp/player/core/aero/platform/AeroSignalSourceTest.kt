package com.musicapp.player.core.aero.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.musicapp.player.core.aero.AeroRuntimeSignals
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
class AeroSignalSourceTest {
    @Test
    fun `source created after activity start initializes foreground and follows later lifecycle`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ApplicationProvider.getApplicationContext<Context>()
        markProcessUiVisible(context)
        val source = AndroidAeroSignalSource(context)
        try {
            assertTrue(source.signals.value.isAppInForeground)

            controller.pause().stop()
            assertFalse(source.signals.value.isAppInForeground)

            controller.start().resume().visible()
            assertTrue(source.signals.value.isAppInForeground)
        } finally {
            source.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `foreground restoration resamples and clears every platform degrade signal`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ApplicationProvider.getApplicationContext<Context>()
        markProcessUiVisible(context)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val shadowPowerManager = shadowOf(powerManager)
        val source = AndroidAeroSignalSource(context)
        try {
            shadowPowerManager.setIsPowerSaveMode(true)
            Settings.Global.putFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f,
            )
            context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            context.sendBroadcast(Intent(Intent.ACTION_BATTERY_LOW))
            context.contentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                null,
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(source.signals.value.isPowerSaveMode)
            assertFalse(source.signals.value.isScreenInteractive)
            assertTrue(source.signals.value.isBatteryLow)
            assertFalse(source.signals.value.areSystemAnimationsEnabled)

            controller.pause().stop()
            shadowPowerManager.setIsPowerSaveMode(false)
            shadowPowerManager.setIsInteractive(true)
            Settings.Global.putFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            context.sendBroadcast(Intent(Intent.ACTION_BATTERY_OKAY))
            controller.start().resume().visible()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(AeroRuntimeSignals.Active, source.signals.value)
        } finally {
            source.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [26])
    fun `api 26 late creation reads low battery level and recovers from changed level`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        publishBatteryLevel(context, level = 15)

        val source = AndroidAeroSignalSource(context)
        try {
            assertTrue(source.signals.value.isBatteryLow)

            publishBatteryLevel(context, level = 16)

            assertFalse(source.signals.value.isBatteryLow)
        } finally {
            source.close()
        }
    }

    private fun markProcessUiVisible(context: Context) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        shadowOf(activityManager).setProcesses(
            listOf(
                ActivityManager.RunningAppProcessInfo().apply {
                    pid = Process.myPid()
                    importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                },
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun publishBatteryLevel(context: Context, level: Int) {
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }
}
