package com.musicapp.player.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.QualityBadge
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.TrackActionsMenu
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.designsystem.component.resolveQuality
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.image.AudioArtworkRequest
import com.musicapp.player.theme.MusicTheme
import java.util.Locale

@Composable
fun AlbumDetailScreenRoute(
    albumId: AlbumId,
    viewModel: AlbumDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.infoTrack != null) {
        viewModel.dismissTrackInfo()
    }
    LaunchedEffect(albumId) {
        viewModel.open(albumId)
    }

    AlbumDetailScreen(
        state = state,
        contentInsets = contentInsets,
        bottomPadding = bottomPadding,
        onBack = onBack,
        onTrackClick = viewModel::playTrack,
        onAddToQueue = viewModel::addToQueue,
        onPlayNext = viewModel::playNext,
        onAddToPlaylist = viewModel::addToPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onHideTrack = viewModel::hideTrack,
        onShowTrackInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onArtistClick = onArtistClick,
    )
}

@Composable
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onTrackClick: (TrackId) -> Unit,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onHideTrack: (TrackId) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onDismissTrackInfo: () -> Unit,
    onArtistClick: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)

    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var singleTrackAddToPlaylistTarget by remember { mutableStateOf<TrackId?>(null) }

    BackHandler(enabled = showAddToPlaylistDialog || showCreatePlaylistDialog) {
        if (showCreatePlaylistDialog) {
            showCreatePlaylistDialog = false
        } else {
            showAddToPlaylistDialog = false
            singleTrackAddToPlaylistTarget = null
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    val topInsetPadding = contentInsets.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        // Ambient Hero blurred background behind transparent status bar
        if (state.representativeTrack != null && state.albumId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp + topInsetPadding),
            ) {
                AsyncImage(
                    model = AudioArtworkRequest.AlbumArtworkRequest(
                        albumId = state.albumId,
                        representativeTrackId = state.representativeTrack.id,
                        dateModifiedMs = state.representativeTrack.dateModifiedMs,
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 42.dp)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.38f),
                                    0.45f to Color.White.copy(alpha = 0.20f),
                                    0.75f to Color.White.copy(alpha = 0.05f),
                                    1.0f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
            }
        }

        // Main scrollable detail list
        if (state.isUnavailable) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimensions.contentHorizontalPadding)
                    .padding(bottom = dimensions.spaceSmall + bottomPadding),
                title = stringResource(R.string.album_detail_unavailable_title),
                description = stringResource(R.string.album_detail_unavailable_description),
            )
        } else {
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topInsetPadding + 56.dp,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
            ) {
                // Item 0: Hero Section
                item(key = "album_hero") {
                    AlbumHeroSection(
                        state = state,
                        modifier = Modifier.padding(horizontal = dimensions.contentHorizontalPadding),
                    )
                }

                // Item 1: Stats Section
                item(key = "album_stats") {
                    AlbumStatsSection(state = state)
                }

                // Empty album state if no tracks
                if (state.isLoaded && state.tracks.isEmpty()) {
                    item(key = "album_empty") {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensions.spaceLarge),
                            title = stringResource(R.string.album_empty_title),
                            description = stringResource(R.string.album_empty_description),
                        )
                    }
                }

                // Track rows
                items(
                    items = state.tracks,
                    key = { "${it.track.id.volumeName}:${it.track.id.mediaStoreId}" },
                ) { presentation ->
                    AlbumTrackItem(
                        presentation = presentation,
                        playlists = state.playlists,
                        onClick = { onTrackClick(presentation.track.id) },
                        onAddToQueue = { onAddToQueue(presentation.track.id) },
                        onPlayNext = { onPlayNext(presentation.track.id) },
                        onAddToPlaylist = {
                            singleTrackAddToPlaylistTarget = presentation.track.id
                            showAddToPlaylistDialog = true
                        },
                        onHide = { onHideTrack(presentation.track.id) },
                        onShowTrackInfo = { onShowTrackInfo(presentation.track) },
                    )
                }

                // Artists section
                if (state.artists.isNotEmpty()) {
                    item(key = "album_artists") {
                        AlbumArtistsSection(
                            artists = state.artists,
                            onArtistClick = onArtistClick,
                        )
                    }
                }
            }
        }

        // Fixed Top Navigation Bar
        AlbumDetailTopBar(
            title = state.title ?: stringResource(R.string.album_unknown_title),
            showTitle = showTopBarTitle,
            onBack = onBack,
            contentInsets = contentInsets,
        )

        // Track Info Dialog
        if (state.infoTrack != null) {
            TrackInfoViewer(
                track = state.infoTrack,
                metadata = state.infoMetadata,
                loading = state.infoMetadata == null,
                onDismiss = onDismissTrackInfo,
            )
        }

        if (showAddToPlaylistDialog) {
            val targetTrackId = singleTrackAddToPlaylistTarget
            AddToPlaylistDialog(
                playlists = state.playlists,
                onSelectPlaylist = { playlistId ->
                    if (targetTrackId != null) {
                        onAddToPlaylist(targetTrackId, playlistId)
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
    }
}

@Composable
private fun AlbumDetailTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    contentInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(56.dp)
            .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareIconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_navigation_back),
                contentDescription = stringResource(R.string.category_back),
                tint = MusicTheme.colors.onSurface,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = dimensions.spaceSmall)
                    .semantics {
                        contentDescription = title
                    },
            )
        }
    }
}

