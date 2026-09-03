package com.musicapp.player.feature.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import com.musicapp.player.core.designsystem.component.MenuIconPalette
import com.musicapp.player.core.designsystem.component.MessageDialog
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.image.AudioArtworkRequest
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategoryNavigationAction
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
        onClearMessage = viewModel::clearMessage,
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
    onClearMessage: () -> Unit,
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
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
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
                    AppDropdownMenu(
                        expanded = pageMenuExpanded,
                        onDismissRequest = { pageMenuExpanded = false },
                    ) {
                        AppDropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_create)) },
                            iconTint = MenuIconPalette.Add,
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_common_add),
                                    contentDescription = null,
                                )
                            },
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
        if (state.isLoaded && state.playlists.isEmpty()) {
            PlaylistEmptyState(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding)
                    .padding(bottom = bottomPadding),
            )
        } else {
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
            ) {
                items(state.playlists, key = { it.id.value }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        contentPadding = PaddingValues(
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
        TextInputDialog(
            title = stringResource(if (creating) R.string.playlist_create_title else R.string.playlist_rename_title),
            initialText = editorInitialName,
            placeholder = stringResource(R.string.playlist_name_label),
            confirmLabel = stringResource(if (creating) R.string.playlist_create else R.string.playlist_save),
            cancelLabel = stringResource(R.string.playlist_cancel),
            onDismiss = { editorPlaylistId = NO_PLAYLIST_ID },
            onConfirm = { name ->
                if (creating) onCreate(name) else onRename(PlaylistId(editorPlaylistId), name)
                editorPlaylistId = NO_PLAYLIST_ID
            },
        )
    }
    if (deletePlaylistId != NO_PLAYLIST_ID) {
        ConfirmationDialog(
            title = stringResource(R.string.playlist_delete_title),
            text = stringResource(R.string.playlist_delete_description),
            confirmLabel = stringResource(R.string.playlist_delete),
            cancelLabel = stringResource(R.string.playlist_cancel),
            onConfirm = {
                onDelete(PlaylistId(deletePlaylistId))
                deletePlaylistId = NO_PLAYLIST_ID
            },
            onDismiss = { deletePlaylistId = NO_PLAYLIST_ID },
            isDestructive = true,
        )
    }
    state.operationMessage?.let { message ->
        val messageRes = when (message) {
            PlaylistOperationMessage.CREATED -> R.string.playlist_created
            PlaylistOperationMessage.RENAMED -> R.string.playlist_renamed
            PlaylistOperationMessage.DELETED -> R.string.playlist_deleted
            PlaylistOperationMessage.TRACKS_REMOVED -> R.string.playlist_tracks_removed
            PlaylistOperationMessage.FAILED -> R.string.playlist_operation_failed
        }
        MessageDialog(
            message = stringResource(messageRes),
            confirmLabel = stringResource(R.string.dismiss),
            onDismiss = onClearMessage,
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
            painter = painterResource(R.drawable.ic_playlist_album),
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
        modifier = Modifier
            .fillMaxWidth()
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
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    iconTint = MenuIconPalette.Rename,
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_common_edit),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_delete)) },
                    isDestructive = true,
                    iconTint = MenuIconPalette.Delete,
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_common_delete),
                            contentDescription = null,
                        )
                    },
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

private const val NO_PLAYLIST_ID = -1L
private const val NEW_PLAYLIST_ID = 0L
