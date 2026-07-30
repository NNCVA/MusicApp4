package com.musicapp.player.feature.albums

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackList
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortMenu
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun AlbumsScreenRoute(
    viewModel: AlbumsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onSortSelected = viewModel::selectSort,
        onAlbumClick = onAlbumClick,
    )
}

@Composable
fun AlbumDetailScreenRoute(
    albumId: AlbumId,
    viewModel: AlbumDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(albumId) { viewModel.open(albumId) }
    AlbumDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onSortSelected = viewModel::selectSort,
        onTrackClick = { viewModel.playTrack(it.id) },
    )
}

@Composable
private fun AlbumsScreen(
    state: AlbumsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onSortSelected: (AlbumSortField) -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = stringResource(R.string.navigation_albums),
            policy = policy,
            openDrawer = openDrawer,
            trailingContent = { AlbumSortMenu(state.sort, onSortSelected) },
        )
        if (state.albums.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.albums_empty_title),
                description = stringResource(R.string.albums_empty_description),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(dimensions.adaptiveGridMinimumCellWidth),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                items(state.albums, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { album ->
                    Surface(
                        onClick = { onAlbumClick(album.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.categoryCardMinHeight),
                        shape = MusicTheme.shapes.large,
                        color = MusicTheme.colors.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensions.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
                        ) {
                            Text(album.title, style = MusicTheme.typography.titleLarge, maxLines = 2)
                            Text(album.artistName, style = MusicTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                pluralStringResource(
                                    R.plurals.category_track_count,
                                    album.trackCount,
                                    album.trackCount,
                                ),
                                style = MusicTheme.typography.labelMedium,
                                color = MusicTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSortSelected: (CategoryTrackSortField) -> Unit,
    onTrackClick: (com.musicapp.player.core.domain.model.Track) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = state.title ?: stringResource(R.string.album_unknown_title),
            onBack = onBack,
            trailingContent = {
                TextButton(
                    onClick = onPlayAll,
                    enabled = state.tracks.any { it.availability == Availability.AVAILABLE },
                ) { Text(stringResource(R.string.category_play_all)) }
                CategoryTrackSortMenu(
                    sort = state.sort,
                    fields = listOf(
                        CategoryTrackSortField.TITLE,
                        CategoryTrackSortField.ARTIST,
                        CategoryTrackSortField.DATE_ADDED,
                        CategoryTrackSortField.DURATION,
                    ),
                    onSelected = onSortSelected,
                )
            },
        )
        if (state.tracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.album_empty_title),
                description = stringResource(R.string.album_empty_description),
            )
        } else {
            CategoryTrackList(state.tracks, onTrackClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AlbumSortMenu(sort: AlbumSort, onSelected: (AlbumSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(sort.field.labelRes()) + stringResource(sort.direction.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumSortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    onClick = { onSelected(field); expanded = false },
                )
            }
        }
    }
}

@StringRes
private fun AlbumSortField.labelRes(): Int =
    when (this) {
        AlbumSortField.TITLE -> R.string.sort_title
        AlbumSortField.ARTIST -> R.string.sort_artist
        AlbumSortField.TRACK_COUNT -> R.string.sort_track_count
        AlbumSortField.DATE_ADDED -> R.string.sort_date_added
    }
