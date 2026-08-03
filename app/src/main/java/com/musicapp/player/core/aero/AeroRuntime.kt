package com.musicapp.player.core.aero

import com.musicapp.player.core.domain.model.AeroMode

data class AeroRuntimeSignals(
    val isAppInForeground: Boolean,
    val isScreenInteractive: Boolean,
    val isPowerSaveMode: Boolean,
    val isBatteryLow: Boolean,
    val areSystemAnimationsEnabled: Boolean,
) {
    companion object {
        val Active =
            AeroRuntimeSignals(
                isAppInForeground = true,
                isScreenInteractive = true,
                isPowerSaveMode = false,
                isBatteryLow = false,
                areSystemAnimationsEnabled = true,
            )
    }
}

enum class AeroDegradeReason {
    APP_BACKGROUND,
    SCREEN_OFF,
    POWER_SAVE,
    BATTERY_LOW,
    SYSTEM_ANIMATIONS_DISABLED,
}

data class AeroRuntimeState(
    val preferredMode: AeroMode,
    val effectiveMode: AeroMode,
    val degradeReasons: Set<AeroDegradeReason>,
) {
    val schedulesCanvasFrames: Boolean
        get() = effectiveMode != AeroMode.SOLID && degradeReasons.isEmpty()
}

object AeroDegradePolicy {
    fun resolve(
        preferredMode: AeroMode,
        signals: AeroRuntimeSignals,
    ): AeroRuntimeState {
        val reasons = buildSet {
            if (!signals.isAppInForeground) add(AeroDegradeReason.APP_BACKGROUND)
            if (!signals.isScreenInteractive) add(AeroDegradeReason.SCREEN_OFF)
            if (signals.isPowerSaveMode) add(AeroDegradeReason.POWER_SAVE)
            if (signals.isBatteryLow) add(AeroDegradeReason.BATTERY_LOW)
            if (!signals.areSystemAnimationsEnabled) {
                add(AeroDegradeReason.SYSTEM_ANIMATIONS_DISABLED)
            }
        }
        return AeroRuntimeState(
            preferredMode = preferredMode,
            effectiveMode = if (reasons.isEmpty()) preferredMode else AeroMode.SOLID,
            degradeReasons = reasons,
        )
    }
}
