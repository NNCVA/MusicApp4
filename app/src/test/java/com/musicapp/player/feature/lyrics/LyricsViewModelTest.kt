package com.musicapp.player.feature.lyrics

import app.cash.turbine.test
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsRepository
import com.musicapp.player.core.lyrics.LyricsSource
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
