package com.musicapp.player.feature.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun PlaylistsScreenRoute(
    viewModel: PlaylistsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onPlaylistClick: (PlaylistId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onPlaylistClick = onPlaylistClick,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@Composable
fun PlaylistDetailScreenRoute(
    playlistId: PlaylistId,
    viewModel: PlaylistDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.isSelectionMode) { viewModel.clearSelection() }
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    PlaylistDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onTrackClick = {
            if (state.isSelectionMode) {
                viewModel.toggleSelection(it.id)
            } else if (it.availability == Availability.AVAILABLE) {
                viewModel.playTrack(it.id)
            }
        },
        onTrackLongClick = { viewModel.toggleSelection(it.id) },
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRemoveSelected = viewModel::removeSelected,
        onRemoveTrack = { viewModel.removeTrack(it.id) },
        onAcknowledgePlaybackFeedback = viewModel::acknowledgePlaybackFeedback,
    )
}

@Composable
private fun PlaylistsScreen(
    state: PlaylistsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onPlaylistClick: (PlaylistId) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (PlaylistId, String) -> Unit,
    onDelete: (PlaylistId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    var editorPlaylistId by rememberSaveable { mutableLongStateOf(NO_PLAYLIST_ID) }
    var editorInitialName by rememberSaveable { mutableStateOf("") }
    var deletePlaylistId by rememberSaveable { mutableLongStateOf(NO_PLAYLIST_ID) }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = stringResource(R.string.navigation_playlists),
            policy = policy,
            openDrawer = openDrawer,
            trailingContent = {
                TextButton(
                    onClick = {
                        editorPlaylistId = NEW_PLAYLIST_ID
                        editorInitialName = ""
                    },
                ) { Text(stringResource(R.string.playlist_create)) }
            },
        )
        state.operationMessage?.let { message ->
            Text(
                text = stringResource(message.labelRes()),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        if (state.playlists.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.playlists_empty_title),
                description = stringResource(R.string.playlists_empty_description),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(dimensions.adaptiveGridMinimumCellWidth),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                items(state.playlists, key = { it.id.value }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = {
                            editorPlaylistId = playlist.id.value
                            editorInitialName = playlist.displayName
                        },
                        onDelete = { deletePlaylistId = playlist.id.value },
                    )
                }
            }
        }
    }

    if (editorPlaylistId != NO_PLAYLIST_ID) {
        val creating = editorPlaylistId == NEW_PLAYLIST_ID
        PlaylistNameDialog(
            title = stringResource(if (creating) R.string.playlist_create_title else R.string.playlist_rename_title),
            initialName = editorInitialName,
            confirmText = stringResource(if (creating) R.string.playlist_create else R.string.playlist_save),
            onDismiss = { editorPlaylistId = NO_PLAYLIST_ID },
            onConfirm = { name ->
                if (creating) onCreate(name) else onRename(PlaylistId(editorPlaylistId), name)
                editorPlaylistId = NO_PLAYLIST_ID
            },
        )
    }
    if (deletePlaylistId != NO_PLAYLIST_ID) {
        AlertDialog(
            onDismissRequest = { deletePlaylistId = NO_PLAYLIST_ID },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(PlaylistId(deletePlaylistId))
                        deletePlaylistId = NO_PLAYLIST_ID
                    },
                ) { Text(stringResource(R.string.playlist_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletePlaylistId = NO_PLAYLIST_ID }) {
                    Text(stringResource(R.string.playlist_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.categoryCardMinHeight),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(playlist.displayName, style = MusicTheme.typography.titleLarge, maxLines = 2)
            Text(
                stringResource(R.string.category_track_count, playlist.trackIds.size),
                style = MusicTheme.typography.labelMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall)) {
                TextButton(onClick = onRename) { Text(stringResource(R.string.playlist_rename)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.playlist_delete)) }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) }
        },
    )
}

@Composable
private fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onAcknowledgePlaybackFeedback: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title =
                if (state.isSelectionMode) {
                    stringResource(R.string.selection_count, state.selectedTrackIds.size)
                } else {
                    state.playlist?.displayName ?: stringResource(R.string.playlist_unknown_name)
                },
            onBack = onBack,
            trailingContent = {
                if (state.isSelectionMode) {
                    TextButton(onClick = onSelectAll) {
                        Text(stringResource(R.string.selection_select_all))
                    }
                    TextButton(onClick = onRemoveSelected) {
                        Text(stringResource(R.string.playlist_remove_track))
                    }
                    TextButton(onClick = onClearSelection) {
                        Text(stringResource(R.string.selection_close))
                    }
                } else {
                    TextButton(
                        onClick = onPlayAll,
                        enabled = state.tracks.any { it.availability == Availability.AVAILABLE },
                    ) { Text(stringResource(R.string.category_play_all)) }
                }
            },
        )
        state.lastRemovalResult?.let { result ->
            Text(
                text = stringResource(R.string.playlist_remove_result, result.changedCount, result.skippedCount),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        } ?: state.operationMessage?.let { message ->
            Text(
                text = stringResource(message.labelRes()),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        if (state.tracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.playlist_empty_title),
                description = stringResource(R.string.playlist_empty_description),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
            ) {
                items(state.tracks, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        selected = track.id in state.selectedTrackIds,
                        selectionMode = state.isSelectionMode,
                        onClick = onTrackClick,
                        onLongClick = onTrackLongClick,
                        onRemove = onRemoveTrack,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    state.playbackFeedback?.let { feedback ->
        AlertDialog(
            onDismissRequest = onAcknowledgePlaybackFeedback,
            title = { Text(stringResource(R.string.playlist_playback_result_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.playlist_playback_result,
                        feedback.playedCount,
                        feedback.skippedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onAcknowledgePlaybackFeedback) {
                    Text(stringResource(R.string.selection_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    track: Track,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    onRemove: (Track) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(dimensions.trackListItemHeight)
            .combinedClickable(
                onClick = { onClick(track) },
                onLongClick = { onLongClick(track) },
            )
            .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick(track) })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MusicTheme.typography.titleMedium, maxLines = 1)
            Text(
                track.artistName,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (!selectionMode) {
            TextButton(onClick = { onRemove(track) }) {
                Text(stringResource(R.string.playlist_remove_track))
            }
        }
    }
}

private fun PlaylistOperationMessage.labelRes(): Int =
    when (this) {
        PlaylistOperationMessage.CREATED -> R.string.playlist_created
        PlaylistOperationMessage.RENAMED -> R.string.playlist_renamed
        PlaylistOperationMessage.DELETED -> R.string.playlist_deleted
        PlaylistOperationMessage.TRACKS_REMOVED -> R.string.playlist_tracks_removed
        PlaylistOperationMessage.FAILED -> R.string.playlist_operation_failed
    }

private const val NO_PLAYLIST_ID = -1L
private const val NEW_PLAYLIST_ID = 0L
