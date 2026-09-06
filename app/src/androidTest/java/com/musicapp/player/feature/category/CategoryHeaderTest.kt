package com.musicapp.player.feature.category

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryHeaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun backButtonRendersAcrossAllWindowLayoutPoliciesWhenOnBackProvided() {
        var currentPolicy by mutableStateOf(WindowLayoutPolicy.COMPACT_DRAWER)
        var clickCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MusicAppTheme {
                CategoryHeader(
                    title = "About",
                    policy = currentPolicy,
                    onBack = { clickCount++ },
                )
            }
        }

        val backButton = composeTestRule.onNode(hasContentDescription(context.getString(R.string.category_back)))

        // 1. COMPACT_DRAWER
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(1, clickCount)

        // 2. MEDIUM_SIDEBAR
        currentPolicy = WindowLayoutPolicy.MEDIUM_SIDEBAR
        composeTestRule.waitForIdle()
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(2, clickCount)

        // 3. EXPANDED_SIDEBAR
        currentPolicy = WindowLayoutPolicy.EXPANDED_SIDEBAR
        composeTestRule.waitForIdle()
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(3, clickCount)
    }

    @Test
    fun backActionRendersAcrossAllWindowLayoutPoliciesWhenNavigationActionIsBack() {
        var currentPolicy by mutableStateOf(WindowLayoutPolicy.COMPACT_DRAWER)
        var clickCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MusicAppTheme {
                CategoryHeader(
                    title = "Settings",
                    policy = currentPolicy,
                    navigationAction = CategoryNavigationAction.BACK,
                    onNavigationClick = { clickCount++ },
                )
            }
        }

        val backButton = composeTestRule.onNode(hasContentDescription(context.getString(R.string.category_back)))

        // 1. COMPACT_DRAWER
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(1, clickCount)

        // 2. MEDIUM_SIDEBAR
        currentPolicy = WindowLayoutPolicy.MEDIUM_SIDEBAR
        composeTestRule.waitForIdle()
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(2, clickCount)

        // 3. EXPANDED_SIDEBAR
        currentPolicy = WindowLayoutPolicy.EXPANDED_SIDEBAR
        composeTestRule.waitForIdle()
        backButton.assertIsDisplayed()
        backButton.performClick()
        assertEquals(3, clickCount)
    }

    @Test
    fun drawerActionOnlyRendersInCompactDrawerPolicy() {
        var currentPolicy by mutableStateOf(WindowLayoutPolicy.COMPACT_DRAWER)
        var drawerClicked = false

        composeTestRule.setContent {
            MusicAppTheme {
                CategoryHeader(
                    title = "Playlists",
                    policy = currentPolicy,
                    navigationAction = CategoryNavigationAction.DRAWER,
                    onNavigationClick = { drawerClicked = true },
                )
            }
        }

        val drawerButton = composeTestRule.onNode(hasContentDescription(context.getString(R.string.open_navigation)))

        // 1. In COMPACT_DRAWER, drawer button is rendered
        drawerButton.assertIsDisplayed()
        drawerButton.performClick()
        assertTrue(drawerClicked)

        // 2. In MEDIUM_SIDEBAR, drawer button is NOT rendered
        currentPolicy = WindowLayoutPolicy.MEDIUM_SIDEBAR
        composeTestRule.waitForIdle()
        drawerButton.assertDoesNotExist()

        // 3. In EXPANDED_SIDEBAR, drawer button is NOT rendered
        currentPolicy = WindowLayoutPolicy.EXPANDED_SIDEBAR
        composeTestRule.waitForIdle()
        drawerButton.assertDoesNotExist()
    }
}
