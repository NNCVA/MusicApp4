package com.musicapp.player.feature.playlists

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import coil3.compose.AsyncImage
import com.musicapp.player.core.image.AudioArtworkRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.musicapp.player.core.designsystem.component.BareIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.feature.category.CategoryNavigationAction
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
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onPlaylistClick = onPlaylistClick,
        bottomPadding = bottomPadding,
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
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.isSelectionMode) { viewModel.clearSelection() }
    LaunchedEffect(playlistId) { viewModel.open(playlistId) }
    PlaylistDetailScreen(
        state = state,
        contentInsets = contentInsets,
        bottomPadding = bottomPadding,
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
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val playlistListStartPadding =
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            dimensions.topBarNavigationVisualStartPadding
        } else {
            dimensions.contentHorizontalPadding
        }
    var editorPlaylistId by rememberSaveable { mutableLongStateOf(NO_PLAYLIST_ID) }
    var editorInitialName by rememberSaveable { mutableStateOf("") }
    var deletePlaylistId by rememberSaveable { mutableLongStateOf(NO_PLAYLIST_ID) }
    var pageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        CategoryHeader(
            title = stringResource(R.string.navigation_playlists),
            policy = policy,
            navigationAction = CategoryNavigationAction.DRAWER,
            onNavigationClick = openDrawer,
            trailingContent = {
                Box {
                    BareIconButton(
                        onClick = { pageMenuExpanded = true },
                        modifier = Modifier.size(dimensions.minimumTouchTarget),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_common_more_vertical),
                            contentDescription = stringResource(R.string.selection_more_actions),
                            tint = MusicTheme.colors.onSurface,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                    }
                    DropdownMenu(
                        expanded = pageMenuExpanded,
                        onDismissRequest = { pageMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_create)) },
                            onClick = {
                                pageMenuExpanded = false
                                editorPlaylistId = NEW_PLAYLIST_ID
                                editorInitialName = ""
                            },
                        )
                    }
                }
            },
        )
        if (!state.isLoaded) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (state.playlists.isEmpty()) {
            PlaylistEmptyState(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding)
                    .padding(bottom = bottomPadding),
            )
        } else {
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
            ) {
                items(state.playlists, key = { it.id.value }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        contentPadding =
                            PaddingValues(
                                start = playlistListStartPadding,
                                end = dimensions.topBarHorizontalPadding,
                            ),
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
private fun PlaylistEmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_status_empty_playlist),
            contentDescription = null,
            tint = MusicTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(MusicTheme.dimensions.playerHeaderHeight),
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(dimensions.trackListItemHeight)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        PlaylistArtwork(
            playlist = playlist,
            modifier = Modifier.size(dimensions.trackArtworkSize),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = playlist.displayName,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.category_track_count,
                    playlist.trackIds.size,
                    playlist.trackIds.size,
                ),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            var menuExpanded by rememberSaveable(playlist.id.value) { mutableStateOf(false) }
            BareIconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_more_vertical),
                    contentDescription = stringResource(R.string.selection_more_actions),
                    tint = MusicTheme.colors.onSurface,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(
    playlist: Playlist,
    modifier: Modifier,
) {
    val firstTrackId = remember(playlist.id, playlist.trackIds) { playlist.trackIds.firstOrNull() }
    val request = remember(playlist.id, firstTrackId) {
        AudioArtworkRequest.PlaylistArtworkRequest(
            playlistId = playlist.id,
            representativeTrackId = firstTrackId,
            dateModifiedMs = 0L,
        )
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier
            .clip(MusicTheme.shapes.small)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
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
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
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
                modifier = Modifier.padding(horizontal = dimensions.contentHorizontalPadding),
            )
        } ?: state.operationMessage?.let { message ->
            Text(
                text = stringResource(message.labelRes()),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensions.contentHorizontalPadding),
            )
        }
        if (state.tracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding)
                    .padding(bottom = bottomPadding),
                title = stringResource(R.string.playlist_empty_title),
                description = stringResource(R.string.playlist_empty_description),
            )
        } else {
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = dimensions.contentHorizontalPadding),
                    )
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
                    pluralStringResource(
                        R.plurals.playlist_playback_result,
                        feedback.playedCount,
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
            .padding(
                horizontal = dimensions.contentHorizontalPadding + dimensions.spaceSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick(track) })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
            )
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
