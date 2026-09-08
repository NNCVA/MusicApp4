package com.musicapp.player.feature.lyrics

import app.cash.turbine.test
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsRepository
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.MissingLyrics
import com.musicapp.player.core.lyrics.StaticLyrics
import com.musicapp.player.core.lyrics.SynchronizedLyrics
import com.musicapp.player.core.lyrics.TimedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loaded synchronized lyrics follow playback and line click requests exact seek`() = runTest(dispatcher) {
        val viewModel = LyricsViewModel(
            LyricsRepository {
                SynchronizedLyrics(
                    LyricsSource.EXTERNAL_LRC,
                    listOf(TimedLyricLine(1_000, "one"), TimedLyricLine(2_000, "two")),
                )
            },
        )
        viewModel.load(track())
        advanceUntilIdle()
        viewModel.updatePlaybackPosition(2_100)

        assertEquals(LyricsDisplayMode.SYNCHRONIZED, viewModel.uiState.value.mode)
        assertEquals("one", viewModel.uiState.value.previousLine)
        assertEquals("two", viewModel.uiState.value.currentLine)

        viewModel.seekRequests.test {
            viewModel.onLineClick(0)
            assertEquals(1_000L, awaitItem())
        }
    }

    @Test
    fun `manual scroll recovers auto center after five seconds`() = runTest(dispatcher) {
        val viewModel = synchronizedSubject()
        viewModel.load(track())
        advanceUntilIdle()

        viewModel.onManualScroll()
        assertFalse(viewModel.uiState.value.autoCenterEnabled)
        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(viewModel.uiState.value.autoCenterEnabled)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(viewModel.uiState.value.autoCenterEnabled)
        assertEquals(1L, viewModel.uiState.value.autoCenterRequest)
    }

    @Test
    fun `manual scroll pauses auto center but focus switch to new line recovers auto center immediately`() = runTest(dispatcher) {
        val viewModel = LyricsViewModel(
            LyricsRepository {
                SynchronizedLyrics(
                    LyricsSource.EXTERNAL_LRC,
                    listOf(TimedLyricLine(1_000, "one"), TimedLyricLine(2_000, "two")),
                )
            },
        )
        viewModel.load(track())
        advanceUntilIdle()
        viewModel.updatePlaybackPosition(1_100)
        assertEquals(0, viewModel.uiState.value.activeLineIndex)
        assertEquals(1L, viewModel.uiState.value.autoCenterRequest)

        viewModel.onManualScroll()
        assertFalse(viewModel.uiState.value.autoCenterEnabled)

        // Advancing position to next line (focus switch)
        viewModel.updatePlaybackPosition(2_100)
        assertEquals(1, viewModel.uiState.value.activeLineIndex)
        assertTrue(viewModel.uiState.value.autoCenterEnabled)
        assertEquals(2L, viewModel.uiState.value.autoCenterRequest)

        // 5s timer should have been canceled, so autoCenterRequest does not increment again
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2L, viewModel.uiState.value.autoCenterRequest)
    }

    @Test
    fun `manual scroll pauses auto center but clicking a line recovers auto center immediately`() = runTest(dispatcher) {
        val viewModel = synchronizedSubject()
        viewModel.load(track())
        advanceUntilIdle()

        viewModel.onManualScroll()
        assertFalse(viewModel.uiState.value.autoCenterEnabled)

        viewModel.onLineClick(0)
        assertTrue(viewModel.uiState.value.autoCenterEnabled)
        assertEquals(1L, viewModel.uiState.value.autoCenterRequest)

        // 5s timer should have been canceled
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1L, viewModel.uiState.value.autoCenterRequest)
    }

    @Test
    fun `static fallback exposes text and keeps synchronized window empty`() = runTest(dispatcher) {
        val viewModel = LyricsViewModel(
            LyricsRepository { StaticLyrics(LyricsSource.EMBEDDED_USLT, "plain") },
        )

        viewModel.load(track())
        advanceUntilIdle()
        viewModel.updatePlaybackPosition(5_000)

        assertEquals(LyricsDisplayMode.STATIC, viewModel.uiState.value.mode)
        assertEquals("plain", viewModel.uiState.value.staticText)
        assertEquals("", viewModel.uiState.value.previousLine)
        assertEquals("", viewModel.uiState.value.currentLine)
        assertEquals("", viewModel.uiState.value.nextLine)
    }

    @Test
    fun `settings updates modify state and persist to settings repository`() = runTest(dispatcher) {
        val fakeSettings = com.musicapp.player.feature.settings.FakeSettingsRepository()
        val viewModel = LyricsViewModel(
            repository = LyricsRepository { MissingLyrics },
            settingsRepository = fakeSettings,
        )
        advanceUntilIdle()

        viewModel.setFontSizeSp(26)
        advanceUntilIdle()
        assertEquals(26, viewModel.uiState.value.fontSizeSp)
        assertEquals(26, fakeSettings.currentSettings().lyricsFontSizeSp)

        viewModel.setTextCentered(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTextCentered)
        assertTrue(fakeSettings.currentSettings().lyricsTextCentered)

        viewModel.setFontWeight(700)
        advanceUntilIdle()
        assertEquals(700, viewModel.uiState.value.fontWeight)
        assertEquals(700, fakeSettings.currentSettings().lyricsFontWeight)
    }

    @Test
    fun `settings sheet visibility toggles correctly`() = runTest(dispatcher) {
        val viewModel = LyricsViewModel(LyricsRepository { MissingLyrics })
        assertFalse(viewModel.uiState.value.isSettingsSheetVisible)

        viewModel.showSettings()
        assertTrue(viewModel.uiState.value.isSettingsSheetVisible)

        viewModel.dismissSettings()
        assertFalse(viewModel.uiState.value.isSettingsSheetVisible)
    }

    private fun synchronizedSubject() = LyricsViewModel(
        LyricsRepository {
            SynchronizedLyrics(
                LyricsSource.EMBEDDED_SYLT,
                listOf(TimedLyricLine(1_000, "one")),
            )
        },
    )

    private fun track() = Track(
        id = TrackId("external", 1),
        title = "Track",
        artistName = "Artist",
        durationMs = 60_000,
        dateAddedMs = 1,
        dateModifiedMs = 1,
        relativePath = "Music/",
        displayName = "track.mp3",
    )
}
