package com.musicapp.player.feature.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.MenuIconPalette
import com.musicapp.player.core.designsystem.component.MessageDialog
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SearchableTopBar
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlaylistDetailScreenRoute(
    playlistId: PlaylistId,
    viewModel: PlaylistDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    BackHandler(enabled = state.isSelectionMode || state.infoTrack != null || state.isSearching) {
        viewModel.onBack()
    }

    LaunchedEffect(playlistId) {
        viewModel.open(playlistId)
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

    PlaylistDetailScreen(
        state = state,
        contentInsets = contentInsets,
        bottomPadding = bottomPadding,
        onBack = onBack,
        onSortSelected = viewModel::selectSort,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onPlayAll = viewModel::playAll,
        onShufflePlay = viewModel::shufflePlay,
        onTrackClick = { track ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(track.id)
            } else if (track.availability == Availability.AVAILABLE) {
                viewModel.playTrack(track.id)
            }
        },
        onTrackLongClick = { track ->
            if (!state.isSelectionMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.startSelection(track.id)
            }
        },
        onStartSelection = {
            state.displayTracks.firstOrNull()?.let { firstTrack ->
                viewModel.startSelection(firstTrack.id)
            } ?: viewModel.selectAll()
        },
        onSelectAll = viewModel::selectAll,
        onSelectTracks = viewModel::selectTracks,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onClearSelection = viewModel::clearSelection,
        onRemoveTrack = viewModel::removeTrack,
        onRemoveSelected = viewModel::removeSelected,
        onTrackPlayNext = viewModel::playTrackNext,
        onTrackAddToQueue = viewModel::addTrackToQueue,
        onTrackAddToPlaylist = viewModel::addTrackToPlaylist,
        onAddSelectedToPlaylist = viewModel::addSelectedToPlaylist,
        onAddSelectedToQueue = viewModel::addSelectedToQueue,
        onTrackShowInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onRenamePlaylist = viewModel::renamePlaylist,
        onDeletePlaylist = {
            viewModel.deletePlaylist(onDeleted = onBack)
        },
        onCreatePlaylist = viewModel::createPlaylist,
        onAcknowledgePlaybackFeedback = viewModel::acknowledgePlaybackFeedback,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    contentInsets: WindowInsets,
    bottomPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onSortSelected: (PlaylistTrackSortField) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onStartSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectTracks: (Collection<TrackId>) -> Unit,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveTrack: (TrackId) -> Unit,
    onRemoveSelected: () -> Unit,
    onTrackPlayNext: (TrackId) -> Unit,
    onTrackAddToQueue: (TrackId) -> Unit,
    onTrackAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onAddSelectedToPlaylist: (PlaylistId) -> Unit,
    onAddSelectedToQueue: () -> Unit,
    onTrackShowInfo: (Track) -> Unit,
    onDismissTrackInfo: () -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onAcknowledgePlaybackFeedback: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)

    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by remember { mutableStateOf<TrackId?>(null) }

    val isTextSort = state.sort.field in listOf(
        PlaylistTrackSortField.TITLE,
        PlaylistTrackSortField.ARTIST,
        PlaylistTrackSortField.ALBUM,
    )

    val gutterMode = remember(state.isLibraryLoaded, state.tracks, state.displayTracks, isTextSort, state.sort.direction, state.sections, state.sectionPositions) {
        when {
            !state.isLibraryLoaded || state.tracks.isEmpty() || state.displayTracks.isEmpty() ->
                GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = if (state.sort.direction == PlaylistTrackSortDirection.ASCENDING) {
                        SectionSortOrder.ASCENDING
                    } else {
                        SectionSortOrder.DESCENDING
                    },
                    activeSectionProvider = { playlistSectionLabelAtPosition(state.sections, listState.firstVisibleItemIndex) },
                    populatedBuckets = state.sections.map(PlaylistSection::label).toSet(),
                    onSectionSelected = { label ->
                        state.sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                // +2 accounts for Hero item and stickyHeader item
                                listState.scrollToItem(position + 2)
                            }
                        }
                    },
                )
            else -> GutterMode.Scrollbar
        }
    }

    val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
    val hasMiniPlayer = bottomPadding > bottomInset + 1.dp
    val selectionBarHeight = dimensions.minimumTouchTarget
    val dynamicBottomPadding = bottomPadding + if (state.isSelectionMode) selectionBarHeight else 0.dp

    val showCollapsedTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val scrollbarModifier =
        if (gutterMode !is GutterMode.Index) {
            listState.scrollIndicatorState?.let { indicator ->
                Modifier.nonInteractiveScrollbar(indicator, Orientation.Vertical)
            } ?: Modifier
        } else {
            Modifier
        }

    var pageMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            SearchableTopBar(
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = {
                    if (state.isSearching) {
                        onCloseSearch()
                    } else {
                        onBack()
                    }
                },
                searchActive = state.isSearching,
                searchQuery = state.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onOpenSearch = onOpenSearch,
                onCloseSearch = onCloseSearch,
                searchPlaceholder = stringResource(R.string.playlist_search_placeholder),
                titleContent = {
                    Crossfade(
                        targetState = showCollapsedTitle,
                        label = "PlaylistTopBarTitleCrossfade",
                        modifier = Modifier.fillMaxHeight(),
                    ) { collapsed ->
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = if (collapsed) {
                                    state.playlist?.displayName ?: stringResource(R.string.playlist_unknown_name)
                                } else {
                                    stringResource(R.string.playlist_detail_title)
                                },
                                style = MusicTheme.typography.titleLarge,
                                color = if (collapsed) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
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
                            onDismissRequest = {
                                pageMenuExpanded = false
                            },
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
                                    pageMenuExpanded = false
                                    showRenameDialog = true
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
                                    pageMenuExpanded = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
            )

            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(scrollbarModifier)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
                contentPadding = PaddingValues(
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + dynamicBottomPadding,
                ),
            ) {
                item(key = "playlist_hero_header") {
                    PlaylistHeroHeader(
                        playlist = state.playlist,
                        tracks = state.tracks,
                    )
                }

                stickyHeader(key = "playlist_responsive_action_bar") {
                    val isPinned by remember {
                        derivedStateOf { listState.firstVisibleItemIndex >= 1 }
                    }
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    ListActionBar(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = if (isPinned && overscrollEffect.currentOffsetPx < 0f) {
                                        (-overscrollEffect.currentOffsetPx).roundToInt()
                                    } else {
                                        0
                                    },
                                )
                            }
                            .drawWithContent {
                                drawRect(
                                    color = Color.Transparent,
                                    blendMode = BlendMode.Clear,
                                )
                                drawContent()
                            },
                        isSelectionMode = state.isSelectionMode,
                        itemCount = state.displayTracks.size,
                        showPlayAll = true,
                        hasPlayableItems = state.displayTracks.any { it.availability == Availability.AVAILABLE },
                        onPlayAll = onPlayAll,
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
                                    PlaylistTrackSortField.entries.forEach { field ->
                                        AppDropdownMenuItem(
                                            text = {
                                                val suffix = if (field == state.sort.field && field != PlaylistTrackSortField.DEFAULT) {
                                                    stringResource(state.sort.direction.labelResId())
                                                } else {
                                                    ""
                                                }
                                                Text(stringResource(field.labelResId()) + suffix)
                                            },
                                            onClick = {
                                                onSortSelected(field)
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        selectedCount = state.selectedTrackIds.size,
                        isAllSelected = state.displayTracks.isNotEmpty() && state.selectedTrackIds.size >= state.displayTracks.size,
                        onClearSelection = onClearSelection,
                        onToggleSelectAll = onToggleSelectAll,
                    )
                }

                if (state.isLibraryLoaded && state.tracks.isEmpty()) {
                    item(key = "playlist_empty_state") {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensions.contentHorizontalPadding)
                                .padding(vertical = dimensions.spaceExtraLarge),
                            title = stringResource(R.string.playlist_empty_title),
                            description = stringResource(R.string.playlist_empty_description),
                        )
                    }
                } else if (state.isLibraryLoaded && state.displayTracks.isEmpty() && state.searchQuery.isNotBlank()) {
                    item(key = "playlist_search_no_results") {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensions.contentHorizontalPadding)
                                .padding(vertical = dimensions.spaceExtraLarge),
                            title = stringResource(R.string.tracks_no_results_title),
                            description = stringResource(R.string.tracks_no_results_description),
                        )
                    }
                } else if (gutterMode is GutterMode.Index && state.sections.isNotEmpty()) {
                    state.sections.forEach { section ->
                        items(section.tracks, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { track ->
                            PlaylistTrackRowItem(
                                track = track,
                                selected = track.id in state.selectedTrackIds,
                                selectionMode = state.isSelectionMode,
                                allPlaylists = state.allPlaylists,
                                onClick = { onTrackClick(track) },
                                onLongClick = { onTrackLongClick(track) },
                                onRemoveFromPlaylist = { onRemoveTrack(track.id) },
                                onPlayNext = { onTrackPlayNext(track.id) },
                                onAddToQueue = { onTrackAddToQueue(track.id) },
                                onAddToOtherPlaylist = {
                                    singleTrackAddToPlaylistTarget = track.id
                                    showAddToPlaylistDialog = true
                                },
                                onShowTrackInfo = { onTrackShowInfo(track) },
                            )
                        }
                    }
                } else {
                    items(state.displayTracks, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { track ->
                        PlaylistTrackRowItem(
                            track = track,
                            selected = track.id in state.selectedTrackIds,
                            selectionMode = state.isSelectionMode,
                            allPlaylists = state.allPlaylists,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) },
                            onRemoveFromPlaylist = { onRemoveTrack(track.id) },
                            onPlayNext = { onTrackPlayNext(track.id) },
                            onAddToQueue = { onTrackAddToQueue(track.id) },
                            onAddToOtherPlaylist = {
                                singleTrackAddToPlaylistTarget = track.id
                                showAddToPlaylistDialog = true
                            },
                            onShowTrackInfo = { onTrackShowInfo(track) },
                        )
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
            PlaylistSelectionBottomBar(
                selectedCount = state.selectedTrackIds.size,
                contentInsets = contentInsets,
                applyBottomInset = !hasMiniPlayer,
                onRemoveFromPlaylistClick = onRemoveSelected,
                onAddToPlaylistClick = {
                    singleTrackAddToPlaylistTarget = null
                    showAddToPlaylistDialog = true
                },
                onAddToQueueClick = onAddSelectedToQueue,
            )
        }

        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(bottom = dynamicBottomPadding),
        )
    }

    if (showRenameDialog && state.playlist != null) {
        TextInputDialog(
            title = stringResource(R.string.playlist_rename_title),
            initialText = state.playlist.displayName,
            placeholder = stringResource(R.string.playlist_name_label),
            confirmLabel = stringResource(R.string.playlist_save),
            cancelLabel = stringResource(R.string.playlist_cancel),
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                onRenamePlaylist(name)
                showRenameDialog = false
            },
        )
    }

    if (showDeleteDialog && state.playlist != null) {
        ConfirmationDialog(
            title = stringResource(R.string.playlist_delete_title),
            text = stringResource(R.string.playlist_delete_description),
            confirmLabel = stringResource(R.string.playlist_delete),
            cancelLabel = stringResource(R.string.playlist_cancel),
            onConfirm = {
                showDeleteDialog = false
                onDeletePlaylist()
            },
            onDismiss = { showDeleteDialog = false },
            isDestructive = true,
        )
    }

    if (showAddToPlaylistDialog) {
        val targetTrackId = singleTrackAddToPlaylistTarget
        val otherPlaylists = remember(state.allPlaylists, state.playlist?.id) {
            state.allPlaylists.filter { it.id != state.playlist?.id }
        }
        AddToPlaylistDialog(
            playlists = otherPlaylists,
            onSelectPlaylist = { targetPlaylistId ->
                if (targetTrackId != null) {
                    onTrackAddToPlaylist(targetTrackId, targetPlaylistId)
                } else {
                    onAddSelectedToPlaylist(targetPlaylistId)
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

    state.playbackFeedback?.let { feedback ->
        MessageDialog(
            title = stringResource(R.string.playlist_playback_result_title),
            message =
                pluralStringResource(
                    R.plurals.playlist_playback_result,
                    feedback.playedCount,
                    feedback.playedCount,
                    feedback.skippedCount,
                ),
            confirmLabel = stringResource(R.string.selection_close),
            onDismiss = onAcknowledgePlaybackFeedback,
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
private fun PlaylistHeroHeader(
    playlist: Playlist?,
    tracks: List<Track>,
) {
    val dimensions = MusicTheme.dimensions
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val totalHours = totalDurationMs / 3_600_000L
    val totalMinutes = (totalDurationMs % 3_600_000L) / 60_000L

    val durationText = if (totalHours > 0) {
        stringResource(R.string.playlist_total_duration_hours_minutes, totalHours, totalMinutes)
    } else {
        stringResource(R.string.playlist_total_duration_minutes, totalMinutes)
    }

    val createdAtDate = remember(playlist?.createdAtMs) {
        val ms = playlist?.createdAtMs ?: 0L
        if (ms > 0L) {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            formatter.format(Date(ms))
        } else {
            ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensions.contentHorizontalPadding,
                vertical = dimensions.spaceMedium,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceLarge),
    ) {
        QuadPlaylistArtwork(
            playlist = playlist,
            tracks = tracks,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = playlist?.displayName ?: stringResource(R.string.playlist_unknown_name),
                style = MusicTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MusicTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val trackCountText = pluralStringResource(
                R.plurals.category_track_count,
                tracks.size,
                tracks.size,
            )
            Text(
                text = "$trackCountText · $durationText",
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (createdAtDate.isNotBlank()) {
                Text(
                    text = stringResource(R.string.playlist_created_at, createdAtDate),
                    style = MusicTheme.typography.labelMedium,
                    color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}



@Composable
private fun PlaylistTrackRowItem(
    track: Track,
    selected: Boolean,
    selectionMode: Boolean,
    allPlaylists: List<Playlist>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToOtherPlaylist: () -> Unit,
    onShowTrackInfo: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    TrackRow(
        track = track,
        selected = selected,
        selectionMode = selectionMode,
        onClick = onClick,
        onLongClick = onLongClick,
        trailingContent = if (!selectionMode) {
            {
                var menuExpanded by remember(track.id) { mutableStateOf(false) }
                Box {
                    BareIconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(dimensions.minimumTouchTarget),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_common_more_vertical),
                            contentDescription = stringResource(R.string.track_more_actions),
                            tint = MusicTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                    }

                    PlaylistTrackActionsMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToOtherPlaylist = onAddToOtherPlaylist,
                        onShowTrackInfo = onShowTrackInfo,
                    )
                }
            }
        } else null,
    )
}

@Composable
private fun PlaylistTrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onAddToOtherPlaylist: () -> Unit,
    onShowTrackInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.playlist_remove_from_playlist)) },
            isDestructive = true,
            iconTint = MenuIconPalette.Delete,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_delete),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onRemoveFromPlaylist()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_play_next)) },
            iconTint = MenuIconPalette.Play,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_playback_skip_next),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onPlayNext()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_add_to_playlist)) },
            iconTint = MenuIconPalette.Add,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_add),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onAddToOtherPlaylist()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_track_info)) },
            iconTint = MenuIconPalette.Info,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sidebar_about),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onShowTrackInfo()
            },
        )
    }
}

