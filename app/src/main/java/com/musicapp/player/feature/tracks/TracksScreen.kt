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
import coil3.compose.AsyncImage
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
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
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
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
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
) {
    val loadStartedNs = remember { SystemClock.elapsedRealtimeNanos() }
    val firstTrackLayoutLogged = remember { AtomicBoolean(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    BackHandler(enabled = state.isSelectionMode || state.infoTrack != null) { viewModel.onBack() }
    TracksScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        bottomPadding = bottomPadding,
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
        onAddToQueue = viewModel::addSelectedToQueue,
        onPlayNext = viewModel::playSelectedNext,
        onHideSelected = viewModel::hideSelected,
        onAcknowledgeBatchResult = viewModel::acknowledgeBatchResult,
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
    bottomPadding: Dp = 0.dp,
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
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHideSelected: () -> Unit,
    onAcknowledgeBatchResult: () -> Unit,
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    onPlayAll: () -> Unit = {},
    onFirstTrackLaidOut: () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val isSearching = searchQuery.isNotBlank()
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
    val filteredTracks =
        remember(state.tracks, searchQuery, isSearching) {
            if (isSearching) {
                state.tracks.filter { it.matchesSearch(searchQuery) }
            } else {
                state.tracks
            }
        }
    val listState = rememberLazyListState()
    val sections = remember(filteredTracks, state.sections, isSearching, state.sort.field, state.sort.direction) {
        if (isSearching) {
            groupTracksIntoSections(filteredTracks, state.sort.field, state.sort.direction)
        } else {
            state.sections
        }
    }
    val sectionPositions = remember(sections, state.sort.direction) {
        sectionStartPositions(sections, state.sort.direction)
    }
    val isTextSort = state.sort.field in listOf(
        TrackSortField.TITLE,
        TrackSortField.ARTIST,
        TrackSortField.ALBUM,
    )
    val gutterMode = remember(state.isLibraryLoaded, state.tracks, filteredTracks, isTextSort, state.sort.direction, sections, sectionPositions) {
        when {
            !state.isLibraryLoaded || state.tracks.isEmpty() || filteredTracks.isEmpty() ->
                GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = trackSortDirectionToSectionOrder(state.sort.direction),
                    activeSectionProvider = { sectionLabelAtPosition(sections, listState.firstVisibleItemIndex) },
                    populatedBuckets = sections.map(TrackSection::label).toSet(),
                    onSectionSelected = { label ->
                        sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                listState.scrollToItem(position)
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

    val onToggleSelectAllResolved = {
        if (searchActive && searchQuery.isNotBlank()) {
            val visibleIds = filteredTracks.map(Track::id).toSet()
            if (visibleIds.isNotEmpty() && state.selectedTrackIds.containsAll(visibleIds)) {
                onClearSelection()
            } else {
                onSelectTracks(visibleIds)
            }
        } else {
            onToggleSelectAll()
        }
    }

    val onPlayAllResolved = {
        val targetTracks = if (searchActive && searchQuery.isNotBlank()) filteredTracks else state.tracks
        val firstAvailable = targetTracks.firstOrNull { it.availability == Availability.AVAILABLE }
        if (firstAvailable != null) {
            if (searchActive && searchQuery.isNotBlank()) {
                onTrackClick(firstAvailable)
            } else {
                onPlayAll()
            }
        }
    }

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
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onOpenSearch = { searchActive = true },
                    onCloseSearch = {
                        searchActive = false
                        searchQuery = ""
                    },
                )
                if (state.isLibraryLoaded && filteredTracks.isNotEmpty()) {
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    ListActionBar(
                        isSelectionMode = state.isSelectionMode,
                        itemCount = filteredTracks.size,
                        showPlayAll = true,
                        hasPlayableItems = filteredTracks.any { it.availability == Availability.AVAILABLE },
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
                        isAllSelected = filteredTracks.isNotEmpty() && state.selectedTrackIds.size >= filteredTracks.size,
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
            } else if (state.isLibraryLoaded && filteredTracks.isEmpty() && searchQuery.isNotBlank()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = dynamicBottomPadding),
                    title = stringResource(R.string.tracks_no_results_title),
                    description = stringResource(R.string.tracks_no_results_description),
                )
            } else {
                TrackList(
                    tracks = filteredTracks,
                    sections = sections,
                    showSectionIndex = gutterMode is GutterMode.Index,
                    listState = listState,
                    selectedIds = state.selectedTrackIds,
                    selectionMode = state.isSelectionMode,
                    playlists = state.playlists,
                    onAddToQueue = onTrackAddToQueue,
                    onPlayNext = onTrackPlayNext,
                    onHide = onTrackHide,
                    onAddToPlaylist = onTrackAddToPlaylist,
                    onShowTrackInfo = onTrackShowInfo,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = onTrackLongClick,
                    onFirstTrackLaidOut = onFirstTrackLaidOut,
                    bottomPadding = dynamicBottomPadding,
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
            SelectionBottomBar(
                selectedCount = state.selectedTrackIds.size,
                contentInsets = contentInsets,
                applyBottomInset = !hasMiniPlayer,
                onAddToPlaylistClick = { showAddToPlaylistDialog = true },
                onAddToQueueClick = onAddToQueue,
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
        AddToPlaylistSelectionDialog(
            playlists = state.playlists,
            onSelectPlaylist = { playlistId ->
                onAddToPlaylist(playlistId)
                showAddToPlaylistDialog = false
            },
            onDismiss = { showAddToPlaylistDialog = false },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    sections: List<TrackSection>,
    showSectionIndex: Boolean,
    listState: LazyListState,
    selectedIds: Set<TrackId>,
    selectionMode: Boolean,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onFirstTrackLaidOut: () -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val scrollbarModifier =
        if (!showSectionIndex) {
            listState.scrollIndicatorState?.let { scrollIndicatorState ->
                Modifier.nonInteractiveScrollbar(scrollIndicatorState, Orientation.Vertical)
            } ?: Modifier
        } else {
            Modifier
        }
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            overscrollEffect = overscrollEffect,
            modifier = Modifier.fillMaxSize()
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
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onHide = onHide,
                    onAddToPlaylist = onAddToPlaylist,
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
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onHide = onHide,
                        onAddToPlaylist = onAddToPlaylist,
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
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    firstTrackId: TrackId?,
    onFirstTrackLaidOut: () -> Unit,
) {
    items(tracks, key = { track -> "${track.id.volumeName}:${track.id.mediaStoreId}" }) { track ->
        TrackRow(
            track = track,
            selected = track.id in selectedIds,
            selectionMode = selectionMode,
            playlists = playlists,
            onAddToQueue = { onAddToQueue(track.id) },
            onPlayNext = { onPlayNext(track.id) },
            onHide = { onHide(track.id) },
            onAddToPlaylist = { playlistId -> onAddToPlaylist(track.id, playlistId) },
            onShowTrackInfo = { onShowTrackInfo(track) },
            onClick = { onTrackClick(track) },
            onLongClick = { onTrackLongClick(track) },
            onLaidOut = if (track.id == firstTrackId) onFirstTrackLaidOut else null,
        )
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    contentInsets: WindowInsets,
    applyBottomInset: Boolean,
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
            modifier =
                if (applyBottomInset) {
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dimensions.minimumTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
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
                    modifier =
                        Modifier
                            .height(dimensions.spaceLarge)
                            .width(1.dp),
                    color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
                )

                Row(
                    modifier =
                        Modifier
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

@Composable
private fun AddToPlaylistSelectionDialog(
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    onSelectPlaylist: (PlaylistId) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.selection_add_to_playlist_dialog_title),
                style = MusicTheme.typography.titleLarge,
            )
        },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.selection_no_playlists),
                    style = MusicTheme.typography.bodyMedium,
                    color = MusicTheme.colors.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                ) {
                    items(playlists, key = { it.id.value }) { playlist ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(dimensions.minimumTouchTarget)
                                    .clickable { onSelectPlaylist(playlist.id) }
                                    .padding(horizontal = dimensions.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sidebar_playlists),
                                contentDescription = null,
                                tint = MusicTheme.colors.onSurfaceVariant,
                                modifier = Modifier.size(dimensions.spaceLarge),
                            )
                            Spacer(modifier = Modifier.width(dimensions.spaceMedium))
                            Text(
                                text = playlist.displayName,
                                style = MusicTheme.typography.bodyLarge,
                                color = MusicTheme.colors.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.playlist_cancel))
            }
        },
        shape = MusicTheme.shapes.extraLarge,
    )
}

private fun Track.matchesSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true
    return listOf(title, artistName, albumTitle, displayName)
        .filterNotNull()
        .any { it.contains(normalizedQuery, ignoreCase = true) }
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
