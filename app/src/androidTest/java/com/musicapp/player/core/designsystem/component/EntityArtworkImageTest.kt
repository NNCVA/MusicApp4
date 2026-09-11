package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntityArtworkImageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun knownMissingArtworkUsesTheEntitySemantics() {
        composeTestRule.setContent {
            MaterialTheme {
                EntityArtworkImage(
                    model = null,
                    contentDescription = "Album artwork",
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Album artwork")).assertExists()
    }
}
