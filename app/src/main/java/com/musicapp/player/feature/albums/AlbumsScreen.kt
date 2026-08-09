package com.musicapp.player.feature.albums

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.SectionIndexBar
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategoryTrackList
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortMenu
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreenRoute(
    viewModel: AlbumsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        onSortSelected = viewModel::selectSort,
        onArtworkRequested = viewModel::requestArtwork,
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
    onScanMusic: () -> Unit,
    onSortSelected: (AlbumSortField) -> Unit,
    onArtworkRequested: (AlbumSummary) -> Unit,
    onAlbumClick: (AlbumId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val sections = remember(state.albums, state.sort.field) {
        groupAlbumsIntoSections(state.albums, state.sort.field)
    }
    val indexLabels = remember(sections) { sectionIndexLabels(sections) }
    val sectionPositions = remember(sections) { sectionStartPositions(sections) }
    val selectedSection by remember(gridState, sections) {
        derivedStateOf {
            sectionLabelAtPosition(sections, gridState.firstVisibleItemIndex)
        }
    }
    val sectionDescriptions = sections.associate { section ->
        section.label to stringResource(R.string.album_index_label, section.label)
    }
    val showSectionIndex = state.albums.isNotEmpty() && indexLabels.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        ) {
            AlbumsHeader(
                policy = policy,
                openDrawer = openDrawer,
                trailingContent = { AlbumSortMenu(state.sort, onSortSelected) },
            )
            if (state.albums.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                    title = stringResource(R.string.albums_empty_title),
                    description = stringResource(R.string.albums_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(dimensions.adaptiveGridMinimumCellWidth),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                    contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    items(state.albums, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { album ->
                        AlbumCard(
                            album = album,
                            artwork = state.artworkByAlbumId[album.id]
                                ?.takeIf {
                                    it.trackId == album.representativeTrack.id &&
                                        it.dateModifiedMs == album.representativeTrack.dateModifiedMs
                                }
                                ?.artwork
                                ?: ArtworkResult.Placeholder,
                            onArtworkRequested = { onArtworkRequested(album) },
                            onClick = { onAlbumClick(album.id) },
                        )
                    }
                }
            }
        }
        if (showSectionIndex) {
            Box(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
            ) {
                SectionIndexBar(
                    sections = indexLabels,
                    selectedSection = selectedSection,
                    onSectionClick = { label ->
                        sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                gridState.animateScrollToItem(position)
                            }
                        }
                    },
                    sectionContentDescription = { label -> sectionDescriptions.getValue(label) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = dimensions.spaceExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun AlbumsHeader(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.playerHeaderHeight)
            .padding(horizontal = dimensions.topBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            CategoryNavigationIconButton(
                action = CategoryNavigationAction.DRAWER,
                onClick = openDrawer,
            )
        }
        Text(
            text = stringResource(R.string.navigation_albums),
            style = MusicTheme.typography.titleLarge,
            color = MusicTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailingContent()
    }
}

@Composable
private fun AlbumCard(
    album: AlbumSummary,
    artwork: ArtworkResult,
    onArtworkRequested: () -> Unit,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    LaunchedEffect(album.id, album.representativeTrack.id, album.representativeTrack.dateModifiedMs) {
        onArtworkRequested()
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        AlbumArtwork(
            artwork = artwork,
            albumTitle = album.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensions.categoryCardInfoHeight)
                .padding(top = dimensions.spaceExtraSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = album.title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.album_track_count,
                    album.trackCount,
                    album.trackCount,
                ),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumArtwork(
    artwork: ArtworkResult,
    albumTitle: String,
    modifier: Modifier,
) {
    val shape = MusicTheme.shapes.large
    val artworkDescription = stringResource(R.string.album_artwork_description, albumTitle)
    when (artwork) {
        ArtworkResult.Placeholder ->
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(MusicTheme.colors.secondaryContainer)
                    .semantics { contentDescription = artworkDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_album),
                    contentDescription = null,
                    tint = MusicTheme.colors.onSecondaryContainer,
                    modifier = Modifier.fillMaxSize(0.5f),
                )
            }
        is ArtworkResult.Embedded -> {
            val image = artwork.image
            val bitmap = remember(image) {
                Bitmap.createBitmap(
                    image.argbPixels,
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = artworkDescription,
                modifier = modifier.clip(shape),
                contentScale = ContentScale.Crop,
            )
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
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
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
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                title = stringResource(R.string.album_empty_title),
                description = stringResource(R.string.album_empty_description),
            )
        } else {
            CategoryTrackList(
                state.tracks,
                onTrackClick,
                Modifier.weight(1f).padding(horizontal = dimensions.contentHorizontalPadding),
            )
        }
    }
}

@Composable
private fun AlbumSortMenu(sort: AlbumSort, onSelected: (AlbumSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = MusicTheme.dimensions
    val sortDescription = stringResource(
        R.string.albums_sort_label,
        stringResource(sort.field.labelRes()),
        stringResource(sort.direction.labelRes()),
    )
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_sort_alpha),
                contentDescription = sortDescription,
                tint = MusicTheme.colors.onSurface,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
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
