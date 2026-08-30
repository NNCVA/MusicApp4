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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.LoadingState
import com.musicapp.player.core.designsystem.component.MessageDialog
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
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
) {
    val loadStartedNs = remember { SystemClock.elapsedRealtimeNanos() }
    val firstTrackLayoutLogged = remember { AtomicBoolean(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    BackHandler(enabled = state.isSelectionMode) { viewModel.onBack() }
    TracksScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        onSortSelected = viewModel::selectSort,
        onTrackArtworkRequested = viewModel::requestArtwork,
        onTrackAddToQueue = viewModel::addTrackToQueue,
        onTrackPlayNext = viewModel::playTrackNext,
        onTrackHide = viewModel::hideTrack,
        onTrackAddToPlaylist = viewModel::addTrackToPlaylist,
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
            }
            viewModel.toggleSelection(track.id)
        },
        onSelectAll = viewModel::selectAllCurrentResults,
        onSelectTracks = viewModel::selectTracks,
        onClearSelection = viewModel::clearSelection,
        onAddToPlaylist = viewModel::addSelectedToPlaylist,
        onAddToQueue = viewModel::addSelectedToQueue,
        onPlayNext = viewModel::playSelectedNext,
        onHideSelected = viewModel::hideSelected,
        onAcknowledgeBatchResult = viewModel::acknowledgeBatchResult,
        onFirstTrackLaidOut = {
            if (firstTrackLayoutLogged.compareAndSet(false, true)) {
                val completedNs = SystemClock.elapsedRealtimeNanos()
                Log.i(
                    TRACKS_LOAD_LOG_TAG,
                    "event=first_track_laid_out duration_us=${(completedNs - loadStartedNs) / 1_000} " +
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
    onSortSelected: (TrackSortField) -> Unit,
    onTrackArtworkRequested: suspend (Track) -> Unit,
    onTrackAddToQueue: (TrackId) -> Unit,
    onTrackPlayNext: (TrackId) -> Unit,
    onTrackHide: (TrackId) -> Unit,
    onTrackAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onSelectAll: () -> Unit,
    onSelectTracks: (Collection<TrackId>) -> Unit,
    onClearSelection: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHideSelected: () -> Unit,
    onAcknowledgeBatchResult: () -> Unit,
    onFirstTrackLaidOut: () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredTracks =
        remember(state.tracks, searchQuery) {
            state.tracks.filter { it.matchesSearch(searchQuery) }
        }
    val listState = rememberLazyListState()
    val sections = remember(filteredTracks, state.sort.field, state.sort.direction) {
        groupTracksIntoSections(filteredTracks, state.sort.field, state.sort.direction)
    }
    val sectionPositions = remember(sections, state.sort.direction) {
        sectionStartPositions(sections, state.sort.direction)
    }
    val selectedSection by remember(listState, sections) {
        derivedStateOf {
            sectionLabelAtPosition(sections, listState.firstVisibleItemIndex)
        }
    }
    val canScroll by remember(listState) {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    val isTextSort = state.sort.field in listOf(
        TrackSortField.TITLE,
        TrackSortField.ARTIST,
        TrackSortField.ALBUM,
    )
    val gutterMode = remember(state.isLibraryLoaded, state.tracks, filteredTracks, canScroll, isTextSort, state.sort.direction, selectedSection, sections, sectionPositions) {
        when {
            !state.isLibraryLoaded || state.tracks.isEmpty() || filteredTracks.isEmpty() || !canScroll ->
                GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = trackSortDirectionToSectionOrder(state.sort.direction),
                    activeSection = selectedSection,
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
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(contentInsets),
        ) {
            TracksTopBar(
                state = state,
                policy = policy,
                openDrawer = openDrawer,
                onSortSelected = onSortSelected,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onOpenSearch = { searchActive = true },
                onCloseSearch = {
                    searchActive = false
                    searchQuery = ""
                },
                onSearchQueryChange = { searchQuery = it },
                onSelectAll = {
                    if (searchQuery.isBlank()) {
                        onSelectAll()
                    } else {
                        onSelectTracks(filteredTracks.map(Track::id))
                    }
                },
                onClearSelection = onClearSelection,
                onAddToPlaylist = onAddToPlaylist,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onHideSelected = onHideSelected,
            )
            if (!state.isLibraryLoaded) {
                LoadingState(modifier = Modifier.weight(1f))
            } else if (state.tracks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                    title = stringResource(R.string.tracks_empty_title),
                    description = stringResource(R.string.tracks_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else if (filteredTracks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
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
                    artworkByTrackId = state.artworkByTrackId,
                    playlists = state.playlists,
                    onArtworkRequested = onTrackArtworkRequested,
                    onAddToQueue = onTrackAddToQueue,
                    onPlayNext = onTrackPlayNext,
                    onHide = onTrackHide,
                    onAddToPlaylist = onTrackAddToPlaylist,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = onTrackLongClick,
                    onFirstTrackLaidOut = onFirstTrackLaidOut,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        )
    }
    state.batchResult?.let { result ->
        BatchResultDialog(result, onAcknowledgeBatchResult)
    }
}

@Composable
private fun TracksTopBar(
    state: TracksUiState,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onSortSelected: (TrackSortField) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHideSelected: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var batchMenuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.minimumTouchTarget)
                .padding(horizontal = dimensions.topBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
        if (state.isSelectionMode) {
            TextButton(onClick = onClearSelection, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.selection_close))
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.selection_count,
                        state.selectedTrackIds.size,
                        state.selectedTrackIds.size,
                    ),
                style = MusicTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.selection_select_all))
            }
            Box {
                TextButton(
                    onClick = { batchMenuExpanded = true },
                    enabled = !state.isBatchActionRunning,
                    shape = MusicTheme.shapes.small,
                ) {
                    Text(stringResource(R.string.selection_more_actions))
                }
                TrackActionsMenu(
                    expanded = batchMenuExpanded && !state.isBatchActionRunning,
                    playlists = state.playlists,
                    onDismissRequest = { batchMenuExpanded = false },
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onHide = onHideSelected,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(dimensions.playerHeaderHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
                ) {
                    if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
                        CategoryNavigationIconButton(CategoryNavigationAction.DRAWER, openDrawer)
                    }
                    if (searchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MusicTheme.typography.titleLarge.copy(color = MusicTheme.colors.onSurface),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.tracks_search_placeholder),
                                            style = MusicTheme.typography.titleMedium,
                                            color = MusicTheme.colors.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        BareIconButton(
                            onClick = onCloseSearch,
                            modifier = Modifier.size(dimensions.minimumTouchTarget),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_common_close),
                                contentDescription = stringResource(R.string.tracks_search_close),
                                tint = MusicTheme.colors.onSurface,
                                modifier = Modifier.size(dimensions.spaceLarge),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.tracks_page_title),
                            style = MusicTheme.typography.headlineMedium,
                            color = MusicTheme.colors.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        BareIconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(dimensions.minimumTouchTarget),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_common_search),
                                contentDescription = stringResource(R.string.tracks_search_label),
                                tint = MusicTheme.colors.onSurface,
                                modifier = Modifier.size(dimensions.spaceLarge),
                            )
                        }
                    }
                }
                if (state.tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(dimensions.minimumTouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box {
                            BareIconButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.size(dimensions.minimumTouchTarget),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_common_sort_alpha),
                                    contentDescription = stringResource(R.string.tracks_sort_label),
                                    tint = MusicTheme.colors.onSurface,
                                    modifier = Modifier.size(dimensions.spaceLarge),
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                TrackSortField.entries.forEach { field ->
                                    DropdownMenuItem(
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
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchResultDialog(
    result: BatchTrackActionResult,
    onDismiss: () -> Unit,
) {
    if (result is BatchTrackActionResult.EmptySelection) return
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
private fun TrackList(
    tracks: List<Track>,
    sections: List<TrackSection>,
    showSectionIndex: Boolean,
    listState: LazyListState,
    selectedIds: Set<TrackId>,
    selectionMode: Boolean,
    artworkByTrackId: Map<TrackId, TrackArtworkState>,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    onArtworkRequested: suspend (Track) -> Unit,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onFirstTrackLaidOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
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
            modifier = Modifier.fillMaxSize()
                .then(scrollbarModifier)
                .padding(horizontal = dimensions.contentHorizontalPadding),
            contentPadding =
                PaddingValues(
                    //start = dimensions.spaceSmall,
                    top = dimensions.spaceSmall,
                    //end = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall,
                ),
        ) {
            if (sections.isEmpty()) {
                trackItems(
                    tracks = tracks,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    artworkByTrackId = artworkByTrackId,
                    playlists = playlists,
                    onArtworkRequested = onArtworkRequested,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onHide = onHide,
                    onAddToPlaylist = onAddToPlaylist,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = onTrackLongClick,
                    firstTrackId = tracks.first().id,
                    onFirstTrackLaidOut = onFirstTrackLaidOut,
                )
            } else {
                sections.forEach { section ->
                    trackItems(
                        tracks = section.tracks,
                        selectedIds = selectedIds,
                        selectionMode = selectionMode,
                        artworkByTrackId = artworkByTrackId,
                        playlists = playlists,
                        onArtworkRequested = onArtworkRequested,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onHide = onHide,
                        onAddToPlaylist = onAddToPlaylist,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick,
                        firstTrackId = tracks.first().id,
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
    artworkByTrackId: Map<TrackId, TrackArtworkState>,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    onArtworkRequested: suspend (Track) -> Unit,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    firstTrackId: TrackId,
    onFirstTrackLaidOut: () -> Unit,
) {
    items(tracks, key = { track -> "${track.id.volumeName}:${track.id.mediaStoreId}" }) { track ->
        TrackRow(
            track = track,
            selected = track.id in selectedIds,
            selectionMode = selectionMode,
            artwork = artworkByTrackId[track.id]
                ?.takeIf { it.dateModifiedMs == track.dateModifiedMs }
                ?.artwork
                ?: ArtworkResult.Placeholder,
            playlists = playlists,
            onArtworkRequested = { onArtworkRequested(track) },
            onAddToQueue = { onAddToQueue(track.id) },
            onPlayNext = { onPlayNext(track.id) },
            onHide = { onHide(track.id) },
            onAddToPlaylist = { playlistId -> onAddToPlaylist(track.id, playlistId) },
            onClick = { onTrackClick(track) },
            onLongClick = { onTrackLongClick(track) },
            onLaidOut = if (track.id == firstTrackId) onFirstTrackLaidOut else null,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    selected: Boolean,
    selectionMode: Boolean,
    artwork: ArtworkResult,
    playlists: List<com.musicapp.player.core.domain.model.Playlist>,
    onArtworkRequested: suspend () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onHide: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLaidOut: (() -> Unit)?,
) {
    val dimensions = MusicTheme.dimensions
    val compact = dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    val artistName = track.artistName.localizedArtistName()
    val subtitle =
        track.albumTitle
            ?.takeIf(String::isNotBlank)
            ?.let { stringResource(R.string.track_artist_album, artistName, it) }
            ?: artistName
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id, track.dateModifiedMs) {
        onArtworkRequested()
    }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(dimensions.trackListItemHeight)
                .then(
                    if (onLaidOut != null) {
                        Modifier.onGloballyPositioned { onLaidOut() }
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                //.padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        TrackArtwork(
            artwork = artwork,
            trackTitle = track.title,
            modifier = Modifier.size(dimensions.trackArtworkSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = if (compact) MusicTheme.typography.compactTrackTitle else MusicTheme.typography.expandedTrackTitle,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                track.qualityLabelResId()?.let { qualityResId ->
                    Surface(
                        color = MusicTheme.colors.secondaryContainer,
                        shape = MusicTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = stringResource(qualityResId),
                            style = MusicTheme.typography.labelSmall,
                            color = MusicTheme.colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = dimensions.spaceExtraSmall),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = if (compact) MusicTheme.typography.compactTrackArtist else MusicTheme.typography.expandedTrackArtist,
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (track.availability == Availability.TEMPORARILY_UNAVAILABLE) {
            Text(
                text = stringResource(R.string.track_temporarily_unavailable),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        if (!selectionMode) {
            BareIconButton(
                onClick = onAddToQueue,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_common_add),
                    contentDescription = stringResource(R.string.track_add_to_queue),
                    tint = MusicTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
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
                TrackActionsMenu(
                    expanded = menuExpanded,
                    playlists = playlists,
                    onDismissRequest = { menuExpanded = false },
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onHide = onHide,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Composable
private fun TrackArtwork(
    artwork: ArtworkResult,
    trackTitle: String,
    modifier: Modifier,
) {
    val shape = MusicTheme.shapes.small
    val artworkDescription = stringResource(R.string.track_artwork_description, trackTitle)
    when (artwork) {
        ArtworkResult.Placeholder ->
            TrackArtworkPlaceholder(
                modifier = modifier,
                artworkDescription = artworkDescription,
            )
        is ArtworkResult.Embedded -> {
            val image = artwork.image
            var bitmap by remember(image) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(image) {
                bitmap =
                    withContext(Dispatchers.Default) {
                        Bitmap.createBitmap(
                            image.argbPixels,
                            image.width,
                            image.height,
                            Bitmap.Config.ARGB_8888,
                        ).apply { prepareToDraw() }
                            .asImageBitmap()
                    }
            }
            val preparedBitmap = bitmap
            if (preparedBitmap == null) {
                TrackArtworkPlaceholder(
                    modifier = modifier,
                    artworkDescription = artworkDescription,
                )
            } else {
                Image(
                    bitmap = preparedBitmap,
                    contentDescription = artworkDescription,
                    modifier = modifier.clip(shape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

private const val TRACKS_LOAD_LOG_TAG = "TracksLoadPerf"

@Composable
private fun TrackArtworkPlaceholder(
    modifier: Modifier,
    artworkDescription: String,
) {
    Box(
        modifier =
            modifier
                .clip(MusicTheme.shapes.small)
                .background(MusicTheme.colors.secondaryContainer)
                .semantics {
                    contentDescription = artworkDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_playlist_album),
            contentDescription = null,
            tint = MusicTheme.colors.onSecondaryContainer,
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}

@Composable
private fun String.localizedArtistName(): String =
    if (this == UNKNOWN_ARTIST_SENTINEL) stringResource(R.string.unknown_artist) else this

private fun Track.qualityLabelResId(): Int? =
    when (mimeType?.lowercase(Locale.ROOT)) {
        "audio/flac",
        "audio/wav",
        "audio/x-wav",
        -> R.string.track_quality_high
        "audio/mpeg",
        "audio/aac",
        "audio/mp4",
        "audio/ogg",
        "audio/opus",
        -> R.string.track_quality_standard
        else -> null
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

private const val UNKNOWN_ARTIST_SENTINEL = "<unknown>"
