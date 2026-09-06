package com.musicapp.player.feature.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.LockScrollOnChange
import com.musicapp.player.core.designsystem.component.ResetScrollOnChange
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.SearchableTopBar
import com.musicapp.player.core.designsystem.component.SelectionBarAction
import com.musicapp.player.core.designsystem.component.SelectionBottomBar
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.theme.MusicTheme

@Composable
fun FolderDetailScreenRoute(
    folderId: FolderId,
    viewModel: FolderDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (AlbumId) -> Unit = {},
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
    isActive: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isActive) {
        if (!isActive && state.isSelectionMode) {
            viewModel.exitSelection()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.exitSelection()
        }
    }

    LaunchedEffect(state.batchResult) {
        val result = state.batchResult ?: return@LaunchedEffect
        when (result) {
            is BatchTrackActionResult.Completed -> {
                onShowMessage(
                    R.string.batch_result_counts,
                    listOf(result.affectedCount, result.skippedCount),
                )
            }
            is BatchTrackActionResult.Failed -> {
                onShowMessage(R.string.batch_result_failed, emptyList())
            }
            BatchTrackActionResult.EmptySelection -> Unit
        }
        viewModel.acknowledgeBatchResult()
    }

    BackHandler(enabled = state.isSelectionMode || state.infoTrack != null) {
        if (state.isSelectionMode) {
            viewModel.exitSelection()
        } else {
            viewModel.dismissTrackInfo()
        }
    }
    LaunchedEffect(folderId) { viewModel.open(folderId) }
    FolderDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onNavigateToArtist = onArtistClick,
        onNavigateToAlbum = onAlbumClick,
        onBack = {
            if (state.isSelectionMode) {
                viewModel.exitSelection()
            }
            onBack()
        },
        onPlayAll = viewModel::playAll,
        onTrackSortSelected = viewModel::selectTrackSort,
        onFolderClick = { childId ->
            if (state.isSelectionMode) {
                viewModel.exitSelection()
            }
            onFolderClick(childId)
        },
        onTrackClick = { track ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(track.id)
            } else {
                viewModel.playTrack(track.id)
            }
        },
        onTrackLongClick = { track ->
            if (!state.isSelectionMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.startSelection(track.id)
            }
        },
        onToggleSelectAll = viewModel::toggleSelectAll,
        onClearSelection = viewModel::exitSelection,
        onAddSelectedToPlaylist = viewModel::addSelectedToPlaylist,
        onAddSelectedToQueue = viewModel::addSelectedToQueue,
        onTrackAddToQueue = viewModel::addTrackToQueue,
        onTrackPlayNext = viewModel::playTrackNext,
        onTrackAddToPlaylist = viewModel::addTrackToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onTrackShowInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        bottomPadding = bottomPadding,
    )
}

