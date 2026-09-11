package com.musicapp.player.data.equalizer

import com.musicapp.player.core.domain.model.EqualizerSettings
import kotlinx.coroutines.flow.StateFlow

interface EqualizerRepository {
    val settings: StateFlow<EqualizerSettings>

    suspend fun setSystemEnabled(enabled: Boolean)

    suspend fun setCustomEnabled(enabled: Boolean)

    suspend fun setPreset(presetIndex: Int, bandLevels: Map<Int, Int>)

    suspend fun setBandLevel(bandIndex: Int, levelMb: Int)

    suspend fun setBandLevels(bandLevels: Map<Int, Int>)

    suspend fun resetToFlat(defaultBandCount: Int = 5)

    suspend fun setBassBoost(enabled: Boolean, strength: Int)

    suspend fun setBassBoostEnabled(enabled: Boolean)

    suspend fun setBassBoostStrength(strength: Int)

    suspend fun setVirtualizer(enabled: Boolean, strength: Int)

    suspend fun setVirtualizerEnabled(enabled: Boolean)

    suspend fun setVirtualizerStrength(strength: Int)
}
