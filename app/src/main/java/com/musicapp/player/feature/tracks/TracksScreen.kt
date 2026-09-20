package com.musicapp.player.feature.tracks

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.LockScrollOnChange
import com.musicapp.player.core.designsystem.component.ResetScrollOnChange
import com.musicapp.player.core.designsystem.component.SelectionBarAction
import com.musicapp.player.core.designsystem.component.SelectionBottomBar
import com.musicapp.player.core.designsystem.component.TextInputDialog
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.LoadingState
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.QualityBadge
import com.musicapp.player.core.designsystem.component.resolveQuality
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SearchableTopBar
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun TracksScreenRoute(
    viewModel: TracksViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (AlbumId) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
    isActive: Boolean = true,
    currentPlayingTrackId: TrackId? = null,
    onOpenPlayer: () -> Unit = {},
) {
    val loadStartedNs = remember { SystemClock.elapsedRealtimeNanos() }
    val firstTrackLayoutLogged = remember { AtomicBoolean(false) }
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

    BackHandler(enabled = state.isSelectionMode || state.infoTrack != null) { viewModel.onBack() }
    TracksScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        onNavigateToArtist = onArtistClick,
        onNavigateToAlbum = onAlbumClick,
        onSearchClick = onSearchClick,
        bottomPadding = bottomPadding,
        currentPlayingTrackId = currentPlayingTrackId,
        onSortSelected = viewModel::selectSort,
        onTrackAddToQueue = viewModel::addTrackToQueue,
        onTrackPlayNext = viewModel::playTrackNext,
        onTrackHide = viewModel::hideTrack,
        onTrackAddToPlaylist = viewModel::addTrackToPlaylist,
        onTrackShowInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onTrackClick = { track ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(track.id)
            } else if (track.id == currentPlayingTrackId) {
                onOpenPlayer()
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
        onSelectAll = viewModel::selectAllCurrentResults,
        onSelectTracks = viewModel::selectTracks,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onClearSelection = viewModel::exitSelection,
        onAddToPlaylist = viewModel::addSelectedToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onAddToQueue = viewModel::addSelectedToQueue,
        onPlayNext = viewModel::playSelectedNext,
        onHideSelected = viewModel::hideSelected,
        onAcknowledgeBatchResult = viewModel::acknowledgeBatchResult,
        onRefresh = viewModel::refreshTracks,
        onAcknowledgeRefreshResult = viewModel::acknowledgeRefreshResult,
        onShowMessage = onShowMessage,
        onPlayAll = viewModel::playAll,
        onFirstTrackLaidOut = {
            if (firstTrackLayoutLogged.compareAndSet(false, true)) {
                val completedNs = SystemClock.elapsedRealtimeNanos()
                Log.i(
                    "BenchmarkTrace",
                    "TracksFirstTrackLaidOut duration_ms=${(completedNs - loadStartedNs) / 1_000_000.0} " +
                        "track_count=${state.tracks.size} start_ns=$loadStartedNs end_ns=$completedNs",
                )
            }
        },
    )
}

