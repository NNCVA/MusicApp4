package com.musicapp.player.core.domain.model

import androidx.annotation.StringRes
import kotlin.math.roundToInt

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int,
) {
    init {
        require(index >= 0) { "index must be non-negative" }
        require(centerFreqHz > 0) { "centerFreqHz must be positive" }
        require(levelMb in EqualizerSettings.MIN_BAND_LEVEL_MB..EqualizerSettings.MAX_BAND_LEVEL_MB) {
            "levelMb must be within bounds [${EqualizerSettings.MIN_BAND_LEVEL_MB}, ${EqualizerSettings.MAX_BAND_LEVEL_MB}]"
        }
    }
}

data class EqualizerPreset(
    val index: Int,
    val name: String,
    val bandLevels: List<Int> = emptyList(),
    @param:StringRes val nameResId: Int = 0,
) {
    init {
        require(index >= EqualizerSettings.PRESET_CUSTOM) { "preset index must be >= PRESET_CUSTOM" }
        require(name.isNotBlank()) { "preset name must not be blank" }
    }
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val selectedPresetIndex: Int = PRESET_CUSTOM,
    val bandLevels: Map<Int, Int> = emptyMap(),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
) {
    init {
        require(bassBoostStrength in MIN_EFFECT_STRENGTH..MAX_EFFECT_STRENGTH) {
            "bassBoostStrength must be between $MIN_EFFECT_STRENGTH and $MAX_EFFECT_STRENGTH"
        }
        require(virtualizerStrength in MIN_EFFECT_STRENGTH..MAX_EFFECT_STRENGTH) {
            "virtualizerStrength must be between $MIN_EFFECT_STRENGTH and $MAX_EFFECT_STRENGTH"
        }
    }

    companion object {
        const val PRESET_CUSTOM: Int = -1
        const val MIN_BAND_LEVEL_MB: Int = -1500
        const val MAX_BAND_LEVEL_MB: Int = 1500
        const val MIN_DISPLAY_BAND_LEVEL_MB: Int = -1000
        const val MAX_DISPLAY_BAND_LEVEL_MB: Int = 1000
        const val DEFAULT_BAND_LEVEL_MB: Int = 0
        const val MIN_EFFECT_STRENGTH: Int = 0
        const val MAX_EFFECT_STRENGTH: Int = 1000

        const val MIN_EFFECT_DB: Float = 0.0f
        const val MAX_EFFECT_DB: Float = 10.0f

        fun effectStrengthToDb(strength: Int): Float {
            return (strength.coerceIn(MIN_EFFECT_STRENGTH, MAX_EFFECT_STRENGTH) / 100.0f)
        }

        fun effectDbToStrength(db: Float): Int {
            return (db.coerceIn(MIN_EFFECT_DB, MAX_EFFECT_DB) * 100.0f).roundToInt()
        }

        val DEFAULT_5_BAND_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)
    }
}
