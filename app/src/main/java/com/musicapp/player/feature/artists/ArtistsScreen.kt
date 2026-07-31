package com.musicapp.player.feature.artists

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackList
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.CategoryTrackSortMenu
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun ArtistsScreenRoute(
    viewModel: ArtistsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onArtistClick: (ArtistId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistsScreen(state, contentInsets, policy, openDrawer, viewModel::selectSort, onArtistClick)
}

@Composable
fun ArtistDetailScreenRoute(
    artistId: ArtistId,
    viewModel: ArtistDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.open(artistId) }
    ArtistDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onSortSelected = viewModel::selectSort,
        onTrackClick = { viewModel.playTrack(it.id) },
    )
}

@Composable
private fun ArtistsScreen(
    state: ArtistsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onSortSelected: (ArtistSortField) -> Unit,
    onArtistClick: (ArtistId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = stringResource(R.string.navigation_artists),
            policy = policy,
            navigationAction = CategoryNavigationAction.DRAWER,
            onNavigationClick = openDrawer,
            trailingContent = { ArtistSortMenu(state.sort, onSortSelected) },
        )
        if (state.artists.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.artists_empty_title),
                description = stringResource(R.string.artists_empty_description),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(dimensions.adaptiveGridMinimumCellWidth),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                items(state.artists, key = { it.id.mediaStoreId }) { artist ->
                    Surface(
                        onClick = { onArtistClick(artist.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.categoryCardMinHeight),
                        shape = MusicTheme.shapes.large,
                        color = MusicTheme.colors.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensions.spaceMedium),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
                        ) {
                            Text(artist.displayName, style = MusicTheme.typography.titleLarge, maxLines = 2)
                            Text(
                                pluralStringResource(
                                    R.plurals.category_track_count,
                                    artist.trackCount,
                                    artist.trackCount,
                                ),
                                style = MusicTheme.typography.bodyMedium,
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.category_album_count,
                                    artist.albumCount,
                                    artist.albumCount,
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
private fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSortSelected: (CategoryTrackSortField) -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        CategoryHeader(
            title = state.displayName ?: stringResource(R.string.artist_unknown_name),
            onBack = onBack,
            trailingContent = {
                TextButton(
                    onClick = onPlayAll,
                    enabled = state.tracks.any { it.availability == Availability.AVAILABLE },
                ) { Text(stringResource(R.string.category_play_all)) }
                CategoryTrackSortMenu(
                    sort = state.sort,
                    fields = listOf(
                        CategoryTrackSortField.ALBUM,
                        CategoryTrackSortField.TITLE,
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
                title = stringResource(R.string.artist_empty_title),
                description = stringResource(R.string.artist_empty_description),
            )
        } else {
            CategoryTrackList(state.tracks, onTrackClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArtistSortMenu(sort: ArtistSort, onSelected: (ArtistSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(sort.field.labelRes()) + stringResource(sort.direction.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ArtistSortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    onClick = { onSelected(field); expanded = false },
                )
            }
        }
    }
}

@StringRes
private fun ArtistSortField.labelRes(): Int =
    when (this) {
        ArtistSortField.NAME -> R.string.sort_name
        ArtistSortField.TRACK_COUNT -> R.string.sort_track_count
        ArtistSortField.ALBUM_COUNT -> R.string.sort_album_count
    }
