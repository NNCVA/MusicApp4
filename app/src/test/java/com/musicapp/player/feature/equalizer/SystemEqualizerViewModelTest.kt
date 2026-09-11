package com.musicapp.player.feature.equalizer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SystemEqualizerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: InMemoryEqualizerRepository
    private lateinit var viewModel: SystemEqualizerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = InMemoryEqualizerRepository()
        viewModel = SystemEqualizerViewModel(repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiStateReflectsRepository() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertFalse(state.isEnabled)
    }

    @Test
    fun toggleSystemEnabledUpdatesRepository() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSystemEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.settings.value.systemEnabled)
        assertTrue(viewModel.uiState.value.isEnabled)

        viewModel.setSystemEnabled(false)
        advanceUntilIdle()
        assertFalse(repository.settings.value.systemEnabled)
        assertFalse(viewModel.uiState.value.isEnabled)
    }

    @Test
    fun openSystemPanelReturnsFalseWhenNoHandlerInstalled() {
        val handled = viewModel.openSystemPanel()
        assertFalse(handled)
    }
}
