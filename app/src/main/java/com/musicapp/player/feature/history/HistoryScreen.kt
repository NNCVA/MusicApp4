package com.musicapp.player.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.MessageDialog
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreenRoute(
    viewModel: HistoryViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    BackHandler(enabled = state.isSelectionMode || state.clearConfirmationVisible) { viewModel.onBack() }
    HistoryScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        bottomPadding = bottomPadding,
        onQueryChange = viewModel::setQuery,
        onTrackClick = { entry ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(entry.trackId)
            } else {
                viewModel.playTrack(entry.trackId)
            }
        },
        onTrackLongClick = { entry ->
            if (entry.isActionable) {
                if (!state.isSelectionMode) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                viewModel.toggleSelection(entry.trackId)
            }
        },
        onSelectAll = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
        onAddToQueue = { viewModel.executeSelected(BatchTrackAction.AddToQueue) },
        onPlayNext = { viewModel.executeSelected(BatchTrackAction.PlayNext) },
        onHide = { viewModel.executeSelected(BatchTrackAction.Hide) },
        onAddToPlaylist = { playlistId ->
            viewModel.executeSelected(BatchTrackAction.AddToPlaylist(playlistId))
        },
        onRequestClearHistory = viewModel::requestClearHistory,
        onCancelClearHistory = viewModel::cancelClearHistory,
        onConfirmClearHistory = viewModel::confirmClearHistory,
        onAcknowledgeBatchResult = viewModel::acknowledgeBatchResult,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTrackClick: (HistoryEntry) -> Unit,
    onTrackLongClick: (HistoryEntry) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHide: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onRequestClearHistory: () -> Unit,
    onCancelClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onAcknowledgeBatchResult: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        CategoryHeader(
            title =
                if (state.isSelectionMode) {
                    pluralStringResource(
                        R.plurals.selection_count,
                        state.selectedTrackIds.size,
                        state.selectedTrackIds.size,
                    )
                } else {
                    stringResource(R.string.navigation_history)
                },
            policy = policy,
            navigationAction = CategoryNavigationAction.BACK,
            onNavigationClick = onBack,
            trailingContent = {
                if (state.isSelectionMode) {
                    HistorySelectionActions(
                        actionsEnabled =
                            state.selectedTrackIdsInVisibleOrder.isNotEmpty() && !state.isBatchActionRunning,
                        playlists = state.playlists,
                        onSelectAll = onSelectAll,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onHide = onHide,
                        onAddToPlaylist = onAddToPlaylist,
                        onClearSelection = onClearSelection,
                    )
                } else {
                    TextButton(
                        onClick = onRequestClearHistory,
                        enabled = state.entries.isNotEmpty() && !state.isClearing,
                    ) { Text(stringResource(R.string.history_clear)) }
                }
            },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.history_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
        )
        when {
            state.isLoading ->
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            state.entries.isEmpty() ->
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = bottomPadding),
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_description),
                )
            state.visibleEntries.isEmpty() ->
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = bottomPadding),
                    title = stringResource(R.string.history_no_results_title),
                    description = stringResource(R.string.history_no_results_description),
                )
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).bounceOverscroll(),
                    contentPadding = PaddingValues(
                        top = dimensions.spaceSmall,
                        bottom = dimensions.spaceSmall + bottomPadding,
                    ),
                ) {
                    items(
                        items = state.visibleEntries,
                        key = { entry -> "${entry.trackId.volumeName}:${entry.trackId.mediaStoreId}" },
                    ) { entry ->
                        HistoryRow(
                            entry = entry,
                            selected = entry.trackId in state.selectedTrackIds,
                            selectionMode = state.isSelectionMode,
                            onClick = { onTrackClick(entry) },
                            onLongClick = { onTrackLongClick(entry) },
                        )
                    }
                }
        }
    }
    if (state.clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onCancelClearHistory,
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_description)) },
            confirmButton = {
                TextButton(onClick = onConfirmClearHistory) {
                    Text(stringResource(R.string.history_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelClearHistory) {
                    Text(stringResource(R.string.history_clear_cancel))
                }
            },
        )
    }
    val batchResult = state.batchResult
    LaunchedEffect(batchResult) {
        if (batchResult is BatchTrackActionResult.EmptySelection) onAcknowledgeBatchResult()
    }
    if (batchResult != null && batchResult !is BatchTrackActionResult.EmptySelection) {
        HistoryBatchResultDialog(batchResult, onAcknowledgeBatchResult)
    }
}

@Composable
private fun HistorySelectionActions(
    actionsEnabled: Boolean,
    playlists: List<Playlist>,
    onSelectAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHide: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onClearSelection: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TextButton(onClick = { menuExpanded = true }, enabled = actionsEnabled) {
        Text(stringResource(R.string.selection_more_actions))
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_select_all)) },
            onClick = { menuExpanded = false; onSelectAll() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_add_to_queue)) },
            enabled = actionsEnabled,
            onClick = { menuExpanded = false; onAddToQueue() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_play_next)) },
            enabled = actionsEnabled,
            onClick = { menuExpanded = false; onPlayNext() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_hide)) },
            enabled = actionsEnabled,
            onClick = { menuExpanded = false; onHide() },
        )
        if (playlists.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_no_playlists)) },
                onClick = {},
                enabled = false,
            )
        } else {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.selection_add_to_playlist_named,
                                playlist.displayName,
                            ),
                        )
                    },
                    enabled = actionsEnabled,
                    onClick = { menuExpanded = false; onAddToPlaylist(playlist.id) },
                )
            }
        }
    }
    TextButton(onClick = onClearSelection) { Text(stringResource(R.string.selection_close)) }
}

@Composable
private fun HistoryBatchResultDialog(
    result: BatchTrackActionResult,
    onDismiss: () -> Unit,
) {
    val message =
        when (result) {
            is BatchTrackActionResult.Completed ->
                stringResource(
                    R.string.batch_result_counts,
                    result.affectedCount,
                    result.skippedCount,
                )
            is BatchTrackActionResult.Failed -> stringResource(R.string.batch_result_failed)
            BatchTrackActionResult.EmptySelection -> return
        }
    MessageDialog(
        message = message,
        confirmLabel = stringResource(R.string.selection_close),
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val track = entry.track
    val enabled = track != null && track.availability == Availability.AVAILABLE
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = dimensions.trackListItemHeight)
                .clip(MusicTheme.shapes.medium)
                .combinedClickable(
                    enabled = entry.isActionable,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        color = if (selected) MusicTheme.colors.secondaryContainer else MusicTheme.colors.surface,
        shape = MusicTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = dimensions.contentHorizontalPadding + dimensions.spaceSmall,
                    vertical = dimensions.spaceExtraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    enabled = entry.isActionable,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        track?.title ?: stringResource(
                            R.string.history_missing_track,
                            entry.trackId.volumeName,
                            entry.trackId.mediaStoreId,
                        ),
                    style = MusicTheme.typography.titleMedium,
                    color = if (enabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
                if (track != null) {
                    Text(
                        text = track.artistName,
                        style = MusicTheme.typography.bodySmall,
                        color = MusicTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(entry.history.lastPlayedAtMs)),
                    style = MusicTheme.typography.labelSmall,
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.history_play_count,
                        entry.history.playCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        entry.history.playCount,
                    ),
                style = MusicTheme.typography.labelMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
    }
}
