package com.musicapp.player

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.musicapp.player.navigation.AboutRoute
import com.musicapp.player.navigation.AlbumDetailRoute
import com.musicapp.player.navigation.AlbumsRoute
import com.musicapp.player.navigation.ArtistDetailRoute
import com.musicapp.player.navigation.ArtistsRoute
import com.musicapp.player.navigation.BackNavigationResult
import com.musicapp.player.navigation.FolderDetailRoute
import com.musicapp.player.navigation.FoldersRoute
import com.musicapp.player.navigation.HistoryRoute
import com.musicapp.player.navigation.MusicNavKey
import com.musicapp.player.navigation.NavigationState
import com.musicapp.player.navigation.Navigator
import com.musicapp.player.navigation.PlaylistDetailRoute
import com.musicapp.player.navigation.PlaylistsRoute
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.navigation.TrackInfoRoute
import com.musicapp.player.navigation.TracksRoute
import com.musicapp.player.navigation.topLevelNavKeys
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.feature.about.AboutScreenRoute
import com.musicapp.player.feature.about.AboutViewModel
import com.musicapp.player.feature.aero.AeroBackground
import com.musicapp.player.feature.permission.MediaPermissionState
import com.musicapp.player.feature.albums.AlbumDetailScreenRoute
import com.musicapp.player.feature.albums.AlbumDetailViewModel
import com.musicapp.player.feature.albums.AlbumsScreenRoute
import com.musicapp.player.feature.albums.AlbumsViewModel
import com.musicapp.player.feature.artists.ArtistDetailScreenRoute
import com.musicapp.player.feature.artists.ArtistDetailViewModel
import com.musicapp.player.feature.artists.ArtistsScreenRoute
import com.musicapp.player.feature.artists.ArtistsViewModel
import com.musicapp.player.feature.folders.FolderDetailScreenRoute
import com.musicapp.player.feature.folders.FolderDetailViewModel
import com.musicapp.player.feature.folders.FolderId
import com.musicapp.player.feature.folders.FoldersScreenRoute
import com.musicapp.player.feature.folders.FoldersViewModel
import com.musicapp.player.feature.history.HistoryScreenRoute
import com.musicapp.player.feature.history.HistoryViewModel
import com.musicapp.player.feature.lyrics.LyricsViewModel
import com.musicapp.player.feature.player.PlayerSheetRoute
import com.musicapp.player.feature.player.PlayerViewModel
import com.musicapp.player.feature.playlists.PlaylistDetailScreenRoute
import com.musicapp.player.feature.playlists.PlaylistDetailViewModel
import com.musicapp.player.feature.playlists.PlaylistsScreenRoute
import com.musicapp.player.feature.playlists.PlaylistsViewModel
import com.musicapp.player.feature.settings.SettingsScreenRoute
import com.musicapp.player.feature.settings.SettingsViewModel
import com.musicapp.player.feature.tracks.TracksScreenRoute
import com.musicapp.player.feature.tracks.TracksViewModel
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.ui.shell.AppShell
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun MainNavigation(
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    onExit: () -> Unit,
    permissionState: MediaPermissionState,
    onConfirmPermission: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    var encodedSnapshot by rememberSaveable {
        mutableStateOf(NavigationState.initial().snapshot().encode())
    }
    val navigationState = remember(encodedSnapshot) { NavigationState.restoreOrInitial(encodedSnapshot) }
    val canonicalSnapshot = navigationState.snapshot().encode()
    LaunchedEffect(encodedSnapshot, canonicalSnapshot) {
        if (encodedSnapshot != canonicalSnapshot) encodedSnapshot = canonicalSnapshot
    }
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val playerViewModel = viewModel<PlayerViewModel>()
    val lyricsViewModel = viewModel<LyricsViewModel>()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    fun commitNavigation(action: Navigator.() -> Unit) {
        navigator.action()
        encodedSnapshot = navigationState.snapshot().encode()
    }

    fun handleBack() {
        if (navigator.goBack() == BackNavigationResult.REQUEST_EXIT) {
            onExit()
        } else {
            encodedSnapshot = navigationState.snapshot().encode()
        }
    }

    AeroBackground(
        preferredMode =
            if (playerExpanded || navigationState.currentBackStack.size > 1) AeroMode.SOLID else aeroMode,
        signals = aeroSignals,
        modifier = Modifier.fillMaxSize(),
    ) {
      AppShell(
        drawerGesturesEnabled = !playerExpanded,
        playerSheetVisible = playerState.currentTrack != null,
        navigationContent = { policy, closeDrawer ->
            NavigationMenu(
                policy = policy,
                selectedRoute = navigationState.currentTopLevelRoute,
                onSelect = { route ->
                    commitNavigation { navigate(route) }
                    closeDrawer()
                },
            )
        },
        content = { contentInsets, policy, openDrawer ->
            val saveableStackKey = topLevelNavKeys.indexOf(navigationState.currentTopLevelRoute)
            saveableStateHolder.SaveableStateProvider(saveableStackKey) {
                NavDisplay(
                    backStack = navigationState.currentBackStack,
                    onBack = ::handleBack,
                    entryProvider =
                        entryProvider {
                            entry<TracksRoute> {
                                TracksDestination(
                                    permissionState = permissionState,
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                    onConfirmPermission = onConfirmPermission,
                                    onRetryPermission = onRetryPermission,
                                    onOpenPermissionSettings = onOpenPermissionSettings,
                                )
                            }
                            entry<AlbumsRoute> {
                                AlbumsScreenRoute(
                                    viewModel = viewModel<AlbumsViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                    onAlbumClick = { albumId ->
                                        commitNavigation {
                                            navigate(AlbumDetailRoute(albumId.volumeName, albumId.mediaStoreId))
                                        }
                                    },
                                )
                            }
                            entry<ArtistsRoute> {
                                ArtistsScreenRoute(
                                    viewModel = viewModel<ArtistsViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                    onArtistClick = { artistId ->
                                        commitNavigation { navigate(ArtistDetailRoute(artistId.mediaStoreId)) }
                                    },
                                )
                            }
                            entry<PlaylistsRoute> {
                                PlaylistsScreenRoute(
                                    viewModel = viewModel<PlaylistsViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                    onPlaylistClick = { playlistId ->
                                        commitNavigation { navigate(PlaylistDetailRoute(playlistId.value)) }
                                    },
                                )
                            }
                            entry<HistoryRoute> {
                                HistoryScreenRoute(
                                    viewModel = viewModel<HistoryViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                )
                            }
                            entry<FoldersRoute> {
                                FoldersScreenRoute(
                                    viewModel = viewModel<FoldersViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                    onFolderClick = { folderId ->
                                        commitNavigation {
                                            navigate(FolderDetailRoute(folderId.volumeName, folderId.relativePath))
                                        }
                                    },
                                )
                            }
                            entry<SettingsRoute> {
                                SettingsScreenRoute(
                                    viewModel = viewModel<SettingsViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                )
                            }
                            entry<AboutRoute> {
                                AboutScreenRoute(
                                    viewModel = viewModel<AboutViewModel>(),
                                    contentInsets = contentInsets,
                                    policy = policy,
                                    openDrawer = openDrawer,
                                )
                            }
                            entry<TrackInfoRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                            entry<AlbumDetailRoute> { key ->
                                AlbumDetailScreenRoute(
                                    albumId = AlbumId(key.volumeName, key.mediaStoreId),
                                    viewModel = viewModel<AlbumDetailViewModel>(
                                        key = "album:${key.volumeName}:${key.mediaStoreId}",
                                    ),
                                    contentInsets = contentInsets,
                                    onBack = ::handleBack,
                                )
                            }
                            entry<ArtistDetailRoute> { key ->
                                ArtistDetailScreenRoute(
                                    artistId = ArtistId(key.mediaStoreId),
                                    viewModel = viewModel<ArtistDetailViewModel>(
                                        key = "artist:${key.mediaStoreId}",
                                    ),
                                    contentInsets = contentInsets,
                                    onBack = ::handleBack,
                                )
                            }
                            entry<PlaylistDetailRoute> { key ->
                                PlaylistDetailScreenRoute(
                                    playlistId = com.musicapp.player.core.domain.model.PlaylistId(key.playlistId),
                                    viewModel = viewModel<PlaylistDetailViewModel>(key = "playlist:${key.playlistId}"),
                                    contentInsets = contentInsets,
                                    onBack = ::handleBack,
                                )
                            }
                            entry<FolderDetailRoute> { key ->
                                FolderDetailScreenRoute(
                                    folderId = FolderId(key.volumeName, key.relativePath),
                                    viewModel = viewModel<FolderDetailViewModel>(
                                        key = "folder:${key.volumeName}:${key.relativePath}",
                                    ),
                                    contentInsets = contentInsets,
                                    onBack = ::handleBack,
                                    onFolderClick = { folderId ->
                                        commitNavigation {
                                            navigate(FolderDetailRoute(folderId.volumeName, folderId.relativePath))
                                        }
                                    },
                                )
                            }
                        },
                )
            }
            BackHandler(
                enabled = navigationState.currentBackStack.size == 1,
                onBack = ::handleBack,
            )
        },
        playerSheetContent = { contentInsets ->
            PlayerSheetRoute(
                viewModel = playerViewModel,
                lyricsViewModel = lyricsViewModel,
                aeroMode = aeroMode,
                aeroSignals = aeroSignals,
                contentInsets = contentInsets,
                onExpansionChanged = { playerExpanded = it },
            )
        },
      )
    }
}

