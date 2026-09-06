package com.musicapp.player.feature.albums

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import coil3.compose.AsyncImage
import com.musicapp.player.core.image.AudioArtworkRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.nonInteractiveScrollbar
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuDivider
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.ResetScrollOnChange
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
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
import com.musicapp.player.theme.MusicWindowWidthTier
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreenRoute(
    viewModel: AlbumsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        bottomPadding = bottomPadding,
        onSortSelected = viewModel::selectSort,
        onColumnCountSelected = viewModel::selectColumnCount,
        onAlbumClick = onAlbumClick,
    )
}

@Composable
private fun AlbumsScreen(
    state: AlbumsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    bottomPadding: Dp = 0.dp,
    onSortSelected: (AlbumSortField) -> Unit,
    onColumnCountSelected: (Int) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    gridState.ResetScrollOnChange(state.sort)
    val overscrollEffect = rememberBounceOverscrollEffect(gridState)
    val hasUnknownTop = state.albums.firstOrNull()?.id == UNKNOWN_ALBUM_ID
    val targetAlbumsForSections = if (hasUnknownTop) state.albums.drop(1) else state.albums
    val sections = remember(targetAlbumsForSections, state.sort.field, state.sort.direction) {
        groupAlbumsIntoSections(targetAlbumsForSections, state.sort.field, state.sort.direction)
    }
    val initialOffset = if (hasUnknownTop) 1 else 0
    val sectionPositions = remember(sections, state.sort.direction, initialOffset) {
        sectionStartPositions(sections, state.sort.direction, initialOffset)
    }
    val isTextSort = state.sort.field in listOf(
        AlbumSortField.TITLE,
        AlbumSortField.ARTIST,
    )
    val gutterMode = remember(state.albums, isTextSort, state.sort.direction, sections, sectionPositions) {
        when {
            state.albums.isEmpty() -> GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = albumSortDirectionToSectionOrder(state.sort.direction),
                    activeSectionProvider = { sectionLabelAtPosition(sections, gridState.firstVisibleItemIndex, initialOffset) },
                    populatedBuckets = sections.map(AlbumSection::label).toSet(),
                    onSectionSelected = { label ->
                        sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                gridState.scrollToItem(position)
                            }
                        }
                    },
                )
            else -> GutterMode.Scrollbar
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            AlbumsHeader(
                policy = policy,
                openDrawer = openDrawer,
                trailingContent = {
                    AlbumOptionsMenu(
                        sort = state.sort,
                        columnCount = state.columnCount,
                        onSortSelected = onSortSelected,
                        onColumnCountSelected = onColumnCountSelected,
                    )
                },
            )
            if (state.isLoaded && state.albums.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = bottomPadding),
                    title = stringResource(R.string.albums_empty_title),
                    description = stringResource(R.string.albums_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.columnCount),
                    state = gridState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                    contentPadding =
                        PaddingValues(
                            top = dimensions.spaceSmall,
                            bottom = dimensions.spaceSmall + bottomPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    items(state.albums, key = { it.key }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
            }
        }
        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(bottom = bottomPadding),
        )
    }
}

@Composable
private fun AlbumsHeader(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val iconVisualOffset = (dimensions.minimumTouchTarget - dimensions.spaceLarge) / 2
    val headerStartPadding =
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            dimensions.contentHorizontalPadding - iconVisualOffset
        } else {
            dimensions.contentHorizontalPadding
        }
    val headerEndPadding = dimensions.contentHorizontalPadding - iconVisualOffset
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.playerHeaderHeight)
            .padding(start = headerStartPadding, end = headerEndPadding),
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
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val cardShape = when (dimensions.windowWidthTier) {
        MusicWindowWidthTier.COMPACT -> MusicTheme.shapes.medium
        MusicWindowWidthTier.MEDIUM,
        MusicWindowWidthTier.EXPANDED,
        -> MusicTheme.shapes.large
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(cardShape).clickable(onClick = onClick),
    ) {
        AlbumArtwork(
            album = album,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensions.categoryCardInfoHeight)
                .padding(top = dimensions.spaceExtraSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = album.title.localizedAlbumTitle(),
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
    album: AlbumSummary,
    modifier: Modifier,
) {
    val shape = when (MusicTheme.dimensions.windowWidthTier) {
        MusicWindowWidthTier.COMPACT -> MusicTheme.shapes.medium
        MusicWindowWidthTier.MEDIUM,
        MusicWindowWidthTier.EXPANDED,
        -> MusicTheme.shapes.large
    }
    val artworkDescription = stringResource(R.string.album_artwork_description, album.title.localizedAlbumTitle())
    val request = remember(album.id, album.representativeTrack.id, album.representativeTrack.dateModifiedMs) {
        AudioArtworkRequest.AlbumArtworkRequest(
            albumId = album.id,
            representativeTrackId = album.representativeTrack.id,
            dateModifiedMs = album.representativeTrack.dateModifiedMs,
        )
    }
    AsyncImage(
        model = request,
        contentDescription = artworkDescription,
        modifier = modifier
            .clip(shape)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}

@Composable
private fun AlbumOptionsMenu(
    sort: AlbumSort,
    columnCount: Int,
    onSortSelected: (AlbumSortField) -> Unit,
    onColumnCountSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = MusicTheme.dimensions
    val optionsDescription = stringResource(R.string.albums_options_label)
    Box {
        BareIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_more_vertical),
                contentDescription = optionsDescription,
                tint = MusicTheme.colors.onSurface,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumSortField.entries.forEach { field ->
                val isSelected = sort.field == field
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(field.labelRes()),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_status_check),
                                contentDescription = null,
                                tint = MusicTheme.colors.primary,
                                modifier = Modifier.size(dimensions.spaceMedium),
                            )
                        }
                    } else null,
                    onClick = { onSortSelected(field); expanded = false },
                )
            }
            AppDropdownMenuDivider()
            listOf(
                2 to R.string.albums_column_2,
                3 to R.string.albums_column_3,
                4 to R.string.albums_column_4,
            ).forEach { (count, labelRes) ->
                val isSelected = columnCount == count
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(labelRes),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_status_check),
                                contentDescription = null,
                                tint = MusicTheme.colors.primary,
                                modifier = Modifier.size(dimensions.spaceMedium),
                            )
                        }
                    } else null,
                    onClick = { onColumnCountSelected(count); expanded = false },
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
        AlbumSortField.RELEASE_YEAR -> R.string.sort_release_year
    }
