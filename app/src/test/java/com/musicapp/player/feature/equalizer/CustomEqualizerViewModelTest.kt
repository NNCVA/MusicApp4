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
        assertFalse(state.bassBoostEnabled)
        assertEquals(0, state.bassBoostStrength)
        assertFalse(state.virtualizerEnabled)
        assertEquals(0, state.virtualizerStrength)
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

        val preset = EqualizerPreset(
            index = 1,
            name = "Classical",
            bandLevels = listOf(400, 300, -200, 400, 300),
        )

        viewModel.selectPreset(preset)
        advanceUntilIdle()

        assertEquals(1, repository.settings.value.selectedPresetIndex)
        assertEquals(1, viewModel.uiState.value.selectedPresetIndex)
        assertEquals(400, viewModel.uiState.value.bands[0].levelMb)
        assertEquals(300, viewModel.uiState.value.bands[1].levelMb)
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
    fun setBassBoostAndVirtualizerUpdatesState() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setBassBoostEnabled(true)
        viewModel.setBassBoostStrength(600)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bassBoostEnabled)
        assertEquals(600, viewModel.uiState.value.bassBoostStrength)

        viewModel.setVirtualizerEnabled(true)
        viewModel.setVirtualizerStrength(800)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.virtualizerEnabled)
        assertEquals(800, viewModel.uiState.value.virtualizerStrength)
    }
}
