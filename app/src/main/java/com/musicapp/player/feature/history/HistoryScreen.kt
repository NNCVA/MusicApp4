package com.musicapp.player.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.MenuIconPalette
import com.musicapp.player.core.designsystem.component.SearchableTopBar
import com.musicapp.player.core.designsystem.component.SelectionBarAction
import com.musicapp.player.core.designsystem.component.SelectionBottomBar
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun HistoryScreenRoute(
    viewModel: HistoryViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onShowMessage: (Int, List<Any>) -> Unit = { _, _ -> },
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    val canHandleBack =
        state.isSelectionMode ||
            state.isSearchActive ||
            state.clearConfirmationVisible ||
            state.deleteConfirmationTrackIds != null ||
            state.infoTrack != null

    BackHandler(enabled = canHandleBack) {
        viewModel.onBack()
    }

    HistoryScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        bottomPadding = bottomPadding,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onQueryChange = viewModel::setQuery,
        onEnterSelectionMode = { viewModel.enterSelectionMode() },
        onExitSelectionMode = viewModel::exitSelectionMode,
        onSelectAllVisible = viewModel::selectAllVisible,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onTrackClick = { entry ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(entry.trackId)
            } else {
                viewModel.playTrack(entry.trackId)
            }
        },
        onTrackLongClick = { entry ->
            if (!state.isSelectionMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            viewModel.toggleSelection(entry.trackId)
        },
        onPlayAll = viewModel::playAll,
        onPlayNext = viewModel::playNext,
        onDeleteTrack = viewModel::requestDeleteTrack,
        onRequestDeleteSelected = viewModel::requestDeleteSelected,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onRequestClearHistory = viewModel::requestClearHistory,
        onCancelClearHistory = viewModel::cancelClearHistory,
        onConfirmClearHistory = viewModel::confirmClearHistory,
        onAddSelectedToQueue = viewModel::addSelectedToQueue,
        onAddSelectedToPlaylist = viewModel::addSelectedToPlaylist,
        onAddSingleTrackToPlaylist = viewModel::addSingleTrackToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onShowTrackInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onAcknowledgeBatchResult = viewModel::acknowledgeBatchResult,
        onAcknowledgeUserMessage = viewModel::acknowledgeUserMessage,
        onShowMessage = onShowMessage,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAllVisible: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onTrackClick: (HistoryEntry) -> Unit,
    onTrackLongClick: (HistoryEntry) -> Unit,
    onPlayAll: () -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onDeleteTrack: (TrackId) -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRequestClearHistory: () -> Unit,
    onCancelClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onAddSelectedToQueue: () -> Unit,
    onAddSelectedToPlaylist: (PlaylistId) -> Unit,
    onAddSingleTrackToPlaylist: (TrackId, PlaylistId) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onDismissTrackInfo: () -> Unit,
    onAcknowledgeBatchResult: () -> Unit,
    onAcknowledgeUserMessage: () -> Unit,
    onShowMessage: (Int, List<Any>) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val hasMiniPlayer = bottomPadding > 0.dp

    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by remember { mutableStateOf<TrackId?>(null) }

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

    LaunchedEffect(state.userMessage) {
        val message = state.userMessage ?: return@LaunchedEffect
        when (message) {
            is HistoryUserMessage.DeleteSuccess -> {
                onShowMessage(R.plurals.history_delete_result, listOf(message.count))
            }
            HistoryUserMessage.DeleteFailed -> {
                onShowMessage(R.string.history_delete_failed, emptyList())
            }
        }
        onAcknowledgeUserMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 1. SearchableTopBar (固定在顶部)
                SearchableTopBar(
                    title = stringResource(R.string.navigation_history),
                    navigationAction = CategoryNavigationAction.BACK,
                    onNavigationClick = onBack,
                    searchActive = state.isSearchActive,
                    searchQuery = state.query,
                    onSearchQueryChange = onQueryChange,
                    onCloseSearch = onCloseSearch,
                    searchPlaceholder = stringResource(R.string.history_search_label),
                    onOpenSearch = null,
                    trailingContent = {
                        if (!state.isSelectionMode) {
                            var topBarMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                BareIconButton(
                                    onClick = { topBarMenuExpanded = true },
                                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_common_more_vertical),
                                        contentDescription = stringResource(R.string.history_more_actions),
                                        tint = MusicTheme.colors.onSurfaceVariant,
                                        modifier = Modifier.size(dimensions.spaceLarge),
                                    )
                                }

                                AppDropdownMenu(
                                    expanded = topBarMenuExpanded,
                                    onDismissRequest = { topBarMenuExpanded = false },
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text(stringResource(R.string.history_search_action)) },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_common_search),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            topBarMenuExpanded = false
                                            onOpenSearch()
                                        },
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text(stringResource(R.string.history_select_action)) },
                                        iconTint = MenuIconPalette.SelectAll,
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_status_check),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            topBarMenuExpanded = false
                                            onEnterSelectionMode()
                                        },
                                    )
                                    HorizontalDivider(
                                        color = MusicTheme.colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
                                        thickness = 1.dp,
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text(stringResource(R.string.history_clear)) },
                                        isDestructive = true,
                                        enabled = state.entries.isNotEmpty() && !state.isClearing,
                                        iconTint = MenuIconPalette.Delete,
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_common_delete),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            topBarMenuExpanded = false
                                            onRequestClearHistory()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )

                if (!state.isLoading && state.visibleEntries.isNotEmpty()) {
                    ListActionBar(
                        isSelectionMode = state.isSelectionMode,
                        selectedCount = state.selectedTrackIds.size,
                        isAllSelected = state.isAllSelected,
                        onClearSelection = onExitSelectionMode,
                        onToggleSelectAll = onToggleSelectAll,
                        showPlayAll = true,
                        hasPlayableItems = state.visibleEntries.any { it.isActionable },
                        onPlayAll = onPlayAll,
                        itemCount = state.visibleEntries.size,
                        itemCountDescription =
                            pluralStringResource(
                                R.plurals.history_item_count,
                                state.visibleEntries.size,
                                state.visibleEntries.size,
                            ),
                        trailingContent = {},
                    )
                }
            }

            // 2. 列表内容
            when {
                state.isLoading -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = dimensions.contentHorizontalPadding)
                                .padding(bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.entries.isEmpty() -> {
                    EmptyState(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = dimensions.contentHorizontalPadding)
                                .padding(bottom = bottomPadding),
                        title = stringResource(R.string.history_empty_title),
                        description = stringResource(R.string.history_empty_description),
                    )
                }
                state.visibleEntries.isEmpty() -> {
                    EmptyState(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = dimensions.contentHorizontalPadding)
                                .padding(bottom = bottomPadding),
                        title = stringResource(R.string.history_no_results_title),
                        description = stringResource(R.string.history_no_results_description),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        overscrollEffect = overscrollEffect,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding =
                            PaddingValues(
                                top = dimensions.spaceSmall,
                                bottom = dimensions.spaceSmall + bottomPadding + if (state.isSelectionMode) dimensions.minimumTouchTarget else 0.dp,
                            ),
                    ) {

                        items(
                            items = state.visibleEntries,
                            key = { "${it.trackId.volumeName}:${it.trackId.mediaStoreId}" },
                        ) { entry ->
                            val track = entry.track
                            if (track != null) {
                                TrackRow(
                                    track = track,
                                    selected = entry.trackId in state.selectedTrackIds,
                                    selectionMode = state.isSelectionMode,
                                    onClick = { onTrackClick(entry) },
                                    onLongClick = { onTrackLongClick(entry) },
                                    trailingContent =
                                        if (!state.isSelectionMode) {
                                            {
                                                var menuExpanded by remember(entry.trackId) { mutableStateOf(false) }
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
                                                    HistoryTrackActionsMenu(
                                                        expanded = menuExpanded,
                                                        onDismissRequest = { menuExpanded = false },
                                                        entry = entry,
                                                        onDeleteRecord = { onDeleteTrack(entry.trackId) },
                                                        onPlayNext = { onPlayNext(entry.trackId) },
                                                        onAddToPlaylist = {
                                                            singleTrackAddToPlaylistTarget = entry.trackId
                                                            showAddToPlaylistDialog = true
                                                        },
                                                        onShowTrackInfo = { onShowTrackInfo(track) },
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                )
                            } else {
                                // 缺失曲目的稳定视觉占位项
                                MissingTrackHistoryRow(
                                    entry = entry,
                                    selected = entry.trackId in state.selectedTrackIds,
                                    selectionMode = state.isSelectionMode,
                                    onClick = { onTrackClick(entry) },
                                    onLongClick = { onTrackLongClick(entry) },
                                    onDeleteRecord = { onDeleteTrack(entry.trackId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 底部多选操作栏 (位于 MiniPlayer 上方)
        AnimatedVisibility(
            visible = state.isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (hasMiniPlayer) bottomPadding else 0.dp)
                    .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Horizontal)),
        ) {
            SelectionBottomBar(
                actions =
                    listOf(
                        SelectionBarAction(
                            label = stringResource(R.string.history_delete_record),
                            iconRes = R.drawable.ic_common_delete,
                            isDestructive = true,
                            enabled = state.selectedTrackIds.isNotEmpty(),
                            onClick = onRequestDeleteSelected,
                        ),
                        SelectionBarAction(
                            label = stringResource(R.string.selection_add_to_playlist),
                            iconRes = R.drawable.ic_common_add,
                            enabled = state.hasPlayableSelection,
                            onClick = {
                                singleTrackAddToPlaylistTarget = null
                                showAddToPlaylistDialog = true
                            },
                        ),
                        SelectionBarAction(
                            label = stringResource(R.string.selection_add_to_queue),
                            iconRes = R.drawable.ic_common_queue_add,
                            enabled = state.hasPlayableSelection,
                            onClick = onAddSelectedToQueue,
                        ),
                    ),
                contentInsets = contentInsets,
                applyBottomInset = !hasMiniPlayer,
            )
        }
    }

    // 4. 清空历史二次确认
    if (state.clearConfirmationVisible) {
        ConfirmationDialog(
            title = stringResource(R.string.history_clear_confirm_title),
            text = stringResource(R.string.history_clear_confirm_description),
            confirmLabel = stringResource(R.string.history_clear_confirm_action),
            cancelLabel = stringResource(R.string.history_clear_cancel),
            onConfirm = onConfirmClearHistory,
            onDismiss = onCancelClearHistory,
            isDestructive = true,
        )
    }

    // 5. 单条/批量删除记录二次确认
    val deleteTargets = state.deleteConfirmationTrackIds
    if (deleteTargets != null) {
        val isSingle = deleteTargets.size == 1
        ConfirmationDialog(
            title =
                if (isSingle) {
                    stringResource(R.string.history_delete_confirm_single_title)
                } else {
                    stringResource(R.string.history_delete_confirm_title)
                },
            text =
                if (isSingle) {
                    stringResource(R.string.history_delete_confirm_single_description)
                } else {
                    pluralStringResource(
                        R.plurals.history_delete_confirm_description,
                        deleteTargets.size,
                        deleteTargets.size,
                    )
                },
            confirmLabel = stringResource(R.string.history_delete_confirm_action),
            cancelLabel = stringResource(R.string.history_clear_cancel),
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
            isDestructive = true,
        )
    }

    // 6. 加入歌单与新建歌单弹窗
    if (showAddToPlaylistDialog) {
        val targetTrackId = singleTrackAddToPlaylistTarget
        AddToPlaylistDialog(
            playlists = state.playlists,
            onSelectPlaylist = { playlistId ->
                if (targetTrackId != null) {
                    onAddSingleTrackToPlaylist(targetTrackId, playlistId)
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

    // 7. 歌曲信息查看器
    state.infoTrack?.let { track ->
        TrackInfoViewer(
            track = track,
            metadata = state.infoMetadata,
            loading = state.isInfoLoading,
            onDismiss = onDismissTrackInfo,
        )
    }
}

/**
 * 针对找不到媒体库 Track 的旧记录的 TrackRow 占位行。
 */
@Composable
private fun MissingTrackHistoryRow(
    entry: HistoryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRecord: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions

    val rowModifier =
        Modifier
            .fillMaxWidth()
            .height(dimensions.trackListItemHeight)
            .clip(MusicTheme.shapes.medium)
            .then(
                if (selectionMode && selected) {
                    Modifier.background(MusicTheme.colors.secondaryContainer.copy(alpha = 0.5f))
                } else {
                    Modifier
                },
            )
            .then(
                if (selectionMode) {
                    Modifier
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onClick() },
                        )
                        .semantics(mergeDescendants = true) {}
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = dimensions.contentHorizontalPadding)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        // 占位封面
        Surface(
            modifier = Modifier.size(dimensions.trackArtworkSize),
            shape = MusicTheme.shapes.extraSmall,
            color = MusicTheme.colors.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_album),
                    contentDescription = null,
                    tint = MusicTheme.colors.onSecondaryContainer,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        R.string.history_missing_track,
                        entry.trackId.volumeName,
                        entry.trackId.mediaStoreId,
                    ),
                style = MusicTheme.typography.compactTrackTitle,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.history_unavailable_record),
                style = MusicTheme.typography.compactTrackArtist,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selectionMode) {
            Box(
                modifier =
                    Modifier
                        .size(dimensions.minimumTouchTarget)
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (selected) R.drawable.ic_common_check_circle else R.drawable.ic_common_radio_button_unchecked,
                        ),
                    contentDescription = null,
                    tint = if (selected) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        } else {
            var menuExpanded by remember(entry.trackId) { mutableStateOf(false) }
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
                HistoryTrackActionsMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    entry = entry,
                    onDeleteRecord = onDeleteRecord,
                    onPlayNext = {},
                    onAddToPlaylist = {},
                    onShowTrackInfo = {},
                )
            }
        }
    }
}
