package com.musicapp.player.feature.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.theme.MusicTheme

@Composable
fun LyricsPaneRoute(
    viewModel: LyricsViewModel,
    missingText: String,
    loadingText: String,
    returnToCurrentText: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LyricsPane(
        state = state,
        missingText = missingText,
        loadingText = loadingText,
        returnToCurrentText = returnToCurrentText,
        onManualScroll = viewModel::onManualScroll,
        onReturnToCurrent = viewModel::returnToCurrentLine,
        onLineClick = viewModel::onLineClick,
        modifier = modifier,
    )
}

@Composable
fun LyricsPane(
    state: LyricsUiState,
    missingText: String,
    loadingText: String,
    returnToCurrentText: String,
    onManualScroll: () -> Unit,
    onReturnToCurrent: () -> Unit,
    onLineClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    when (state.mode) {
        LyricsDisplayMode.LOADING -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    loadingText,
                    style = MusicTheme.typography.bodyMedium,
                    color = MusicTheme.colors.onSurfaceVariant,
                )
            }
        }

        LyricsDisplayMode.STATIC,
        LyricsDisplayMode.MISSING,
        -> Box(
            modifier = modifier.fillMaxSize().padding(dimensions.contentHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.staticText ?: missingText,
                style = MusicTheme.typography.bodyLarge,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }

        LyricsDisplayMode.SYNCHRONIZED -> {
            val listState = rememberLazyListState()
            val manualScrollConnection = remember(onManualScroll) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && available.y != 0f) onManualScroll()
                        return Offset.Zero
                    }
                }
            }
            LaunchedEffect(state.activeLineIndex, state.autoCenterEnabled, state.autoCenterRequest) {
                val index = state.activeLineIndex ?: return@LaunchedEffect
                if (!state.autoCenterEnabled) return@LaunchedEffect
                listState.animateScrollToItem(index)
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    ?: return@LaunchedEffect
                val viewportCenter = (
                    listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
                ) / 2f
                listState.animateScrollBy(item.offset + item.size / 2f - viewportCenter)
            }
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val centerableEdgePadding = ((maxHeight - dimensions.minimumTouchTarget) / 2)
                    .coerceAtLeast(dimensions.spaceLarge)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(manualScrollConnection),
                    contentPadding = PaddingValues(vertical = centerableEdgePadding),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    itemsIndexed(state.lines) { index, line ->
                        val isActive = index == state.activeLineIndex
                        Text(
                            text = line.text,
                            style = if (isActive) MusicTheme.typography.headlineMedium else MusicTheme.typography.bodyLarge,
                            color = if (isActive) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = dimensions.minimumTouchTarget)
                                .clickable { onLineClick(index) }
                                .padding(horizontal = dimensions.contentHorizontalPadding),
                        )
                    }
                }
                if (!state.autoCenterEnabled) {
                    TextButton(
                        onClick = onReturnToCurrent,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) { Text(returnToCurrentText) }
                }
            }
        }
    }
}
