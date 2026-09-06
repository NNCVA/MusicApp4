package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Observes a [key] (such as sort options or filter criteria) and resets the list scroll position
 * to [itemIndex] and [scrollOffset] whenever the key changes after initial composition.
 */
@Composable
fun <T> LazyListState.ResetScrollOnChange(
    key: T,
    itemIndex: Int = 0,
    scrollOffset: Int = 0,
) {
    var lastKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (lastKey != key) {
            lastKey = key
            scrollToItem(itemIndex, scrollOffset)
        }
    }
}

/**
 * Observes a [key] (such as sort options or filter criteria) and resets the grid scroll position
 * to [itemIndex] and [scrollOffset] whenever the key changes after initial composition.
 */
@Composable
fun <T> LazyGridState.ResetScrollOnChange(
    key: T,
    itemIndex: Int = 0,
    scrollOffset: Int = 0,
) {
    var lastKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (lastKey != key) {
            lastKey = key
            scrollToItem(itemIndex, scrollOffset)
        }
    }
}
