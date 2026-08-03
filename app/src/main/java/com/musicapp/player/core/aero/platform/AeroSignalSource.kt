package com.musicapp.player.core.aero.platform

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.musicapp.player.core.aero.AeroRuntimeSignals
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface AeroSignalSource {
    val signals: StateFlow<AeroRuntimeSignals>
}

@Singleton
class AndroidAeroSignalSource @Inject constructor(
    @ApplicationContext context: Context,
) : AeroSignalSource, Closeable {
    private val application = context.applicationContext as Application
    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val closed = AtomicBoolean(false)
    private var startedActivityCount = 0

    private val mutableSignals =
        MutableStateFlow(
            AeroRuntimeSignals(
                isAppInForeground = currentProcessHasVisibleUi(),
                isScreenInteractive = powerManager.isInteractive,
                isPowerSaveMode = powerManager.isPowerSaveMode,
                isBatteryLow = currentBatteryLow(application),
                areSystemAnimationsEnabled = systemAnimationsEnabled(application),
            ),
        )
    override val signals: StateFlow<AeroRuntimeSignals> = mutableSignals.asStateFlow()

    private val activityCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
                refreshPlatformSignals(isAppInForeground = true)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    refreshPlatformSignals(isAppInForeground = false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON ->
                        mutableSignals.update { it.copy(isScreenInteractive = true) }
                    Intent.ACTION_SCREEN_OFF ->
                        mutableSignals.update { it.copy(isScreenInteractive = false) }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        mutableSignals.update { it.copy(isPowerSaveMode = powerManager.isPowerSaveMode) }
                    Intent.ACTION_BATTERY_LOW ->
                        mutableSignals.update { it.copy(isBatteryLow = true) }
                    Intent.ACTION_BATTERY_OKAY ->
                        mutableSignals.update { it.copy(isBatteryLow = false) }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        batteryLowFromChangedIntent(intent)?.let { isBatteryLow ->
                            mutableSignals.update {
                                it.copy(isBatteryLow = isBatteryLow)
                            }
                        }
                    }
                }
            }
        }

    private val animatorScaleObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mutableSignals.update {
                    it.copy(areSystemAnimationsEnabled = systemAnimationsEnabled(application))
                }
            }
        }

    init {
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        registerSignalReceiver()
        application.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animatorScaleObserver,
        )
        refreshPlatformSignals(isAppInForeground = currentProcessHasVisibleUi())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        application.unregisterReceiver(receiver)
        application.contentResolver.unregisterContentObserver(animatorScaleObserver)
    }

    private fun refreshPlatformSignals(isAppInForeground: Boolean) {
        mutableSignals.update {
            it.copy(
                isAppInForeground = isAppInForeground,
                isScreenInteractive = powerManager.isInteractive,
                isPowerSaveMode = powerManager.isPowerSaveMode,
                isBatteryLow = currentBatteryLow(application),
                areSystemAnimationsEnabled = systemAnimationsEnabled(application),
            )
        }
    }

    @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
    private fun registerSignalReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                application,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            application.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        @Suppress("DEPRECATION")
        fun currentBatteryLow(context: Context): Boolean {
            val batteryChanged =
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                )
            return batteryChanged?.let(::batteryLowFromChangedIntent) ?: false
        }

        private fun batteryLowFromChangedIntent(intent: Intent): Boolean? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return if (intent.hasExtra(EXTRA_BATTERY_LOW_COMPAT)) {
                    intent.getBooleanExtra(EXTRA_BATTERY_LOW_COMPAT, false)
                } else {
                    null
                }
            }

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            return level.toLong() * 100 <= scale.toLong() * LOW_BATTERY_THRESHOLD_PERCENT
        }

        fun systemAnimationsEnabled(context: Context): Boolean =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                DEFAULT_ANIMATOR_DURATION_SCALE,
            ) > 0f

        fun currentProcessHasVisibleUi(): Boolean {
            val processInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(processInfo)
            return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        const val DEFAULT_ANIMATOR_DURATION_SCALE = 1f
        const val EXTRA_BATTERY_LOW_COMPAT = "battery_low"
        const val LOW_BATTERY_THRESHOLD_PERCENT = 15
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AeroSignalModule {
    @Binds
    @Singleton
    abstract fun bindAeroSignalSource(implementation: AndroidAeroSignalSource): AeroSignalSource
}
