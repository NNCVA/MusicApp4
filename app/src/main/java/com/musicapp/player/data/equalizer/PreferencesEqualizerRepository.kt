package com.musicapp.player.data.equalizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.core.domain.model.EqualizerSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class PreferencesEqualizerRepository @Inject constructor(
    @param:EqualizerDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationCoroutineScope applicationScope: CoroutineScope,
) : EqualizerRepository {

    private val settingsFlow: Flow<EqualizerSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::toEqualizerSettings)

    override val settings: StateFlow<EqualizerSettings> = settingsFlow
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = EqualizerSettings(),
        )

    override suspend fun setSystemEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SYSTEM_ENABLED] = enabled
        }
    }

    override suspend fun setCustomEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_ENABLED] = enabled
        }
    }

    override suspend fun setPreset(presetIndex: Int, bandLevels: Map<Int, Int>) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_PRESET_INDEX] = presetIndex
            preferences[Keys.BAND_LEVELS] = encodeBandLevels(bandLevels)
        }
    }

    override suspend fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val coerced = levelMb.coerceIn(EqualizerSettings.MIN_BAND_LEVEL_MB, EqualizerSettings.MAX_BAND_LEVEL_MB)
        dataStore.edit { preferences ->
            val currentLevels = decodeBandLevels(preferences[Keys.BAND_LEVELS]).toMutableMap()
            currentLevels[bandIndex] = coerced
            preferences[Keys.BAND_LEVELS] = encodeBandLevels(currentLevels)
            // Manual adjustment automatically switches preset to custom
            preferences[Keys.SELECTED_PRESET_INDEX] = EqualizerSettings.PRESET_CUSTOM
        }
    }

    override suspend fun setBandLevels(bandLevels: Map<Int, Int>) {
        dataStore.edit { preferences ->
            preferences[Keys.BAND_LEVELS] = encodeBandLevels(bandLevels)
            preferences[Keys.SELECTED_PRESET_INDEX] = EqualizerSettings.PRESET_CUSTOM
        }
    }

    override suspend fun resetToFlat(defaultBandCount: Int) {
        val flatLevels = (0 until defaultBandCount).associateWith { EqualizerSettings.DEFAULT_BAND_LEVEL_MB }
        dataStore.edit { preferences ->
            preferences[Keys.BAND_LEVELS] = encodeBandLevels(flatLevels)
            preferences[Keys.SELECTED_PRESET_INDEX] = EqualizerSettings.PRESET_CUSTOM
        }
    }

    override suspend fun setBassBoost(enabled: Boolean, strength: Int) {
        val coercedStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH)
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_ENABLED] = enabled
            preferences[Keys.BASS_BOOST_STRENGTH] = coercedStrength
        }
    }

    override suspend fun setBassBoostEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_ENABLED] = enabled
        }
    }

    override suspend fun setBassBoostStrength(strength: Int) {
        val coercedStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH)
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_STRENGTH] = coercedStrength
        }
    }

    override suspend fun setVirtualizer(enabled: Boolean, strength: Int) {
        val coercedStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH)
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_ENABLED] = enabled
            preferences[Keys.VIRTUALIZER_STRENGTH] = coercedStrength
        }
    }

    override suspend fun setVirtualizerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_ENABLED] = enabled
        }
    }

    override suspend fun setVirtualizerStrength(strength: Int) {
        val coercedStrength = strength.coerceIn(EqualizerSettings.MIN_EFFECT_STRENGTH, EqualizerSettings.MAX_EFFECT_STRENGTH)
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_STRENGTH] = coercedStrength
        }
    }

    private fun toEqualizerSettings(preferences: Preferences): EqualizerSettings {
        return EqualizerSettings(
            systemEnabled = preferences[Keys.SYSTEM_ENABLED] ?: false,
            customEnabled = preferences[Keys.CUSTOM_ENABLED] ?: false,
            selectedPresetIndex = preferences[Keys.SELECTED_PRESET_INDEX] ?: EqualizerSettings.PRESET_CUSTOM,
            bandLevels = decodeBandLevels(preferences[Keys.BAND_LEVELS]),
            bassBoostEnabled = preferences[Keys.BASS_BOOST_ENABLED] ?: false,
            bassBoostStrength = (preferences[Keys.BASS_BOOST_STRENGTH] ?: 0).coerceIn(
                EqualizerSettings.MIN_EFFECT_STRENGTH,
                EqualizerSettings.MAX_EFFECT_STRENGTH,
            ),
            virtualizerEnabled = preferences[Keys.VIRTUALIZER_ENABLED] ?: false,
            virtualizerStrength = (preferences[Keys.VIRTUALIZER_STRENGTH] ?: 0).coerceIn(
                EqualizerSettings.MIN_EFFECT_STRENGTH,
                EqualizerSettings.MAX_EFFECT_STRENGTH,
            ),
        )
    }

    private fun encodeBandLevels(levels: Map<Int, Int>): String =
        levels.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    private fun decodeBandLevels(encoded: String?): Map<Int, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(";")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val band = parts[0].toIntOrNull()
                    val level = parts[1].toIntOrNull()
                    if (band != null && level != null) band to level else null
                } else null
            }
            .toMap()
    }

    private object Keys {
        val SYSTEM_ENABLED = booleanPreferencesKey("system_equalizer_enabled")
        val CUSTOM_ENABLED = booleanPreferencesKey("custom_equalizer_enabled")
        val SELECTED_PRESET_INDEX = intPreferencesKey("selected_preset_index")
        val BAND_LEVELS = stringPreferencesKey("band_levels")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
    }
}