@Composable
private fun AlbumHeroSection(
    state: AlbumDetailUiState,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // High quality square rounded cover
        Surface(
            shape = MusicTheme.shapes.large,
            color = MusicTheme.colors.surfaceVariant,
            modifier = Modifier.size(128.dp),
        ) {
            AsyncImage(
                model = state.representativeTrack?.let {
                    AudioArtworkRequest.AlbumArtworkRequest(
                        albumId = state.albumId ?: AlbumId(it.id.volumeName, 1L),
                        representativeTrackId = it.id,
                        dateModifiedMs = it.dateModifiedMs,
                    )
                },
                contentDescription = stringResource(
                    R.string.album_artwork_description,
                    state.title ?: "",
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Title, Artist, Bit Depth, Sample Rate
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = state.title ?: stringResource(R.string.album_unknown_title),
                style = MusicTheme.typography.headlineSmall,
                color = MusicTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.artistName ?: stringResource(R.string.unknown_artist),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bitDepthText = state.technicalSummary.bitDepth?.let {
                stringResource(R.string.album_detail_bit_depth_format, it)
            } ?: ALBUM_TRACK_NO_NUMBER_PLACEHOLDER
            val bitDepthDesc = if (state.technicalSummary.bitDepth != null) {
                bitDepthText
            } else {
                stringResource(R.string.album_detail_meta_na_desc)
            }
            Text(
                text = bitDepthText,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.semantics { contentDescription = bitDepthDesc },
            )
            val sampleRateText = state.technicalSummary.sampleRateHz?.let {
                stringResource(R.string.album_detail_sample_rate_format, it)
            } ?: ALBUM_TRACK_NO_NUMBER_PLACEHOLDER
            val sampleRateDesc = if (state.technicalSummary.sampleRateHz != null) {
                sampleRateText
            } else {
                stringResource(R.string.album_detail_meta_na_desc)
            }
            Text(
                text = sampleRateText,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.semantics { contentDescription = sampleRateDesc },
            )
        }
    }
}

@Composable
private fun AlbumStatsSection(
    state: AlbumDetailUiState,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensions.contentHorizontalPadding,
                vertical = dimensions.spaceLarge,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Column 1: Song count
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${state.stats.trackCount}",
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.album_detail_stat_songs),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }

        // Column 2: Duration
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatDuration(state.stats.totalDurationMs),
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.album_detail_stat_duration),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }

        // Column 3: Year
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val yearText = state.stats.releaseYear?.toString() ?: ALBUM_TRACK_NO_NUMBER_PLACEHOLDER
            val yearDesc = if (state.stats.releaseYear != null) yearText else stringResource(R.string.album_detail_meta_na_desc)
            Text(
                text = yearText,
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
                modifier = Modifier.semantics { contentDescription = yearDesc },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.album_detail_stat_year),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumTrackItem(
    presentation: AlbumTrackPresentation,
    playlists: List<Playlist>,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHide: () -> Unit,
    onShowTrackInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val track = presentation.track
    val isEnabled = presentation.isPlayable
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.trackListItemHeight)
            .alpha(if (isEnabled) 1f else 0.38f)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = dimensions.contentHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number slot
        val isNoNumber = presentation.trackNumberText == ALBUM_TRACK_NO_NUMBER_PLACEHOLDER ||
            presentation.trackNumberText == "–" ||
            presentation.trackNumberText == "-" ||
            presentation.trackNumberText == "➖"
        val trackNumberDesc = if (isNoNumber) {
            stringResource(R.string.album_detail_track_no_number_desc)
        } else {
            stringResource(R.string.album_detail_track_number_desc, presentation.trackNumberText)
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .semantics { contentDescription = trackNumberDesc },
            contentAlignment = if (isNoNumber) Alignment.Center else Alignment.CenterStart,
        ) {
            Text(
                text = presentation.trackNumberText,
                style = if (isNoNumber) MusicTheme.typography.titleSmall else MusicTheme.typography.titleMedium,
                textAlign = if (isNoNumber) TextAlign.Center else TextAlign.Start,
                color = if (presentation.isCurrentPlaying) {
                    MusicTheme.colors.primary
                } else if (isNoNumber) {
                    MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MusicTheme.colors.onSurfaceVariant
                },
                modifier = Modifier.align(if (isNoNumber) Alignment.Center else Alignment.CenterStart),
            )
        }

        // Title and QualityBadge + Artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimensions.spaceSmall),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MusicTheme.typography.titleMedium,
                color = if (presentation.isCurrentPlaying) {
                    MusicTheme.colors.primary
                } else {
                    MusicTheme.colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                track.resolveQuality()?.let { quality ->
                    QualityBadge(quality = quality)
                }
                Text(
                    text = track.artistName,
                    style = MusicTheme.typography.bodySmall,
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 3-dot actions menu
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
                onDismissRequest = { menuExpanded = false },
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onAddToPlaylist = onAddToPlaylist,
                onHide = onHide,
                onShowTrackInfo = onShowTrackInfo,
                playlists = playlists,
            )
        }
    }
}

