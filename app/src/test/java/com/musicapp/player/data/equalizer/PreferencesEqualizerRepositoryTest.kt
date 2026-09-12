package com.musicapp.player.data.equalizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.musicapp.player.core.domain.model.EqualizerSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesEqualizerRepositoryTest {

    @Test
    fun firstReadReturnsDefaultEqualizerSettings() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val settings = repository.settings.value
        assertEquals(EqualizerSettings(), settings)
        assertFalse(settings.enabled)
        assertEquals(EqualizerSettings.PRESET_CUSTOM, settings.selectedPresetIndex)
        assertTrue(settings.bandLevels.isEmpty())
        assertFalse(settings.bassBoostEnabled)
        assertEquals(0, settings.bassBoostStrength)
        assertFalse(settings.virtualizerEnabled)
        assertEquals(0, settings.virtualizerStrength)
    }

    @Test
    fun toggleEnabled() = runTest {
        val repository = createRepository()

        repository.setEnabled(true)
        assertTrue(repository.settings.first { it.enabled }.enabled)

        repository.setEnabled(false)
        assertFalse(repository.settings.first { !it.enabled }.enabled)
    }

    @Test
    fun selectPresetUpdatesPresetIndexAndBands() = runTest {
        val repository = createRepository()

        val presetBands = mapOf(0 to 300, 1 to 100, 2 to 0, 3 to -100, 4 to 200)
        repository.setPreset(1, presetBands)

        val updated = repository.settings.first { it.selectedPresetIndex == 1 }
        assertEquals(1, updated.selectedPresetIndex)
        assertEquals(presetBands, updated.bandLevels)
    }

    @Test
    fun setBandLevelAutomaticallySwitchesToCustomPreset() = runTest {
        val repository = createRepository()

        val presetBands = mapOf(0 to 300, 1 to 100, 2 to 0, 3 to -100, 4 to 200)
        repository.setPreset(1, presetBands)
        assertEquals(1, repository.settings.first { it.selectedPresetIndex == 1 }.selectedPresetIndex)

        // Adjust band 0
        repository.setBandLevel(0, 500)
        val updated = repository.settings.first { it.selectedPresetIndex == EqualizerSettings.PRESET_CUSTOM }
        assertEquals(EqualizerSettings.PRESET_CUSTOM, updated.selectedPresetIndex)
        assertEquals(500, updated.bandLevels[0])
        assertEquals(100, updated.bandLevels[1])
    }

    @Test
    fun resetToFlatRestoresAllBandsToZero() = runTest {
        val repository = createRepository()

        repository.setBandLevel(0, 500)
        repository.setBandLevel(1, -200)

        repository.resetToFlat(5)
        val updated = repository.settings.first { it.bandLevels.isNotEmpty() && it.bandLevels.values.all { v -> v == 0 } }
        assertEquals(EqualizerSettings.PRESET_CUSTOM, updated.selectedPresetIndex)
        assertEquals(5, updated.bandLevels.size)
        assertTrue(updated.bandLevels.values.all { it == 0 })
    }

    @Test
    fun bassBoostAndVirtualizerRoundTrip() = runTest {
        val repository = createRepository()

        repository.setBassBoost(enabled = true, strength = 750)
        val bbState = repository.settings.first { it.bassBoostEnabled && it.bassBoostStrength == 750 }
        assertTrue(bbState.bassBoostEnabled)
        assertEquals(750, bbState.bassBoostStrength)

        repository.setVirtualizer(enabled = true, strength = 500)
        val virtState = repository.settings.first { it.virtualizerEnabled && it.virtualizerStrength == 500 }
        assertTrue(virtState.virtualizerEnabled)
        assertEquals(500, virtState.virtualizerStrength)
    }

    private fun TestScope.createRepository(): EqualizerRepository {
        val dataStore = InMemoryDataStore()
        return PreferencesEqualizerRepository(
            dataStore = dataStore,
            applicationScope = backgroundScope,
        )
    }

    private class InMemoryDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
        val current: Preferences get() = preferences.value
        private val preferences = MutableStateFlow(initialPreferences)
        private val updateMutex = Mutex()

        override val data: Flow<Preferences> = preferences

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            updateMutex.withLock {
                transform(preferences.value).also { updatedPreferences ->
                    preferences.value = updatedPreferences
                }
            }
    }
}
