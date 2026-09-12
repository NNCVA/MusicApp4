package com.musicapp.player.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.SelectionBarAction
import com.musicapp.player.core.designsystem.component.SelectionBottomBar
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.tracks.TrackSection
import com.musicapp.player.feature.tracks.TrackSortDirection
import com.musicapp.player.feature.tracks.TrackSortField
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.navigation.SearchScopeType
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.launch

@Composable
fun SearchScreenRoute(
    viewModel: SearchViewModel,
    scopeType: SearchScopeType,
    playlistId: Long? = null,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    isInBackStack: () -> Boolean = { true },
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (AlbumId) -> Unit = {},
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
    persistentBottomPadding: Dp? = null,
    currentPlayingTrackId: TrackId? = null,
    onOpenPlayer: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    val exitSearch: () -> Unit = {
        keyboardController?.hide()
        viewModel.resetSearch()
        onBack()
    }

    LaunchedEffect(scopeType, playlistId) {
        viewModel.initScope(scopeType, playlistId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.exitSelection()
            if (!isInBackStack()) {
                viewModel.resetSearch()
            }
        }
    }

    BackHandler {
        if (state.isSelectionMode || state.infoTrack != null) {
            viewModel.onBack()
        } else {
            exitSearch()
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

    SearchScreen(
        state = state,
        contentInsets = contentInsets,
        bottomPadding = bottomPadding,
        persistentBottomPadding = persistentBottomPadding,
        currentPlayingTrackId = currentPlayingTrackId,
        onBack = exitSearch,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onSortSelected = viewModel::onSortSelected,
        onPlayTrack = { track ->
            keyboardController?.hide()
            if (track.id == currentPlayingTrackId) {
                onOpenPlayer()
            } else {
                viewModel.playTrack(track)
            }
        },
        onPlayAll = {
            keyboardController?.hide()
            viewModel.playAll()
        },
        onEnterSelection = { trackId ->
            keyboardController?.hide()
            viewModel.enterSelection(trackId)
        },
        onToggleSelection = { trackId ->
            keyboardController?.hide()
            viewModel.toggleSelection(trackId)
        },
        onToggleSelectAll = {
            keyboardController?.hide()
            viewModel.toggleSelectAll()
        },
        onClearSelection = viewModel::clearSelection,
        onAddToQueue = viewModel::onAddToQueue,
        onPlayNext = viewModel::onPlayNext,
        onHideTrack = viewModel::onHideTrack,
        onAddToPlaylist = viewModel::onAddToPlaylist,
        onBatchAddToQueue = viewModel::onBatchAddToQueue,
        onShowTrackInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onArtistClick = onArtistClick,
        onAlbumClick = onAlbumClick,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    contentInsets: WindowInsets,
    bottomPadding: Dp,
    persistentBottomPadding: Dp? = null,
    currentPlayingTrackId: TrackId? = null,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortSelected: (TrackSortField) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onEnterSelection: (TrackId) -> Unit,
    onToggleSelection: (TrackId) -> Unit,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHideTrack: (TrackId) -> Unit,
    onAddToPlaylist: (PlaylistId, TrackId?) -> Unit,
    onBatchAddToQueue: () -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onDismissTrackInfo: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by rememberSaveable { mutableStateOf<TrackId?>(null) }
    var hasAutoRequestedFocus by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasAutoRequestedFocus && state.query.isEmpty()) {
            focusRequester.requestFocus()
            keyboardController?.show()
            hasAutoRequestedFocus = true
        }
    }

    val placeholderText = when (state.scopeType) {
        SearchScopeType.ALL_TRACKS -> stringResource(R.string.search_tracks_placeholder_format, state.totalCandidateCount)
        SearchScopeType.PLAYLIST -> stringResource(R.string.search_playlist_placeholder_format, state.totalCandidateCount)
        SearchScopeType.HISTORY -> stringResource(R.string.search_history_placeholder_format, state.totalCandidateCount)
    }

    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val hasContent = state.filteredTracks.isNotEmpty()

    val isTextSort = state.sort.field in listOf(
        TrackSortField.TITLE,
        TrackSortField.ARTIST,
    )

    val selectedSection by remember(listState, state.sections) {
        derivedStateOf {
            if (state.sections.isEmpty()) return@derivedStateOf null
            val firstIndex = listState.firstVisibleItemIndex
            var accumulated = 0
            for (section in state.sections) {
                accumulated += section.tracks.size
                if (firstIndex < accumulated) {
                    return@derivedStateOf section.label
                }
            }
            state.sections.lastOrNull()?.label
        }
    }

    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }

    val gutterMode = remember(state.filteredTracks, canScroll, selectedSection, state.sections, state.sectionPositions, isTextSort) {
        if (!hasContent || !canScroll || !isTextSort) {
            GutterMode.Hidden
        } else {
            GutterMode.Index(
                sortOrder = if (state.sort.direction == TrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING,
                activeSection = selectedSection,
                populatedBuckets = state.sections.map(TrackSection::label).toSet(),
                onSectionSelected = { label ->
                    state.sectionPositions[label]?.let { position ->
                        coroutineScope.launch {
                            listState.scrollToItem(position.coerceAtLeast(0))
                        }
                    }
                },
            )
        }
    }

    val selectionBarHeight = dimensions.minimumTouchTarget
    val resolvedPersistentBottomPadding = persistentBottomPadding ?: run {
        val systemBottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        val miniPlayerPadding = (bottomPadding - contentInsets.asPaddingValues().calculateBottomPadding()).coerceAtLeast(0.dp)
        systemBottomInset + miniPlayerPadding
    }
    val dynamicBottomPadding =
        if (state.isSelectionMode) {
            resolvedPersistentBottomPadding + selectionBarHeight
        } else {
            bottomPadding
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            // 1. 顶部胶囊搜索栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.playerHeaderHeight)
                    .padding(
                        start = dimensions.topBarHorizontalPadding,
                        end = dimensions.topBarHorizontalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            color = MusicTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                            shape = CircleShape,
                        )
                        .padding(horizontal = dimensions.spaceMedium),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_common_search),
                            contentDescription = stringResource(R.string.tracks_search_label),
                            tint = MusicTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = dimensions.spaceExtraSmall),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    style = MusicTheme.typography.bodyMedium,
                                    color = MusicTheme.colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = MusicTheme.typography.bodyLarge.copy(color = MusicTheme.colors.onSurface),
                                cursorBrush = SolidColor(MusicTheme.colors.primary),
                            )
                        }
                        if (state.query.isNotEmpty()) {
                            BareIconButton(
                                onClick = onClearQuery,
                                modifier = Modifier.size(dimensions.minimumTouchTarget),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_common_close_circle),
                                    contentDescription = stringResource(R.string.search_clear),
                                    tint = MusicTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.size(dimensions.spaceLarge),
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        onBack()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.search_cancel),
                        style = MusicTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MusicTheme.colors.primary,
                    )
                }
            }

            // 2. 内容区域：未输入或无匹配时展示纯插画空态，有匹配时展示吸顶操作栏与歌曲列表
            if (!hasContent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = resolvedPersistentBottomPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_empty_state),
                        contentDescription = null,
                        modifier = Modifier.size(dimensions.emptyStateIllustrationSize),
                    )
                }
            } else {
                var sortMenuExpanded by remember { mutableStateOf(false) }

                ListActionBar(
                    isSelectionMode = state.isSelectionMode,
                    itemCount = state.filteredTracks.size,
                    showPlayAll = true,
                    hasPlayableItems = state.filteredTracks.any { it.availability == Availability.AVAILABLE },
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
                                TrackSortField.entries.forEach { field ->
                                    AppDropdownMenuItem(
                                        text = {
                                            val suffix = if (field == state.sort.field) {
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
                    isAllSelected = state.filteredTracks.isNotEmpty() && state.selectedTrackIds.size >= state.filteredTracks.size,
                    onClearSelection = onClearSelection,
                    onToggleSelectAll = onToggleSelectAll,
                )

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
                    items(
                        items = state.filteredTracks,
                        key = { "${it.id.volumeName}:${it.id.mediaStoreId}" },
                    ) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentPlayingTrackId,
                            selected = track.id in state.selectedTrackIds,
                            selectionMode = state.isSelectionMode,
                            playlists = state.playlists,
                            onClick = {
                                if (state.isSelectionMode) {
                                    onToggleSelection(track.id)
                                } else {
                                    onPlayTrack(track)
                                }
                            },
                            onLongClick = {
                                onEnterSelection(track.id)
                            },
                            onAddToQueue = { onAddToQueue(track.id) },
                            onPlayNext = { onPlayNext(track.id) },
                            onHide = { onHideTrack(track.id) },
                            onAddToPlaylist = {
                                singleTrackAddToPlaylistTarget = track.id
                                showAddToPlaylistDialog = true
                            },
                            onShowTrackInfo = { onShowTrackInfo(track) },
                            onNavigateToArtist = onArtistClick,
                            onNavigateToAlbum = onAlbumClick,
                        )
                    }
                }
            }
        }

        // 3. 侧边字母快速索引条
        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(bottom = dynamicBottomPadding),
        )

        // 4. 多选底部批量操作栏（绝对锚定底部，对软键盘完全免疫）
        val systemBottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        val hasMiniPlayer = resolvedPersistentBottomPadding > systemBottomInset + 1.dp
        AnimatedVisibility(
            visible = state.isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (hasMiniPlayer) resolvedPersistentBottomPadding else 0.dp)
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
                        onClick = onBatchAddToQueue,
                    ),
                ),
                contentInsets = contentInsets.exclude(WindowInsets.ime),
                applyBottomInset = !hasMiniPlayer,
            )
        }
    }

    if (showAddToPlaylistDialog) {
        val targetTrackId = singleTrackAddToPlaylistTarget
        AddToPlaylistDialog(
            playlists = state.playlists,
            onSelectPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, targetTrackId)
                showAddToPlaylistDialog = false
                singleTrackAddToPlaylistTarget = null
            },
            onCreatePlaylist = {},
            onDismiss = {
                showAddToPlaylistDialog = false
                singleTrackAddToPlaylistTarget = null
            },
        )
    }

    state.infoTrack?.let { track ->
        TrackInfoViewer(
            track = track,
            metadata = state.infoMetadata,
            loading = state.infoMetadata == null,
            onDismiss = onDismissTrackInfo,
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
