package com.musicapp.player.feature.settings

import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakeHistoryRepository
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.fakes.FakeSortPreferencesRepository
import com.musicapp.player.feature.settings.data.DataManagementUseCase
import com.musicapp.player.feature.settings.data.PathRuleChangeCoordinator
import com.musicapp.player.feature.tracks.TrackSort
import com.musicapp.player.feature.tracks.TrackSortDirection
import com.musicapp.player.feature.tracks.TrackSortField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `theme language aero fade and scan intents update reactive state`() = runTest(dispatcher) {
        val fixture = fixture(backgroundScope)
        val collection = backgroundScope.launch { fixture.viewModel.uiState.collect {} }
        advanceUntilIdle()

        fixture.viewModel.setColorSource(ColorSource.PRESET)
        fixture.viewModel.setPresetTheme(PresetTheme.VIOLET)
        fixture.viewModel.setThemeMode(ThemeMode.DARK)
        fixture.viewModel.setAppLanguage(AppLanguage.ENGLISH)
        fixture.viewModel.setAeroMode(AeroMode.SOLID)
        fixture.viewModel.setFadeThroughDurationMs(1_000)
        fixture.viewModel.setScanMode(ScanMode.SELECTED_DIRECTORIES)
        advanceUntilIdle()

        assertEquals(
            AppSettings(
                colorSource = ColorSource.PRESET,
                presetTheme = PresetTheme.VIOLET,
                themeMode = ThemeMode.DARK,
                appLanguage = AppLanguage.ENGLISH,
                aeroMode = AeroMode.SOLID,
                fadeThroughDurationMs = 1_000,
                scanMode = ScanMode.SELECTED_DIRECTORIES,
            ),
            fixture.viewModel.uiState.value.settings,
        )
        assertTrue(fixture.viewModel.uiState.value.pendingLibrarySync)
        assertTrue(fixture.viewModel.uiState.value.rescanPromptVisible)
        collection.cancel()
    }

    @Test
    fun `reset restores DataStore settings while retaining pending rules and business data`() = runTest(dispatcher) {
        val fixture = fixture(backgroundScope)
        val collection = backgroundScope.launch { fixture.viewModel.uiState.collect {} }
        advanceUntilIdle()
        fixture.viewModel.setThemeMode(ThemeMode.DARK)
        fixture.viewModel.addPathRule("external", "Music/", PathRuleKind.INCLUDE)
        fixture.sortPreferences.setTrackSort(
            TrackSort(field = TrackSortField.ARTIST, direction = TrackSortDirection.DESCENDING),
        )
        advanceUntilIdle()

        fixture.viewModel.cancelPathRescan()
        fixture.viewModel.requestConfirmation(SettingsConfirmation.RESET_SETTINGS)
        fixture.viewModel.confirmAction()
        advanceUntilIdle()

        assertEquals(AppSettings(), fixture.viewModel.uiState.value.settings)
        assertEquals(TrackSort(), fixture.sortPreferences.trackSort.value)
        assertTrue(fixture.viewModel.uiState.value.pendingLibrarySync)
        assertEquals("Music", fixture.media.observePathRules().first().single().directory)
        assertEquals(1, fixture.playlists.observePlaylists().first().size)
        assertEquals(1, fixture.history.observeHistory().first().size)
        assertEquals(SettingsMessage.SETTINGS_RESET, fixture.viewModel.uiState.value.message)
        collection.cancel()
    }

    @Test
    fun `resetting a selected directory scan marks the library pending`() = runTest(dispatcher) {
        val fixture = fixture(
            scope = backgroundScope,
            initialSettings = AppSettings(scanMode = ScanMode.SELECTED_DIRECTORIES),
        )
        val collection = backgroundScope.launch { fixture.viewModel.uiState.collect {} }
        advanceUntilIdle()

        fixture.viewModel.requestConfirmation(SettingsConfirmation.RESET_SETTINGS)
        fixture.viewModel.confirmAction()
        advanceUntilIdle()

        assertEquals(ScanMode.ALL, fixture.viewModel.uiState.value.settings.scanMode)
        assertTrue(fixture.viewModel.uiState.value.pendingLibrarySync)
        assertTrue(fixture.viewModel.uiState.value.rescanPromptVisible)
        collection.cancel()
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        initialSettings: AppSettings = AppSettings(),
    ): Fixture {
        val track = track(1)
        val settings = FakeSettingsRepository(initialSettings = initialSettings)
        val media = FakeMediaLibraryRepository(listOf(track))
        val history = FakeHistoryRepository(
            initialHistory = listOf(PlayHistory(track.id, 10, 1)),
            existingTrackIds = setOf(track.id),
        )
        val playlists = FakePlaylistRepository(
            initialPlaylists = listOf(
                Playlist(PlaylistId(1), "Keep", "keep", listOf(track.id), createdAtMs = 1),
            ),
            existingTrackIds = setOf(track.id),
        )
        val sync = RecordingSettingsSyncController()
        val pathCoordinator = PathRuleChangeCoordinator(media, settings, sync, scope)
        val sortPreferences = FakeSortPreferencesRepository()
        return Fixture(
            viewModel = SettingsViewModel(
                settingsRepository = settings,
                mediaLibraryRepository = media,
                pathRuleChangeCoordinator = pathCoordinator,
                dataManagementUseCase = DataManagementUseCase(history, playlists, sync),
                syncController = sync,
                sortPreferencesRepository = sortPreferences,
            ),
            media = media,
            history = history,
            playlists = playlists,
            sortPreferences = sortPreferences,
        )
    }

    private fun track(value: Long) = Track(
        id = TrackId("external", value),
        title = "Track $value",
        artistName = "Artist",
        durationMs = 1_000,
        dateAddedMs = value,
        dateModifiedMs = value,
        relativePath = "Music",
        displayName = "$value.mp3",
    )

    private data class Fixture(
        val viewModel: SettingsViewModel,
        val media: FakeMediaLibraryRepository,
        val history: FakeHistoryRepository,
        val playlists: FakePlaylistRepository,
        val sortPreferences: FakeSortPreferencesRepository,
    )
}