@Composable
private fun AlbumArtistsSection(
    artists: List<AlbumArtistCredit>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.album_detail_artists_section_title),
            style = MusicTheme.typography.titleMedium,
            color = MusicTheme.colors.primary,
            modifier = Modifier.padding(bottom = dimensions.spaceMedium),
        )
        artists.forEach { credit ->
            val isClickable = credit.artistMediaStoreId != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(MusicTheme.shapes.small)
                    .then(
                        if (isClickable) {
                            Modifier.clickable { onArtistClick(credit.artistName) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = dimensions.spaceSmallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circular avatar
                Surface(
                    shape = CircleShape,
                    color = MusicTheme.colors.surfaceVariant,
                    modifier = Modifier.size(dimensions.trackArtworkSize),
                ) {
                    AsyncImage(
                        model = AudioArtworkRequest.ArtistArtworkRequest(
                            artistName = credit.artistName,
                            representativeTrackId = credit.representativeTrack.id,
                            dateModifiedMs = credit.representativeTrack.dateModifiedMs,
                        ),
                        contentDescription = credit.artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.width(dimensions.spaceMedium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credit.artistName,
                        style = MusicTheme.typography.titleMedium,
                        color = MusicTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.album_detail_artist_songs_format, credit.trackCount),
                        style = MusicTheme.typography.bodySmall,
                        color = MusicTheme.colors.onSurfaceVariant,
                    )
                }

                if (isClickable) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_chevron_right),
                        contentDescription = null,
                        tint = MusicTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMinutes = minutes % 60
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, remMinutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
