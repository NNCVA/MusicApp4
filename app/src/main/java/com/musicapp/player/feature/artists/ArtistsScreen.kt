package com.musicapp.player.feature.artists

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategoryTrackList
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.CategoryTrackSortMenu
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        onArtworkRequested = viewModel::requestArtwork,
        onArtistClick = onArtistClick,
    )
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
    onScanMusic: () -> Unit,
    onArtworkRequested: (ArtistSummary) -> Unit,
    onArtistClick: (ArtistId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sections = remember(state.artists) { groupArtistsIntoSections(state.artists) }
    val displayArtists = remember(sections) { sections.flatMap(ArtistSection::artists) }
    val sectionPositions = remember(sections) { sectionStartPositions(sections) }
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
    val gutterMode = remember(displayArtists, canScroll, selectedSection, sections, sectionPositions) {
        if (displayArtists.isEmpty() || !canScroll) {
            GutterMode.Hidden
        } else {
            GutterMode.Index(
                sortOrder = SectionSortOrder.ASCENDING,
                activeSection = selectedSection,
                populatedBuckets = sections.map(ArtistSection::label).toSet(),
                onSectionSelected = { label ->
                    sectionPositions[label]?.let { position ->
                        coroutineScope.launch {
                            listState.scrollToItem(position.coerceIn(0, displayArtists.lastIndex))
                        }
                    }
                },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        ) {
            ArtistsHeader(policy = policy, openDrawer = openDrawer)
            if (state.artists.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f)
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                    title = stringResource(R.string.artists_empty_title),
                    description = stringResource(R.string.artists_empty_description),
                    actionLabel = stringResource(R.string.navigation_scan_music),
                    actionIconRes = R.drawable.ic_sidebar_scan,
                    onAction = onScanMusic,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(
                        top = dimensions.spaceExtraSmall,
                        bottom = dimensions.spaceSmall,
                    ),
                ) {
                    items(displayArtists, key = { it.id.name }) { artist ->
                        val artworkState = state.artworkByArtistId[artist.id]
                        val artwork = artworkState
                            ?.takeIf { it.matches(artist) }
                            ?.artwork
                            ?: ArtworkResult.Placeholder
                        ArtistRow(
                            artist = artist,
                            artwork = artwork,
                            onArtworkRequested = { onArtworkRequested(artist) },
                            onClick = { onArtistClick(artist.id) },
                        )
                    }
                }
            }
        }
        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        )
    }
}

@Composable
private fun ArtistsHeader(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
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
            text = stringResource(R.string.navigation_artists),
            style = MusicTheme.typography.titleLarge,
            color = MusicTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
    }
}

@Composable
private fun ArtistRow(
    artist: ArtistSummary,
    artwork: ArtworkResult,
    onArtworkRequested: () -> Unit,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    LaunchedEffect(artist.id, artist.artworkCandidates) {
        onArtworkRequested()
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.minimumTouchTarget)
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimensions.contentHorizontalPadding,
                vertical = dimensions.spaceSmallMedium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistArtwork(
            artwork = artwork,
            artistName = artist.displayName,
            modifier = Modifier.size(dimensions.trackArtworkSize),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = artist.displayName,
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
    artwork: ArtworkResult,
    artistName: String,
    modifier: Modifier,
) {
    val artworkDescription = stringResource(R.string.artist_artwork_description, artistName)
    when (artwork) {
        ArtworkResult.Placeholder ->
            Box(
                modifier = modifier
                    .clip(CircleShape)
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
                modifier = modifier.clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
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
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
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
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                title = stringResource(R.string.artist_empty_title),
                description = stringResource(R.string.artist_empty_description),
            )
        } else {
            CategoryTrackList(
                state.tracks,
                onTrackClick,
                Modifier.weight(1f),
            )
        }
    }
}
