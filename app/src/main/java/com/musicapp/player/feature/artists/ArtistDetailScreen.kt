package com.musicapp.player.feature.artists

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AddToPlaylistDialog
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.ListActionBar
import com.musicapp.player.core.designsystem.component.LoadingState
import com.musicapp.player.core.designsystem.component.SearchableTopBar
import com.musicapp.player.core.designsystem.component.TextInputDialog
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.TrackRow
import com.musicapp.player.core.designsystem.component.localizedArtistName
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.image.AudioArtworkRequest
import com.musicapp.player.feature.albums.localizedAlbumTitle
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.theme.MusicTheme

@Composable
fun ArtistDetailScreenRoute(
    artistId: ArtistId,
    viewModel: ArtistDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onScanMusic: () -> Unit = {},
    onAlbumClick: (ArtistAlbumSummary) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumIdClick: (AlbumId) -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.infoTrack != null) { viewModel.dismissTrackInfo() }
    LaunchedEffect(artistId) { viewModel.open(artistId) }
    ArtistDetailScreen(
        state = state,
        contentInsets = contentInsets,
        bottomPadding = bottomPadding,
        onBack = onBack,
        onScanMusic = onScanMusic,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onAlbumIdClick = onAlbumIdClick,
        onPlayAll = viewModel::playAll,
        onTrackClick = { viewModel.playTrack(it.id) },
        onAddToQueue = viewModel::addToQueue,
        onPlayNext = viewModel::playNext,
        onHide = viewModel::hideTrack,
        onAddToPlaylist = { trackId, playlistId -> viewModel.addToPlaylist(trackId, playlistId) },
        onShowTrackInfo = viewModel::showTrackInfo,
        onDismissTrackInfo = viewModel::dismissTrackInfo,
        onCreatePlaylist = viewModel::createPlaylist,
    )
}

@Composable
internal fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onScanMusic: () -> Unit,
    onAlbumClick: (ArtistAlbumSummary) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumIdClick: (AlbumId) -> Unit = {},
    onPlayAll: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddToQueue: (TrackId) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onHide: (TrackId) -> Unit,
    onAddToPlaylist: (TrackId, PlaylistId) -> Unit,
    onShowTrackInfo: (Track) -> Unit,
    onDismissTrackInfo: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    val heroCollapseOffsetPx = with(LocalDensity.current) {
        dimensions.artistHeroArtworkSize.toPx()
    }
    val showTopBarTitle by remember(heroCollapseOffsetPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > heroCollapseOffsetPx
        }
    }
    val title = (state.displayName ?: stringResource(R.string.artist_unknown_name)).localizedArtistName()
    var addToPlaylistTrackId by remember { mutableStateOf<TrackId?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            SearchableTopBar(
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = onBack,
                titleContent = {
                    AnimatedVisibility(
                        visible = showTopBarTitle,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = title,
                            style = MusicTheme.typography.titleLarge,
                            color = MusicTheme.colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )

            if (!state.isLoaded) {
                LoadingState(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxWidth().weight(1f).bounceOverscroll(overscrollEffect),
                    contentPadding = PaddingValues(
                        bottom = dimensions.spaceSmall + bottomPadding,
                    ),
                ) {
                    item(key = "artist-hero") {
                        ArtistHeroSection(state = state, title = title)
                    }
                    item(key = "artist-actions") {
                        ListActionBar(
                            isSelectionMode = false,
                            itemCount = state.tracks.size,
                            hasPlayableItems = state.tracks.any { it.availability == Availability.AVAILABLE },
                            itemCountDescription = pluralStringResource(
                                R.plurals.category_track_count,
                                state.tracks.size,
                                state.tracks.size,
                            ),
                            onPlayAll = onPlayAll,
                        )
                    }
                    if (state.tracks.isEmpty()) {
                        item(key = "artist-empty") {
                            EmptyState(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = dimensions.contentHorizontalPadding)
                                    .padding(bottom = dimensions.spaceLarge),
                                title = stringResource(R.string.artist_empty_title),
                                description = stringResource(R.string.artist_empty_description),
                                actionLabel = stringResource(R.string.navigation_scan_music),
                                actionIconRes = R.drawable.ic_sidebar_scan,
                                onAction = onScanMusic,
                            )
                        }
                    } else {
                        items(
                            items = state.tracks,
                            key = { "track:${it.id.volumeName}:${it.id.mediaStoreId}" },
                        ) { track ->
                            TrackRow(
                                track = track,
                                playlists = state.playlists,
                                onAddToQueue = { onAddToQueue(track.id) },
                                onPlayNext = { onPlayNext(track.id) },
                                onHide = { onHide(track.id) },
                                onAddToPlaylist = { addToPlaylistTrackId = track.id },
                                onNavigateToArtist = onArtistClick,
                                onNavigateToAlbum = onAlbumIdClick,
                                onShowTrackInfo = { onShowTrackInfo(track) },
                                onClick = { onTrackClick(track) },
                            )
                        }
                        if (state.albums.isNotEmpty()) {
                            item(key = "artist-albums-title") {
                                Text(
                                    text = stringResource(R.string.artist_albums_section_title),
                                    style = MusicTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MusicTheme.colors.onSurface,
                                    modifier = Modifier.padding(
                                        start = dimensions.contentHorizontalPadding,
                                        end = dimensions.contentHorizontalPadding,
                                        top = dimensions.spaceLarge,
                                        bottom = dimensions.spaceSmall,
                                    ),
                                )
                            }
                            items(
                                items = state.albums,
                                key = { "album:${it.groupKey.encode()}" },
                            ) { album ->
                                ArtistAlbumRow(album = album, onClick = { onAlbumClick(album) })
                            }
                        }
                    }
                }
            }
        }

        state.infoTrack?.let { track ->
            TrackInfoViewer(
                track = track,
                metadata = state.infoMetadata,
                loading = state.isInfoLoading,
                onDismiss = onDismissTrackInfo,
            )
        }
        addToPlaylistTrackId?.let { trackId ->
            AddToPlaylistDialog(
                playlists = state.playlists,
                onSelectPlaylist = { playlistId ->
                    onAddToPlaylist(trackId, playlistId)
                    addToPlaylistTrackId = null
                },
                onCreatePlaylist = {
                    addToPlaylistTrackId = null
                    showCreatePlaylistDialog = true
                },
                onDismiss = { addToPlaylistTrackId = null },
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
                    addToPlaylistTrackId = null
                },
            )
        }
    }
}

