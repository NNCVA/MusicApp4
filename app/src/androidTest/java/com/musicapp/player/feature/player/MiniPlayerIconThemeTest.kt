package com.musicapp.player.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerIconThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTrack = Track(
        id = TrackId("primary", 101L),
        title = "七里香",
        artistName = "周杰伦",
        albumId = AlbumId("primary", 202L),
        albumTitle = "七里香",
        durationMs = 299_000L,
        dateAddedMs = 1700000000000L,
        dateModifiedMs = 1700000000000L,
        relativePath = "Music/",
        displayName = "七里香.mp3",
        mimeType = "audio/mp4",
        sizeBytes = 10_000_000L,
    )

    @Test
    fun miniPlayer_inLightAndDarkTheme_preservesCorrectContentColorAndInteractions() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val playDesc = appContext.getString(R.string.playback_play)
        val queueDesc = appContext.getString(R.string.playback_queue)

        var toggleClicked = false
        var queueClicked = false
        var expandClicked = false
        var capturedContentColorLight: Color? = null
        var expectedOnSurfaceLight: Color? = null

        composeRule.setContent {
            MusicAppTheme(themeMode = ThemeMode.LIGHT) {
                expectedOnSurfaceLight = MusicTheme.colors.onSurface
                Surface(color = Color.Transparent) {
                    CompositionLocalProvider(
                        LocalContentColor provides MusicTheme.colors.onSurface,
                    ) {
                        capturedContentColorLight = LocalContentColor.current
                        MiniPlayer(
                            state = PlayerUiState(isPlaying = false),
                            track = sampleTrack,
                            isVisible = true,
                            onExpand = { expandClicked = true },
                            onTogglePlayback = { toggleClicked = true },
                            onOpenQueue = { queueClicked = true },
                        )
                    }
                }
            }
        }

        assertEquals(expectedOnSurfaceLight, capturedContentColorLight)

        composeRule.onNodeWithContentDescription(playDesc).performClick()
        assertTrue(toggleClicked)

        composeRule.onNodeWithContentDescription(queueDesc).performClick()
        assertTrue(queueClicked)
    }

    @Test
    fun miniPlayer_darkTheme_providesDarkThemeOnSurfaceColor() {
        var capturedContentColorDark: Color? = null
        var expectedOnSurfaceDark: Color? = null

        composeRule.setContent {
            MusicAppTheme(themeMode = ThemeMode.DARK) {
                expectedOnSurfaceDark = MusicTheme.colors.onSurface
                Surface(color = Color.Transparent) {
                    CompositionLocalProvider(
                        LocalContentColor provides MusicTheme.colors.onSurface,
                    ) {
                        capturedContentColorDark = LocalContentColor.current
                        MiniPlayer(
                            state = PlayerUiState(isPlaying = true),
                            track = sampleTrack,
                            isVisible = true,
                            onExpand = {},
                            onTogglePlayback = {},
                            onOpenQueue = {},
                        )
                    }
                }
            }
        }

        assertEquals(expectedOnSurfaceDark, capturedContentColorDark)
    }
}
