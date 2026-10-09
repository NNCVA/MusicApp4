package com.musicapp.player.feature.albums

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumDetailLoadingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun heroWaitsForFirstRoomProjection() {
        val state = mutableStateOf(AlbumDetailUiState(title = "Pending album"))
        composeTestRule.setContent {
            MaterialTheme {
                AlbumDetailScreen(
                    state = state.value,
                    contentInsets = WindowInsets(0),
                    onBack = {},
                    onTrackClick = {},
                    onAddToQueue = {},
                    onPlayNext = {},
                    onAddToPlaylist = { _, _ -> },
                    onHideTrack = {},
                    onShowTrackInfo = {},
                    onDismissTrackInfo = {},
                    onArtistClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Pending album").assertDoesNotExist()
        composeTestRule.runOnIdle { state.value = state.value.copy(isLoaded = true) }
        composeTestRule.onNodeWithText("Pending album").assertIsDisplayed()
    }
}