@Composable
internal fun ArtistHeroSection(state: ArtistDetailUiState, title: String) {
    val dimensions = MusicTheme.dimensions
    val representative = state.representativeTrack
    val request = remember(state.artistId, representative?.id, representative?.dateModifiedMs) {
        AudioArtworkRequest.ArtistArtworkRequest(
            artistName = state.artistId?.name ?: title,
            representativeTrackId = representative?.id,
            dateModifiedMs = representative?.dateModifiedMs ?: 0L,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = dimensions.contentHorizontalPadding)
            .padding(top = dimensions.spaceLarge, bottom = dimensions.spaceMedium)
            .semantics(mergeDescendants = true) {
                contentDescription = title
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.artist_artwork_description, title),
            modifier = Modifier.size(dimensions.artistHeroArtworkSize)
                .clip(CircleShape)
                .background(MusicTheme.colors.surfaceVariant),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_playlist_album),
            placeholder = painterResource(R.drawable.ic_playlist_album),
        )
        Text(
            text = title,
            style = MusicTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MusicTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ArtistAlbumRow(album: ArtistAlbumSummary, onClick: () -> Unit) {
    val dimensions = MusicTheme.dimensions
    val title = album.title.localizedAlbumTitle()
    val request = remember(album.groupKey, album.representativeTrack.id, album.representativeTrack.dateModifiedMs) {
        AudioArtworkRequest.AlbumArtworkRequest(
            albumId = album.albumId,
            representativeTrackId = album.representativeTrack.id,
            dateModifiedMs = album.representativeTrack.dateModifiedMs,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.albumRowMinHeight)
            .clip(MusicTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = dimensions.contentHorizontalPadding, vertical = dimensions.spaceSmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.album_artwork_description, title),
            modifier = Modifier.size(dimensions.albumRowArtworkSize)
                .clip(MusicTheme.shapes.small)
                .background(MusicTheme.colors.surfaceVariant),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_playlist_album),
            placeholder = painterResource(R.drawable.ic_playlist_album),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artistName.localizedArtistName(),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.artist_album_track_count,
                    album.artistTrackCount,
                    album.artistTrackCount,
                ),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
