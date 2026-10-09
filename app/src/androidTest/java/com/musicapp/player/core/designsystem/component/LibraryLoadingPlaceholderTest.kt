package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLoadingPlaceholderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingProjectionHidesBackgroundThenRevealsItWhenReady() {
        val loaded = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = Color.Blue)) {
                Box(Modifier.size(48.dp).background(Color.Red).testTag("page")) {
                    Box(Modifier.fillMaxSize().libraryLoadingBackground(isLoaded = loaded.value))
                }
            }
        }

        assertEquals(Color.Blue, pageCenterColor())
        composeTestRule.runOnIdle { loaded.value = true }
        assertEquals(Color.Red, pageCenterColor())
        composeTestRule.runOnIdle { loaded.value = false }
        assertEquals(Color.Blue, pageCenterColor())
    }

    private fun pageCenterColor(): Color {
        val pixels = composeTestRule.onNodeWithTag("page").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
