package com.musicapp.player.feature.folders

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategoryTrackRow
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.CategoryTrackSortMenu
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun FoldersScreenRoute(
    viewModel: FoldersViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoldersScreen(state, contentInsets, policy, openDrawer, viewModel::selectSort, onFolderClick)
}

@Composable
fun FolderDetailScreenRoute(
    folderId: FolderId,
    viewModel: FolderDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { viewModel.open(folderId) }
    FolderDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onFolderSortSelected = viewModel::selectFolderSort,
        onTrackSortSelected = viewModel::selectTrackSort,
        onFolderClick = onFolderClick,
        onTrackClick = { viewModel.playTrack(it.id) },
    )
}

@Composable
private fun FoldersScreen(
    state: FoldersUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onSortSelected: (FolderSortField) -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = stringResource(R.string.navigation_folders),
            policy = policy,
            openDrawer = openDrawer,
            trailingContent = { FolderSortMenu(state.sort, onSortSelected) },
        )
        if (state.roots.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.folders_empty_title),
                description = stringResource(R.string.folders_empty_description),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
            ) {
                items(state.roots, key = { it.id.sourceId }) { root ->
                    FolderRow(root, onFolderClick)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FolderDetailScreen(
    state: FolderDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onFolderSortSelected: (FolderSortField) -> Unit,
    onTrackSortSelected: (CategoryTrackSortField) -> Unit,
    onFolderClick: (FolderId) -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = state.displayName ?: stringResource(R.string.folder_unknown_name),
            onBack = onBack,
            trailingContent = {
                TextButton(
                    onClick = onPlayAll,
                    enabled = state.recursiveTracks.any { it.availability == Availability.AVAILABLE },
                ) { Text(stringResource(R.string.category_play_all)) }
                FolderSortMenu(state.folderSort, onFolderSortSelected)
                CategoryTrackSortMenu(
                    sort = state.trackSort,
                    fields = listOf(
                        CategoryTrackSortField.TITLE,
                        CategoryTrackSortField.ARTIST,
                        CategoryTrackSortField.DATE_ADDED,
                        CategoryTrackSortField.DURATION,
                    ),
                    onSelected = onTrackSortSelected,
                )
            },
        )
        if (state.childFolders.isEmpty() && state.directTracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.folder_empty_title),
                description = stringResource(R.string.folder_empty_description),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
            ) {
                if (state.childFolders.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.folder_subfolders_section),
                            style = MusicTheme.typography.titleMedium,
                            modifier = Modifier.padding(dimensions.spaceSmall),
                        )
                    }
                    items(state.childFolders, key = { it.id.sourceId }) { folder ->
                        FolderRow(folder, onFolderClick)
                        HorizontalDivider()
                    }
                }
                if (state.directTracks.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.folder_tracks_section),
                            style = MusicTheme.typography.titleMedium,
                            modifier = Modifier.padding(dimensions.spaceSmall),
                        )
                    }
                    items(
                        state.directTracks,
                        key = { "${it.id.volumeName}:${it.id.mediaStoreId}" },
                    ) { track -> CategoryTrackRow(track, onTrackClick) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderNode, onClick: (FolderId) -> Unit) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(dimensions.trackListItemHeight)
            .clickable { onClick(folder.id) }
            .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.displayName, style = MusicTheme.typography.titleMedium, maxLines = 1)
            Text(
                if (folder.id.relativePath.isEmpty()) {
                    stringResource(R.string.folder_volume_root)
                } else {
                    folder.id.relativePath
                },
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            stringResource(R.string.category_track_count, folder.recursiveTrackCount),
            style = MusicTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun FolderSortMenu(sort: FolderSort, onSelected: (FolderSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(sort.field.labelRes()) + stringResource(sort.direction.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FolderSortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    onClick = { onSelected(field); expanded = false },
                )
            }
        }
    }
}

@StringRes
private fun FolderSortField.labelRes(): Int =
    when (this) {
        FolderSortField.NAME -> R.string.sort_name
        FolderSortField.TRACK_COUNT -> R.string.sort_track_count
    }
