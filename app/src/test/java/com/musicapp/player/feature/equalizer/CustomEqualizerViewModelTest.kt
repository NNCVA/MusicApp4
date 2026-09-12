package com.musicapp.player.feature.equalizer

import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import com.musicapp.player.data.equalizer.InMemoryEqualizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomEqualizerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryEqualizerRepository
    private lateinit var viewModel: CustomEqualizerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = InMemoryEqualizerRepository()
        viewModel = CustomEqualizerViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiStateReflectsDefaults() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertFalse(state.isEnabled)
        assertEquals(EqualizerSettings.PRESET_CUSTOM, state.selectedPresetIndex)
        assertEquals(5, state.bands.size)
        assertEquals(9, state.presets.size)
        assertFalse(state.bassBoostEnabled)
        assertEquals(0, state.bassBoostStrength)
        assertEquals(0f, state.bassBoostDb)
        assertFalse(state.virtualizerEnabled)
        assertEquals(0, state.virtualizerStrength)
        assertEquals(0f, state.virtualizerDb)
    }

    @Test
    fun toggleCustomEnabledUpdatesRepository() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setCustomEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.settings.value.customEnabled)
        assertTrue(viewModel.uiState.value.isEnabled)

        viewModel.setCustomEnabled(false)
        advanceUntilIdle()
        assertFalse(repository.settings.value.customEnabled)
        assertFalse(viewModel.uiState.value.isEnabled)
    }

    @Test
    fun selectPresetUpdatesRepositoryAndState() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val preset = viewModel.availablePresets.first { it.name == "Bass boost" }

        viewModel.selectPreset(preset)
        advanceUntilIdle()

        assertEquals(preset.index, repository.settings.value.selectedPresetIndex)
        assertEquals(preset.index, viewModel.uiState.value.selectedPresetIndex)
        assertEquals(400, viewModel.uiState.value.bands[0].levelMb)
        assertEquals(200, viewModel.uiState.value.bands[1].levelMb)
        assertEquals(-100, viewModel.uiState.value.bands[2].levelMb)
    }

    @Test
    fun setBandLevelUpdatesBandAndSwitchesToCustom() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setBandLevel(0, 450)
        advanceUntilIdle()

        assertEquals(EqualizerSettings.PRESET_CUSTOM, repository.settings.value.selectedPresetIndex)
        assertEquals(450, viewModel.uiState.value.bands[0].levelMb)
    }

    @Test
    fun setBandLevelClampsToDisplayBounds() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setBandLevel(0, 1500)
        advanceUntilIdle()
        assertEquals(1000, viewModel.uiState.value.bands[0].levelMb)

        viewModel.setBandLevel(0, -1500)
        advanceUntilIdle()
        assertEquals(-1000, viewModel.uiState.value.bands[0].levelMb)
    }

    @Test
    fun resetToFlatRestoresAllBandsToZero() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setBandLevel(0, 500)
        advanceUntilIdle()

        viewModel.resetToFlat()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bands.all { it.levelMb == 0 })
    }

    @Test
    fun setBassBoostDbUpdatesDbAndEnablesEffect() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setBassBoostDb(5.5f)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bassBoostEnabled)
        assertEquals(550, viewModel.uiState.value.bassBoostStrength)
        assertEquals(5.5f, viewModel.uiState.value.bassBoostDb)

        viewModel.setBassBoostDb(0f)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.bassBoostEnabled)
        assertEquals(0, viewModel.uiState.value.bassBoostStrength)
        assertEquals(0f, viewModel.uiState.value.bassBoostDb)
    }

    @Test
    fun setVirtualizerDbUpdatesDbAndEnablesEffect() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setVirtualizerDb(7.0f)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.virtualizerEnabled)
        assertEquals(700, viewModel.uiState.value.virtualizerStrength)
        assertEquals(7.0f, viewModel.uiState.value.virtualizerDb)

        viewModel.setVirtualizerDb(0f)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.virtualizerEnabled)
        assertEquals(0, viewModel.uiState.value.virtualizerStrength)
        assertEquals(0f, viewModel.uiState.value.virtualizerDb)
    }
}