@Composable
fun TracksScreen(
    state: TracksUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (AlbumId) -> Unit = {},
    bottomPadding: Dp = 0.dp,
    currentPlayingTrackId: TrackId? = null,
    onSortSelected: (TrackSortField) -> Unit,
    onTrackArtworkRequested: suspend (Track) -> Unit = {},
    onTrackAddToQueue: (TrackId) -> Unit,
    onTrackPlayNext: (TrackId) -> Unit,
    onTrackHide: (TrackId) -> Unit,
    onTrackAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onTrackShowInfo: (Track) -> Unit = {},
    onDismissTrackInfo: () -> Unit = {},
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onSelectAll: () -> Unit,
    onSelectTracks: (Collection<TrackId>) -> Unit,
    onToggleSelectAll: () -> Unit = onSelectAll,
    onClearSelection: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHideSelected: () -> Unit,
    onAcknowledgeBatchResult: () -> Unit,
    onRefresh: () -> Unit = {},
    onAcknowledgeRefreshResult: () -> Unit = {},
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onFirstTrackLaidOut: () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by remember { mutableStateOf<TrackId?>(null) }
    LaunchedEffect(state.refreshResult) {
        val result = state.refreshResult ?: return@LaunchedEffect
        when (result) {
            is TracksRefreshResult.Added -> {
                onShowMessage(
                    R.string.scan_refresh_added,
                    listOf(result.count),
                )
            }
            is TracksRefreshResult.Removed -> {
                onShowMessage(
                    R.string.scan_refresh_removed,
                    listOf(result.count),
                )
            }
            is TracksRefreshResult.AddedAndRemoved -> {
                onShowMessage(
                    R.string.scan_refresh_added_and_removed,
                    listOf(result.addedCount, result.removedCount),
                )
            }
            TracksRefreshResult.UpToDate -> {
                onShowMessage(R.string.scan_refresh_up_to_date, emptyList())
            }
            TracksRefreshResult.Failed -> {
                onShowMessage(R.string.scan_result_failed_title, emptyList())
            }
        }
        onAcknowledgeRefreshResult()
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
        onAcknowledgeBatchResult()
    }
    val listState = rememberLazyListState()
    listState.LockScrollOnChange(state.sort)
    val isTextSort = state.sort.field in listOf(
        TrackSortField.TITLE,
        TrackSortField.ARTIST,
        TrackSortField.ALBUM,
    )
    val gutterMode = remember(state.isLibraryLoaded, state.tracks, isTextSort, state.sort.direction, state.sections, state.sectionPositions) {
        when {
            !state.isLibraryLoaded || state.tracks.isEmpty() ->
                GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = trackSortDirectionToSectionOrder(state.sort.direction),
                    activeSection = sectionLabelAtPosition(state.sections, listState.firstVisibleItemIndex),
                    populatedBuckets = state.sections.map(TrackSection::label).toSet(),
                    onSectionSelected = { label ->
                        state.sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                listState.scrollToItem(position.coerceAtLeast(0))
                            }
                        }
                    },
                )
            else -> GutterMode.Scrollbar
        }
    }
    val onPlayAllResolved: () -> Unit = {
        onPlayAll()
    }
    val onToggleSelectAllResolved: () -> Unit = {
        if (state.selectedTrackIds.size >= state.tracks.size) {
            onClearSelection()
        } else {
            onSelectTracks(state.tracks.map(Track::id))
        }
    }
    val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
    val hasMiniPlayer = bottomPadding > bottomInset + 1.dp
    val selectionBarHeight = dimensions.minimumTouchTarget
    val dynamicBottomPadding = bottomPadding + if (state.isSelectionMode) selectionBarHeight else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SearchableTopBar(
                    title = stringResource(R.string.tracks_page_title),
                    navigationAction = if (policy == WindowLayoutPolicy.COMPACT_DRAWER) CategoryNavigationAction.DRAWER else null,
                    onNavigationClick = openDrawer,
                    searchActive = false,
                    searchQuery = "",
                    onOpenSearch = onSearchClick,
                    onCloseSearch = {},
                )
                if (state.isLibraryLoaded && state.tracks.isNotEmpty()) {
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    ListActionBar(
                        isSelectionMode = state.isSelectionMode,
                        itemCount = state.tracks.size,
                        showPlayAll = true,
                        hasPlayableItems = state.tracks.any { it.availability == Availability.AVAILABLE },
                        onPlayAll = onPlayAllResolved,
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
                                    TrackSortField.entries.forEach { field ->
                                        AppDropdownMenuItem(
                                            text = {
                                                val suffix =
                                                    if (field == state.sort.field) {
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
                        isAllSelected = state.tracks.isNotEmpty() && state.selectedTrackIds.size >= state.tracks.size,
                        onClearSelection = onClearSelection,
                        onToggleSelectAll = onToggleSelectAllResolved,
                    )
                }
            }
            if (state.isLibraryLoaded && state.tracks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = dynamicBottomPadding),
                    title = stringResource(R.string.tracks_empty_title),
                    description = stringResource(R.string.tracks_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else {
                TrackList(
                    tracks = state.tracks,
                    sections = state.sections,
                    showSectionIndex = gutterMode is GutterMode.Index,
                    listState = listState,
                    selectedIds = state.selectedTrackIds,
                    selectionMode = state.isSelectionMode,
                    playlists = state.playlists,
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    onAddToQueue = onTrackAddToQueue,
                    onPlayNext = onTrackPlayNext,
                    onHide = onTrackHide,
                    onAddToPlaylist = { trackId ->
                        singleTrackAddToPlaylistTarget = trackId
                        showAddToPlaylistDialog = true
                    },
                    onShowTrackInfo = onTrackShowInfo,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = onTrackLongClick,
                    onFirstTrackLaidOut = onFirstTrackLaidOut,
                    bottomPadding = dynamicBottomPadding,
                    currentPlayingTrackId = currentPlayingTrackId,
                    modifier = Modifier.weight(1f),
                )
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
                        onClick = onAddToQueue,
                    ),
                ),
                contentInsets = contentInsets,
                applyBottomInset = !hasMiniPlayer,
            )
        }

        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(bottom = dynamicBottomPadding),
        )
    }

    if (showAddToPlaylistDialog) {
        val targetTrackId = singleTrackAddToPlaylistTarget
        AddToPlaylistDialog(
            playlists = state.playlists,
            onSelectPlaylist = { playlistId ->
                if (targetTrackId != null) {
                    onTrackAddToPlaylist(targetTrackId, playlistId)
                } else {
                    onAddToPlaylist(playlistId)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    sections: List<TrackSection>,
    showSectionIndex: Boolean,
    listState: LazyListState,
    selectedIds: Set<TrackId>,
    selectionMode: Boolean,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId) -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (AlbumId) -> Unit = {},
    onShowTrackInfo: (Track) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onFirstTrackLaidOut: () -> Unit,
    bottomPadding: Dp = 0.dp,
    currentPlayingTrackId: TrackId? = null,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val overscrollEffect =
        rememberBounceOverscrollEffect(
            state = listState,
            allowStartEdge = false,
            allowEndEdge = true,
        )
    val scrollbarModifier =
        if (!showSectionIndex) {
            listState.scrollIndicatorState?.let { scrollIndicatorState ->
                Modifier.nonInteractiveScrollbar(scrollIndicatorState, Orientation.Vertical)
            } ?: Modifier
        } else {
            Modifier
        }
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
        state = pullRefreshState,
        enabled = !selectionMode && tracks.isNotEmpty(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MusicTheme.colors.surfaceContainerHigh,
                color = MusicTheme.colors.primary,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            overscrollEffect = overscrollEffect,
            modifier = Modifier.fillMaxSize()
                .bounceOverscroll(overscrollEffect)
                .then(scrollbarModifier),
            contentPadding =
                PaddingValues(
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
        ) {
            val firstTrackId = tracks.firstOrNull()?.id
            if (sections.isEmpty()) {
                trackItems(
                    tracks = tracks,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    playlists = playlists,
                    currentPlayingTrackId = currentPlayingTrackId,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onHide = onHide,
                    onAddToPlaylist = onAddToPlaylist,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onShowTrackInfo = onShowTrackInfo,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = onTrackLongClick,
                    firstTrackId = firstTrackId,
                    onFirstTrackLaidOut = onFirstTrackLaidOut,
                )
            } else {
                sections.forEach { section ->
                    trackItems(
                        tracks = section.tracks,
                        selectedIds = selectedIds,
                        selectionMode = selectionMode,
                        playlists = playlists,
                        currentPlayingTrackId = currentPlayingTrackId,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onHide = onHide,
                        onAddToPlaylist = onAddToPlaylist,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onShowTrackInfo = onShowTrackInfo,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick,
                        firstTrackId = firstTrackId,
                        onFirstTrackLaidOut = onFirstTrackLaidOut,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>,
    selectedIds: Set<TrackId>,
    selectionMode: Boolean,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    currentPlayingTrackId: TrackId?,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (AlbumId) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    firstTrackId: TrackId?,
    onFirstTrackLaidOut: () -> Unit,
) {
    items(tracks, key = { track -> "${track.id.volumeName}:${track.id.mediaStoreId}" }) { track ->
        TrackRow(
            track = track,
            modifier = Modifier.animateItem(),
            isCurrent = track.id == currentPlayingTrackId,
            selected = track.id in selectedIds,
            selectionMode = selectionMode,
            playlists = playlists,
            onAddToQueue = { onAddToQueue(track.id) },
            onPlayNext = { onPlayNext(track.id) },
            onHide = { onHide(track.id) },
            onAddToPlaylist = { onAddToPlaylist(track.id) },
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = onNavigateToAlbum,
            onShowTrackInfo = { onShowTrackInfo(track) },
            onClick = { onTrackClick(track) },
            onLongClick = { onTrackLongClick(track) },
            onLaidOut = if (track.id == firstTrackId) onFirstTrackLaidOut else null,
        )
    }
}

private fun TrackSortField.labelResId(): Int =
    when (this) {
        TrackSortField.TITLE -> R.string.sort_title
        TrackSortField.ARTIST -> R.string.sort_artist
        TrackSortField.ALBUM -> R.string.sort_album
        TrackSortField.DATE_ADDED -> R.string.sort_date_added
        TrackSortField.DURATION -> R.string.sort_duration
    }

private fun TrackSortDirection.labelResId(): Int =
    when (this) {
        TrackSortDirection.ASCENDING -> R.string.sort_direction_ascending
        TrackSortDirection.DESCENDING -> R.string.sort_direction_descending
    }
