package com.musicapp.player.feature.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import com.musicapp.player.data.equalizer.EqualizerRepository
import com.musicapp.player.media.audiofx.AudioEffectController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomEqualizerUiState(
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<EqualizerPreset> = emptyList(),
    val selectedPresetIndex: Int = EqualizerSettings.PRESET_CUSTOM,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
)

@HiltViewModel
class CustomEqualizerViewModel @Inject constructor(
    private val equalizerRepository: EqualizerRepository,
) : ViewModel() {

    private val hardwareCaps = AudioEffectController.queryHardwareCapabilities()
    val availablePresets: List<EqualizerPreset> = hardwareCaps.second

    val uiState: StateFlow<CustomEqualizerUiState> = equalizerRepository.settings
        .map { settings ->
            val bands = hardwareCaps.first.map { defaultBand ->
                val currentLevel = settings.bandLevels[defaultBand.index] ?: defaultBand.levelMb
                defaultBand.copy(levelMb = currentLevel)
            }
            CustomEqualizerUiState(
                isEnabled = settings.customEnabled,
                bands = bands,
                presets = availablePresets,
                selectedPresetIndex = settings.selectedPresetIndex,
                bassBoostEnabled = settings.bassBoostEnabled,
                bassBoostStrength = settings.bassBoostStrength,
                virtualizerEnabled = settings.virtualizerEnabled,
                virtualizerStrength = settings.virtualizerStrength,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomEqualizerUiState(
                bands = hardwareCaps.first,
                presets = availablePresets,
            ),
        )

    fun setCustomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerRepository.setCustomEnabled(enabled)
        }
    }

    fun selectPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            val bandMap = preset.bandLevels.mapIndexed { index, level -> index to level }.toMap()
            equalizerRepository.setPreset(preset.index, bandMap)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        viewModelScope.launch {
            equalizerRepository.setBandLevel(bandIndex, levelMb)
        }
    }

    fun resetToFlat() {
        viewModelScope.launch {
            equalizerRepository.resetToFlat(hardwareCaps.first.size)
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerRepository.setBassBoostEnabled(enabled)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        viewModelScope.launch {
            equalizerRepository.setBassBoostStrength(strength)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerRepository.setVirtualizerEnabled(enabled)
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        viewModelScope.launch {
            equalizerRepository.setVirtualizerStrength(strength)
        }
    }
}