@Composable
private fun FolderDetailScreen(
    state: FolderDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackSortSelected: (CategoryTrackSortField) -> Unit,
    onFolderClick: (FolderId) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onAddSelectedToPlaylist: (PlaylistId) -> Unit = {},
    onAddSelectedToQueue: () -> Unit = {},
    onTrackAddToQueue: (TrackId) -> Unit = {},
    onTrackPlayNext: (TrackId) -> Unit = {},
    onTrackAddToPlaylist: (TrackId, PlaylistId) -> Unit = { _, _ -> },
    onCreatePlaylist: (String) -> Unit = {},
    onTrackShowInfo: (Track) -> Unit = {},
    onDismissTrackInfo: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (AlbumId) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    listState.LockScrollOnChange(state.trackSort)
    val overscrollEffect = rememberBounceOverscrollEffect(listState)

    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by remember { mutableStateOf<TrackId?>(null) }

    val hasPlayableTracks = state.directTracks.any { it.availability == Availability.AVAILABLE }
    val hasItems = state.childFolders.isNotEmpty() || state.directTracks.isNotEmpty()

    val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
    val hasMiniPlayer = bottomPadding > bottomInset + 1.dp
    val selectionBarHeight = dimensions.minimumTouchTarget
    val dynamicBottomPadding = bottomPadding + if (state.isSelectionMode) selectionBarHeight else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            SearchableTopBar(
                title = folderDetailTitle(state),
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = onBack,
                onOpenSearch = null,
            )

            if (hasItems) {
                if (state.directTracks.isNotEmpty()) {
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    ListActionBar(
                        isSelectionMode = state.isSelectionMode,
                        itemCount = state.directTracks.size,
                        showPlayAll = true,
                        hasPlayableItems = hasPlayableTracks,
                        onPlayAll = onPlayAll,
                        selectedCount = state.selectedTrackIds.size,
                        isAllSelected = state.directTracks.isNotEmpty() && state.selectedTrackIds.size >= state.directTracks.size,
                        onClearSelection = onClearSelection,
                        onToggleSelectAll = onToggleSelectAll,
                        trailingContent = {
                            Box {
                                BareIconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_common_sort),
                                        contentDescription = stringResource(R.string.tracks_sort_label),
                                        tint = MusicTheme.colors.onSurface,
                                        modifier = Modifier.size(dimensions.spaceLarge),
                                    )
                                }
                                AppDropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                ) {
                                    listOf(
                                        CategoryTrackSortField.TITLE,
                                        CategoryTrackSortField.ARTIST,
                                        CategoryTrackSortField.DATE_ADDED,
                                        CategoryTrackSortField.DURATION,
                                    ).forEach { field ->
                                        AppDropdownMenuItem(
                                            text = {
                                                val suffix =
                                                    if (field == state.trackSort.field) {
                                                        stringResource(state.trackSort.direction.labelRes())
                                                    } else {
                                                        ""
                                                    }
                                                Text(stringResource(field.labelRes()) + suffix)
                                            },
                                            onClick = {
                                                onTrackSortSelected(field)
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxWidth().height(dimensions.minimumTouchTarget))
                }
            }

            if (state.isLoaded && !hasItems) {
                EmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = dynamicBottomPadding),
                    title = stringResource(R.string.folder_empty_title),
                    description = stringResource(R.string.folder_empty_description),
                )
            } else {
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .bounceOverscroll(overscrollEffect),
                    contentPadding = PaddingValues(
                        top = dimensions.spaceSmall,
                        bottom = dimensions.spaceSmall + dynamicBottomPadding,
                    ),
                ) {
                    if (state.childFolders.isNotEmpty()) {
                        item(key = "folder_subfolders_header") {
                            Text(
                                text = stringResource(R.string.folder_subfolders_section),
                                style = MusicTheme.typography.titleMedium,
                                color = MusicTheme.colors.onSurface,
                                modifier = Modifier.padding(
                                    horizontal = dimensions.contentHorizontalPadding + dimensions.spaceSmall,
                                    vertical = dimensions.spaceSmall,
                                ),
                            )
                        }
                        items(state.childFolders, key = { it.id.sourceId }) { folder ->
                            Box(
                                modifier = Modifier.padding(
                                    horizontal = dimensions.contentHorizontalPadding,
                                    vertical = dimensions.spaceExtraSmall,
                                ),
                            ) {
                                BrowserFolderRow(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.id) },
                                )
                            }
                        }
                    }

                    if (state.directTracks.isNotEmpty()) {
                        item(key = "folder_tracks_header") {
                            Text(
                                text = stringResource(R.string.folder_tracks_section),
                                style = MusicTheme.typography.titleMedium,
                                color = MusicTheme.colors.onSurface,
                                modifier = Modifier.padding(
                                    horizontal = dimensions.contentHorizontalPadding + dimensions.spaceSmall,
                                    vertical = dimensions.spaceSmall,
                                ),
                            )
                        }
                        items(
                            state.directTracks,
                            key = { "${it.id.volumeName}:${it.id.mediaStoreId}" },
                        ) { track ->
                            TrackRow(
                                track = track,
                                modifier = Modifier.animateItem(),
                                selected = track.id in state.selectedTrackIds,
                                selectionMode = state.isSelectionMode,
                                playlists = state.playlists,
                                onAddToQueue = { onTrackAddToQueue(track.id) },
                                onPlayNext = { onTrackPlayNext(track.id) },
                                onAddToPlaylist = {
                                    singleTrackAddToPlaylistTarget = track.id
                                    showAddToPlaylistDialog = true
                                },
                                onShowTrackInfo = { onTrackShowInfo(track) },
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onClick = { onTrackClick(track) },
                                onLongClick = { onTrackLongClick(track) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasMiniPlayer) bottomPadding else 0.dp)
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Horizontal)),
        ) {
            val isSelectionEnabled = state.selectedTrackIds.isNotEmpty()
            SelectionBottomBar(
                actions = listOf(
                    SelectionBarAction(
                        label = stringResource(R.string.selection_add_to_playlist),
                        iconRes = R.drawable.ic_common_add,
                        enabled = isSelectionEnabled,
                        onClick = {
                            singleTrackAddToPlaylistTarget = null
                            showAddToPlaylistDialog = true
                        },
                    ),
                    SelectionBarAction(
                        label = stringResource(R.string.selection_add_to_queue),
                        iconRes = R.drawable.ic_common_queue_add,
                        enabled = isSelectionEnabled,
                        onClick = onAddSelectedToQueue,
                    ),
                ),
                contentInsets = contentInsets,
                applyBottomInset = !hasMiniPlayer,
            )
        }
    }

    if (showAddToPlaylistDialog) {
        val targetTrackId = singleTrackAddToPlaylistTarget
        AddToPlaylistDialog(
            playlists = state.playlists,
            onSelectPlaylist = { playlistId ->
                if (targetTrackId != null) {
                    onTrackAddToPlaylist(targetTrackId, playlistId)
                } else {
                    onAddSelectedToPlaylist(playlistId)
                }
                showAddToPlaylistDialog = false
                singleTrackAddToPlaylistTarget = null
            },
            onCreatePlaylist = { showCreatePlaylistDialog = true },
            onDismiss = {
                showAddToPlaylistDialog = false
                singleTrackAddToPlaylistTarget = null
            },
        )
    }

    if (showCreatePlaylistDialog) {
        TextInputDialog(
            title = stringResource(R.string.playlist_create_title),
            confirmLabel = stringResource(R.string.playlist_create),
            placeholder = stringResource(R.string.playlist_name_label),
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreatePlaylistDialog = false
            },
        )
    }

    state.infoTrack?.let { track ->
        TrackInfoViewer(
            track = track,
            metadata = state.infoMetadata,
            loading = state.isInfoLoading,
            onDismiss = onDismissTrackInfo,
        )
    }
}

@Composable
internal fun BrowserFolderRow(
    folder: FolderNode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MusicTheme.aeroCardContainerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .padding(horizontal = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_folder),
                contentDescription = null,
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
            Text(
                text = folder.displayName,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun folderDetailTitle(state: FolderDetailUiState): String =
    if (state.isVolumeRoot && state.volumeIsPrimary) {
        stringResource(R.string.folder_internal_storage)
    } else {
        state.displayName?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.folder_unknown_name)
    }
