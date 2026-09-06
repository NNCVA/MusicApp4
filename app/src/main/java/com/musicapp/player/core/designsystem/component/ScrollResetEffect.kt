package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Observes a [key] (such as sort options or filter criteria) and locks the list scroll viewport
 * position (index and offset) whenever the key changes after initial composition.
 * This prevents Compose LazyLayout from shifting the viewport by following moved item keys.
 */
@Composable
fun <T> LazyListState.LockScrollOnChange(
    key: T,
) {
    var lastKey by remember { mutableStateOf(key) }
    var anchorIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }
    var anchorOffset by remember { mutableIntStateOf(firstVisibleItemScrollOffset) }

    if (lastKey == key) {
        anchorIndex = firstVisibleItemIndex
        anchorOffset = firstVisibleItemScrollOffset
    }

    LaunchedEffect(key) {
        if (lastKey != key) {
            val targetIndex = anchorIndex
            val targetOffset = anchorOffset
            lastKey = key
            requestScrollToItem(targetIndex, targetOffset)
        }
    }
}

/**
 * Observes a [key] (such as sort options or filter criteria) and locks the grid scroll viewport
 * position (index and offset) whenever the key changes after initial composition.
 * This prevents Compose LazyLayout from shifting the viewport by following moved item keys.
 */
@Composable
fun <T> LazyGridState.LockScrollOnChange(
    key: T,
) {
    var lastKey by remember { mutableStateOf(key) }
    var anchorIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }
    var anchorOffset by remember { mutableIntStateOf(firstVisibleItemScrollOffset) }

    if (lastKey == key) {
        anchorIndex = firstVisibleItemIndex
        anchorOffset = firstVisibleItemScrollOffset
    }

    LaunchedEffect(key) {
        if (lastKey != key) {
            val targetIndex = anchorIndex
            val targetOffset = anchorOffset
            lastKey = key
            requestScrollToItem(targetIndex, targetOffset)
        }
    }
}

/**
 * Legacy compatibility extension. If [itemIndex] is explicitly provided, scrolls to [itemIndex].
 * Otherwise locks the current viewport position via [LockScrollOnChange].
 */
@Composable
fun <T> LazyListState.ResetScrollOnChange(
    key: T,
    itemIndex: Int? = null,
    scrollOffset: Int = 0,
) {
    if (itemIndex == null) {
        LockScrollOnChange(key)
    } else {
        var lastKey by remember { mutableStateOf(key) }
        LaunchedEffect(key) {
            if (lastKey != key) {
                lastKey = key
                requestScrollToItem(itemIndex, scrollOffset)
            }
        }
    }
}

/**
 * Legacy compatibility extension. If [itemIndex] is explicitly provided, scrolls to [itemIndex].
 * Otherwise locks the current viewport position via [LockScrollOnChange].
 */
@Composable
fun <T> LazyGridState.ResetScrollOnChange(
    key: T,
    itemIndex: Int? = null,
    scrollOffset: Int = 0,
) {
    if (itemIndex == null) {
        LockScrollOnChange(key)
    } else {
        var lastKey by remember { mutableStateOf(key) }
        LaunchedEffect(key) {
            if (lastKey != key) {
                lastKey = key
                requestScrollToItem(itemIndex, scrollOffset)
            }
        }
    }
}