@Composable
private fun TracksDestination(
    permissionState: MediaPermissionState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onConfirmPermission: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    when (permissionState) {
        is MediaPermissionState.Granted ->
            TracksScreenRoute(
                viewModel = viewModel<TracksViewModel>(),
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
        is MediaPermissionState.PurposeExplanation ->
            PermissionPrompt(
                descriptionResId = R.string.permission_audio_explanation,
                actionLabelResId = R.string.permission_continue,
                onAction = onConfirmPermission,
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
        is MediaPermissionState.DeniedCanRetry ->
            PermissionPrompt(
                descriptionResId = R.string.permission_denied,
                actionLabelResId = R.string.permission_retry,
                onAction = onRetryPermission,
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
        is MediaPermissionState.PermanentlyDenied ->
            PermissionPrompt(
                descriptionResId = R.string.permission_permanently_denied,
                actionLabelResId = R.string.permission_open_settings,
                onAction = onOpenPermissionSettings,
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
        is MediaPermissionState.Requesting ->
            PermissionProgress(
                messageResId = R.string.permission_requesting,
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
        is MediaPermissionState.WaitingForSettingsReturn ->
            PermissionProgress(
                messageResId = R.string.permission_waiting_for_settings,
                contentInsets = contentInsets,
                policy = policy,
                openDrawer = openDrawer,
            )
    }
}

@Composable
private fun PermissionPrompt(
    @StringRes descriptionResId: Int,
    @StringRes actionLabelResId: Int,
    onAction: () -> Unit,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = permissionContentModifier(contentInsets),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        CompactNavigationButton(policy, openDrawer)
        Text(
            text = stringResource(R.string.permission_audio_title),
            color = MusicTheme.colors.onSurface,
            style = MusicTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(descriptionResId),
            color = MusicTheme.colors.onSurfaceVariant,
            style = MusicTheme.typography.bodyLarge,
        )
        Button(
            onClick = onAction,
            modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
            shape = MusicTheme.shapes.small,
        ) {
            Text(
                text = stringResource(actionLabelResId),
                style = MusicTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PermissionProgress(
    @StringRes messageResId: Int,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = permissionContentModifier(contentInsets),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        CompactNavigationButton(policy, openDrawer)
        CircularProgressIndicator(color = MusicTheme.colors.primary)
        Text(
            text = stringResource(messageResId),
            color = MusicTheme.colors.onSurfaceVariant,
            style = MusicTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun permissionContentModifier(contentInsets: WindowInsets): Modifier {
    val dimensions = MusicTheme.dimensions
    return Modifier.fillMaxSize()
        .windowInsetsPadding(contentInsets)
        .padding(
            horizontal = dimensions.contentHorizontalPadding,
            vertical = dimensions.spaceMedium,
        )
}

@Composable
private fun NavigationMenu(
    policy: WindowLayoutPolicy,
    selectedRoute: TopLevelNavKey,
    onSelect: (TopLevelNavKey) -> Unit,
) {
    if (policy == WindowLayoutPolicy.MEDIUM_RAIL) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
            topLevelNavKeys.forEach { route ->
                val label = stringResource(route.titleResId())
                NavigationRailItem(
                    selected = route == selectedRoute,
                    onClick = { onSelect(route) },
                    icon = { Text(text = label.take(1), style = MusicTheme.typography.labelLarge) },
                    label = { Text(text = label, style = MusicTheme.typography.labelSmall) },
                )
            }
        }
    } else {
        val dimensions = MusicTheme.dimensions
        val navigationModifier =
            if (policy == WindowLayoutPolicy.EXPANDED_SIDEBAR) {
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            } else {
                Modifier.fillMaxSize()
            }
        Column(
            modifier =
                navigationModifier.padding(
                    horizontal = dimensions.spaceSmall,
                    vertical = dimensions.spaceMedium,
                ),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            topLevelNavKeys.forEach { route ->
                NavigationDrawerItem(
                    label = { Text(text = stringResource(route.titleResId())) },
                    selected = route == selectedRoute,
                    onClick = { onSelect(route) },
                )
            }
        }
    }
}

@Composable
private fun DestinationPlaceholder(
    route: MusicNavKey,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets)
                .padding(
                    horizontal = dimensions.contentHorizontalPadding,
                    vertical = dimensions.spaceMedium,
                ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        CompactNavigationButton(policy, openDrawer)
        Text(
            text = stringResource(route.titleResId()),
            color = MusicTheme.colors.onSurface,
            style = MusicTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun CompactNavigationButton(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
) {
    if (policy != WindowLayoutPolicy.COMPACT_DRAWER) return
    TextButton(
        onClick = openDrawer,
        modifier = Modifier.heightIn(min = MusicTheme.dimensions.minimumTouchTarget),
        shape = MusicTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.open_navigation),
            style = MusicTheme.typography.labelLarge,
        )
    }
}

@StringRes
private fun MusicNavKey.titleResId(): Int =
    when (this) {
        TracksRoute, is TrackInfoRoute -> R.string.navigation_tracks
        AlbumsRoute, is AlbumDetailRoute -> R.string.navigation_albums
        ArtistsRoute, is ArtistDetailRoute -> R.string.navigation_artists
        PlaylistsRoute, is PlaylistDetailRoute -> R.string.navigation_playlists
        HistoryRoute -> R.string.navigation_history
        FoldersRoute, is FolderDetailRoute -> R.string.navigation_folders
        SettingsRoute -> R.string.navigation_settings
        AboutRoute -> R.string.navigation_about
    }
