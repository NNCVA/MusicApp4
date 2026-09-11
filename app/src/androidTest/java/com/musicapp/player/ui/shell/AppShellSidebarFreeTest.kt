package com.musicapp.player.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.theme.MusicAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellSidebarFreeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sidebarAndPlayerSheetAreNotMountedWhenVisibilityIsFalse() {
        composeRule.setContent {
            MusicAppTheme {
                AppShell(
                    sidebarVisible = false,
                    playerSheetVisible = false,
                    navigationContent = { _, _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_SIDEBAR)) {
                            Text("Sidebar Navigation")
                        }
                    },
                    content = { _, _, _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_MAIN_CONTENT)) {
                            Text("Main Screen Content")
                        }
                    },
                    playerSheetContent = { _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_PLAYER_SHEET)) {
                            Text("Player Sheet Content")
                        }
                    },
                )
            }
        }

        // 主内容必须正常显示
        composeRule.onNodeWithTag(TAG_MAIN_CONTENT).assertIsDisplayed()
        // 侧边栏与播放器图层在 sidebarVisible=false 和 playerSheetVisible=false 下必须完全不挂载
        composeRule.onNodeWithTag(TAG_SIDEBAR).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_PLAYER_SHEET).assertDoesNotExist()
    }

    @Test
    fun sidebarAndPlayerSheetAreMountedWhenVisibilityIsTrue() {
        composeRule.setContent {
            MusicAppTheme {
                AppShell(
                    sidebarVisible = true,
                    playerSheetVisible = true,
                    navigationContent = { _, _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_SIDEBAR)) {
                            Text("Sidebar Navigation")
                        }
                    },
                    content = { _, _, _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_MAIN_CONTENT)) {
                            Text("Main Screen Content")
                        }
                    },
                    playerSheetContent = { _ ->
                        Box(Modifier.fillMaxSize().testTag(TAG_PLAYER_SHEET)) {
                            Text("Player Sheet Content")
                        }
                    },
                )
            }
        }

        // 主内容与播放器图层必须正常显示或挂载
        composeRule.onNodeWithTag(TAG_MAIN_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_PLAYER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SIDEBAR).assertExists()
    }

    private companion object {
        const val TAG_SIDEBAR = "test_sidebar"
        const val TAG_MAIN_CONTENT = "test_main_content"
        const val TAG_PLAYER_SHEET = "test_player_sheet"
    }
}