@Composable
private fun PlaylistSelectionBottomBar(
    selectedCount: Int,
    contentInsets: WindowInsets,
    applyBottomInset: Boolean,
    onRemoveFromPlaylistClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val isEnabled = selectedCount > 0
    val contentColor =
        if (isEnabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurface.copy(alpha = 0.38f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MusicTheme.colors.surface,
        tonalElevation = dimensions.spaceExtraSmall,
    ) {
        Column(
            modifier = if (applyBottomInset) {
                Modifier.windowInsetsPadding(contentInsets.only(WindowInsetsSides.Bottom))
            } else {
                Modifier
            },
        ) {
            HorizontalDivider(
                color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.minimumTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Remove from playlist
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = isEnabled, onClick = onRemoveFromPlaylistClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_delete),
                        contentDescription = null,
                        tint = if (isEnabled) MusicTheme.colors.error else contentColor,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                    Spacer(modifier = Modifier.width(dimensions.spaceSmall))
                    Text(
                        text = stringResource(R.string.playlist_remove_from_playlist),
                        style = MusicTheme.typography.titleMedium,
                        color = if (isEnabled) MusicTheme.colors.error else contentColor,
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(dimensions.spaceLarge)
                        .width(1.dp),
                    color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
                )

                // 2. Add to other playlist
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = isEnabled, onClick = onAddToPlaylistClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_add),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                    Spacer(modifier = Modifier.width(dimensions.spaceSmall))
                    Text(
                        text = stringResource(R.string.selection_add_to_playlist),
                        style = MusicTheme.typography.titleMedium,
                        color = contentColor,
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(dimensions.spaceLarge)
                        .width(1.dp),
                    color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
                )

                // 3. Add to queue
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = isEnabled, onClick = onAddToQueueClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_queue_add),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                    Spacer(modifier = Modifier.width(dimensions.spaceSmall))
                    Text(
                        text = stringResource(R.string.selection_add_to_queue),
                        style = MusicTheme.typography.titleMedium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
