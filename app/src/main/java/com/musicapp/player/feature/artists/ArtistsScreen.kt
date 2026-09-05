package com.musicapp.player.feature.artists

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.LoadingState
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.localizedArtistName
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.image.AudioArtworkRequest
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.launch

@Composable
fun ArtistsScreenRoute(
    viewModel: ArtistsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onArtistClick: (ArtistId) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        bottomPadding = bottomPadding,
        onSortSelected = viewModel::selectSort,
        onArtistClick = onArtistClick,
    )
}

@Composable
private fun ArtistsScreen(
    state: ArtistsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onArtistClick: (ArtistId) -> Unit,
    bottomPadding: Dp = 0.dp,
    onSortSelected: (ArtistSortField) -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val isTextSort = state.sort.field == ArtistSortField.NAME
    val sections = remember(state.artists, isTextSort, state.sort.direction) {
        if (isTextSort) {
            groupArtistsIntoSections(state.artists, state.sort.direction)
        } else {
            emptyList()
        }
    }
    val displayArtists = state.artists
    val sectionPositions = remember(sections, isTextSort, state.sort.direction) {
        if (isTextSort) {
            sectionStartPositions(sections, state.sort.direction)
        } else {
            emptyMap()
        }
    }
    val gutterMode = remember(state.isLoaded, displayArtists, isTextSort, state.sort.direction, sections, sectionPositions) {
        when {
            !state.isLoaded || displayArtists.isEmpty() -> GutterMode.Hidden
            isTextSort ->
                GutterMode.Index(
                    sortOrder = artistSortDirectionToSectionOrder(state.sort.direction),
                    activeSectionProvider = { sectionLabelAtPosition(sections, listState.firstVisibleItemIndex) },
                    populatedBuckets = sections.map(ArtistSection::label).toSet(),
                    onSectionSelected = { label ->
                        sectionPositions[label]?.let { position ->
                            coroutineScope.launch {
                                listState.scrollToItem(position.coerceIn(0, displayArtists.lastIndex))
                            }
                        }
                    },
                )
            else -> GutterMode.Scrollbar
        }
    }

    val scrollbarModifier =
        if (gutterMode !is GutterMode.Index) {
            listState.scrollIndicatorState?.let { scrollIndicatorState ->
                Modifier.nonInteractiveScrollbar(scrollIndicatorState, Orientation.Vertical)
            } ?: Modifier
        } else {
            Modifier
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            ArtistsHeader(
                policy = policy,
                openDrawer = openDrawer,
                trailingContent = {
                    ArtistOptionsMenu(
                        sort = state.sort,
                        onSortSelected = onSortSelected,
                    )
                },
            )
            if (!state.isLoaded) {
                LoadingState(modifier = Modifier.weight(1f))
            } else if (state.artists.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding)
                        .padding(bottom = bottomPadding),
                    title = stringResource(R.string.artists_empty_title),
                    description = stringResource(R.string.artists_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else {
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxWidth().weight(1f).then(scrollbarModifier),
                    contentPadding = PaddingValues(
                        top = dimensions.spaceExtraSmall,
                        bottom = dimensions.spaceSmall + bottomPadding,
                    ),
                ) {
                    items(displayArtists, key = { it.id.name }) { artist ->
                        ArtistRow(
                            artist = artist,
                            onClick = { onArtistClick(artist.id) },
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
private fun ArtistsHeader(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    trailingContent: @Composable () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val iconVisualOffset = (dimensions.minimumTouchTarget - dimensions.spaceLarge)/2
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
            text = stringResource(R.string.navigation_artists),
            style = MusicTheme.typography.titleLarge,
            color = MusicTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailingContent()
    }
}

@Composable
private fun ArtistOptionsMenu(
    sort: ArtistSort,
    onSortSelected: (ArtistSortField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = MusicTheme.dimensions
    val optionsDescription = stringResource(R.string.artists_options_label)
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
            ArtistSortField.entries.forEach { field ->
                val isSelected = sort.field == field
                val suffix = if (isSelected) {
                    stringResource(sort.direction.labelRes())
                } else {
                    ""
                }
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(field.labelRes()) + suffix,
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
                    onClick = {
                        onSortSelected(field)
                        expanded = false
                    },
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
    }

@Composable
private fun ArtistRow(
    artist: ArtistSummary,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.minimumTouchTarget)
            .clip(MusicTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensions.contentHorizontalPadding,
                vertical = dimensions.spaceSmallMedium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistArtwork(
            artist = artist,
            modifier = Modifier.size(dimensions.trackArtworkSize),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = artist.displayName.localizedArtistName(),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.artist_track_count,
                    artist.trackCount,
                    artist.trackCount,
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
private fun ArtistArtwork(
    artist: ArtistSummary,
    modifier: Modifier,
) {
    val repTrack = remember(artist.id) { artist.sortedArtworkCandidates().firstOrNull() }
    val request = remember(artist.id, repTrack?.id, repTrack?.dateModifiedMs) {
        AudioArtworkRequest.ArtistArtworkRequest(
            artistName = artist.id.name,
            representativeTrackId = repTrack?.id,
            dateModifiedMs = repTrack?.dateModifiedMs ?: 0L,
        )
    }
    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.artist_artwork_description, artist.displayName.localizedArtistName()),
        modifier = modifier.clip(CircleShape).background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}
