package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollResetEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lazyListState_doesNotResetOnInitialComposition() {
        var key by mutableStateOf("initial_key")
        lateinit var capturedState: LazyListState

        composeRule.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 5)
            listState.ResetScrollOnChange(key)
            capturedState = listState

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(50) { index ->
                    Box(modifier = Modifier.height(50.dp)) {
                        Text(text = "Item $index")
                    }
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(5, capturedState.firstVisibleItemIndex)
    }

    @Test
    fun lazyListState_resetsToTopWhenKeyChanges() {
        var key by mutableStateOf("sort_title")
        lateinit var capturedState: LazyListState

        composeRule.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 10)
            listState.ResetScrollOnChange(key)
            capturedState = listState

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(50) { index ->
                    Box(modifier = Modifier.height(50.dp)) {
                        Text(text = "Item $index")
                    }
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(10, capturedState.firstVisibleItemIndex)

        composeRule.runOnIdle {
            key = "sort_artist"
        }
        composeRule.waitForIdle()

        assertEquals(0, capturedState.firstVisibleItemIndex)
    }

    @Test
    fun lazyGridState_resetsToTopWhenKeyChanges() {
        var key by mutableStateOf("sort_title")
        lateinit var capturedState: LazyGridState

        composeRule.setContent {
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 8)
            gridState.ResetScrollOnChange(key)
            capturedState = gridState

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(50) { index ->
                    Box(modifier = Modifier.height(50.dp)) {
                        Text(text = "Grid Item $index")
                    }
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(8, capturedState.firstVisibleItemIndex)

        composeRule.runOnIdle {
            key = "sort_year"
        }
        composeRule.waitForIdle()

        assertEquals(0, capturedState.firstVisibleItemIndex)
    }
}
