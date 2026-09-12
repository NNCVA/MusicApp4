package com.musicapp.player.data.equalizer

import com.musicapp.player.core.domain.model.EqualizerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryEqualizerRepository(
    initialSettings: EqualizerSettings = EqualizerSettings(),
) : EqualizerRepository {
    private val _settings = MutableStateFlow(initialSettings)
    override val settings: StateFlow<EqualizerSettings> = _settings.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    override suspend fun setPreset(presetIndex: Int, bandLevels: Map<Int, Int>) {
        _settings.value = _settings.value.copy(
            selectedPresetIndex = presetIndex,
            bandLevels = bandLevels,
        )
    }

    override suspend fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val currentLevels = _settings.value.bandLevels.toMutableMap()
        currentLevels[bandIndex] = levelMb.coerceIn(EqualizerSettings.MIN_BAND_LEVEL_MB, EqualizerSettings.MAX_BAND_LEVEL_MB)
        _settings.value = _settings.value.copy(
            bandLevels = currentLevels,
            selectedPresetIndex = EqualizerSettings.PRESET_CUSTOM,
        )
    }

    override suspend fun setBandLevels(bandLevels: Map<Int, Int>) {
        _settings.value = _settings.value.copy(
            bandLevels = bandLevels,
            selectedPresetIndex = EqualizerSettings.PRESET_CUSTOM,
        )
    }

    override suspend fun resetToFlat(defaultBandCount: Int) {
        val flatLevels = (0 until defaultBandCount).associateWith { EqualizerSettings.DEFAULT_BAND_LEVEL_MB }
        _settings.value = _settings.value.copy(
            bandLevels = flatLevels,
            selectedPresetIndex = EqualizerSettings.PRESET_CUSTOM,
        )
    }

    override suspend fun setBassBoost(enabled: Boolean, strength: Int) {
        _settings.value = _settings.value.copy(
            bassBoostEnabled = enabled,
            bassBoostStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH),
        )
    }

    override suspend fun setBassBoostEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(bassBoostEnabled = enabled)
    }

    override suspend fun setBassBoostStrength(strength: Int) {
        _settings.value = _settings.value.copy(
            bassBoostStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH),
        )
    }

    override suspend fun setVirtualizer(enabled: Boolean, strength: Int) {
        _settings.value = _settings.value.copy(
            virtualizerEnabled = enabled,
            virtualizerStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH),
        )
    }

    override suspend fun setVirtualizerEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(virtualizerEnabled = enabled)
    }

    override suspend fun setVirtualizerStrength(strength: Int) {
        _settings.value = _settings.value.copy(
            virtualizerStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH),
        )
    }
}
