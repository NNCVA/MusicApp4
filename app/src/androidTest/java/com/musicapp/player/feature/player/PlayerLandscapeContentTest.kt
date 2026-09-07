package com.musicapp.player.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsRepository
import com.musicapp.player.core.lyrics.MissingLyrics
import com.musicapp.player.core.lyrics.ResolvedLyrics
import com.musicapp.player.feature.lyrics.LyricsViewModel
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerLandscapeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTrack = Track(
        id = TrackId("primary", 101L),
        title = "兰亭序",
        artistName = "周杰伦",
        albumId = AlbumId("primary", 202L),
        albumTitle = "魔杰座",
        durationMs = 253_000L,
        dateAddedMs = 1700000000000L,
        dateModifiedMs = 1700000000000L,
        relativePath = "Music/",
        displayName = "兰亭序.mp3",
        mimeType = "audio/mp4",
        sizeBytes = 10_000_000L,
    )

    private val fakeLyricsRepository = object : LyricsRepository {
        override suspend fun load(track: Track): ResolvedLyrics = MissingLyrics
    }

    @Test
    fun landscapeContent_rendersTitleArtistAndControls_andNavigatesToQueue() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val queueDescription = appContext.getString(R.string.playback_queue)
        val playDescription = appContext.getString(R.string.playback_play)
        val previousDescription = appContext.getString(R.string.playback_previous)
        val nextDescription = appContext.getString(R.string.playback_next)

        val lyricsViewModel = LyricsViewModel(fakeLyricsRepository)
        var pagerCurrentPage = 0

        composeRule.setContent {
            val pager = rememberPagerState(initialPage = 0, pageCount = { 3 })
            pagerCurrentPage = pager.currentPage

            MusicAppTheme {
                Box(modifier = Modifier.size(width = 840.dp, height = 390.dp)) {
                    PlayerLandscapeContent(
                        state = PlayerUiState(
                            currentTrack = sampleTrack,
                            isPlaying = false,
                            positionMs = 132_000L,
                            durationMs = 253_000L,
                            playbackMode = PlaybackMode.LIST_REPEAT,
                        ),
                        lyricsViewModel = lyricsViewModel,
                        track = sampleTrack,
                        pager = pager,
                        isScrollableContentActive = false,
                        onTogglePlayback = {},
                        onPrevious = {},
                        onNext = {},
                        onSeek = {},
                        onCycleMode = {},
                        onJumpToQueueItem = {},
                        onRemoveQueueItem = {},
                        onShowInfo = {},
                        showFeedback = {},
                        onSheetDrag = { 0f },
                        onSheetSettle = {},
                        sheetProgress = { 1f },
                    )
                }
            }
        }

        // Verify title & artist
        composeRule.onNodeWithText("兰亭序").assertExists()
        composeRule.onNodeWithText("周杰伦").assertExists()

        // Verify controls
        composeRule.onNodeWithContentDescription(playDescription).assertExists()
        composeRule.onNodeWithContentDescription(previousDescription).assertExists()
        composeRule.onNodeWithContentDescription(nextDescription).assertExists()

        // Verify queue button exists and click jumps to Page 2
        composeRule.onNodeWithContentDescription(queueDescription).performClick()
        composeRule.waitForIdle()

        assertEquals(FullPlayerPage.QUEUE.ordinal, pagerCurrentPage)
    }
}
