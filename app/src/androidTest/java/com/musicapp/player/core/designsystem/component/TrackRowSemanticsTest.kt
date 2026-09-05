package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackRowSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trackRowInSelectionModeExposesExactlyOneToggleableSemanticsNode() {
        val testTrack =
            Track(
                id = TrackId("primary", 1L),
                title = "Test Song",
                artistName = "Test Artist",
                albumTitle = "Test Album",
                durationMs = 120_000L,
                dateAddedMs = 1L,
                dateModifiedMs = 1L,
                relativePath = "Music/",
                displayName = "Test Song.mp3",
                availability = Availability.AVAILABLE,
            )

        var selected by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                TrackRow(
                    track = testTrack,
                    selected = selected,
                    selectionMode = true,
                    onClick = { selected = !selected },
                )
            }
        }

        // 验证未合并树中只有 1 个可切换节点，杜绝内部按钮多重语义噪音
        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(1)

        val node = composeTestRule.onNode(isToggleable())
        node.assertIsOff()
        node.performClick()
        node.assertIsOn()
        assertEquals(true, selected)
    }

    @Test
    fun selectionBottomBarDisablesActionsWhenEmpty() {
        var deleteClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                SelectionBottomBar(
                    actions =
                        listOf(
                            SelectionBarAction(
                                label = "Delete",
                                iconRes = R.drawable.ic_common_delete,
                                enabled = false,
                                onClick = { deleteClicked = true },
                            ),
                        ),
                    contentInsets = WindowInsets(0, 0, 0, 0),
                    applyBottomInset = false,
                )
            }
        }

        val deleteButton = composeTestRule.onNode(hasText("Delete"), useUnmergedTree = true)
        deleteButton.assertIsNotEnabled()
    }
}
