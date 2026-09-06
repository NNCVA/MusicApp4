package com.musicapp.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
import com.musicapp.player.navigation.ScanMusicRoute
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.navigation.TrackInfoRoute
import com.musicapp.player.navigation.TracksRoute
import com.musicapp.player.navigation.topLevelNavKeys
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.designsystem.snackbar.MessageBubbleHost
import com.musicapp.player.core.designsystem.snackbar.MessageBubbleQueue
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import com.musicapp.player.feature.about.AboutScreenRoute
import com.musicapp.player.feature.about.AboutViewModel
import com.musicapp.player.feature.aero.AeroBackground
import com.musicapp.player.feature.permission.MediaPermissionState
import com.musicapp.player.feature.albums.AlbumDetailScreenRoute
import com.musicapp.player.feature.albums.AlbumDetailViewModel
import com.musicapp.player.feature.albums.AlbumGroupKey
import com.musicapp.player.feature.albums.AlbumsScreenRoute
import com.musicapp.player.feature.albums.AlbumsViewModel
import com.musicapp.player.feature.artists.ArtistDetailScreenRoute
import com.musicapp.player.feature.artists.ArtistDetailViewModel
import com.musicapp.player.feature.artists.ArtistRouteKey
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
import com.musicapp.player.feature.scan.ScanMusicScreenRoute
import com.musicapp.player.feature.scan.ScanViewModel
import com.musicapp.player.feature.tracks.TracksScreenRoute
import com.musicapp.player.feature.tracks.TracksViewModel
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.ui.shell.AppShell
import com.musicapp.player.ui.shell.LibrarySyncFeedbackDialog
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import com.musicapp.player.ui.sidebar.SidebarNavigation
import com.musicapp.player.ui.sidebar.nextSidebarMode
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    tracksViewModel: TracksViewModel = viewModel(),
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    themeMode: ThemeMode,
    pendingFeedback: PendingLibrarySyncFeedback? = null,
    onFullExit: () -> Unit,
    onReturnToDesktop: () -> Unit,
    onThemeModeChange: suspend (ThemeMode) -> Boolean,
    onScanMusic: () -> Unit,
    onAcknowledgeSyncFeedback: (Long) -> Unit,
    permissionState: MediaPermissionState,
    onConfirmPermission: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
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
    val playerViewModel = viewModel<PlayerViewModel>()
    val lyricsViewModel = viewModel<LyricsViewModel>()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var pageTransitionDirection by remember { mutableStateOf(PageTransitionDirection.FORWARD) }
    val messageBubbleQueue = remember { MessageBubbleQueue() }
    val messageBubbleRequest by messageBubbleQueue.current.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val nextThemeMode = themeMode.nextSidebarMode()
    val nextThemeMessageRes =
        when (nextThemeMode) {
            ThemeMode.SYSTEM -> R.string.sidebar_theme_changed_system
            ThemeMode.LIGHT -> R.string.sidebar_theme_changed_light
            ThemeMode.DARK -> R.string.sidebar_theme_changed_dark
        }
    val messageBubbleText =
        messageBubbleRequest?.let { request ->
            stringResource(request.messageResId, *request.messageFormatArgs.toTypedArray())
        }

    var lastNavCommitTimeMs by remember { mutableLongStateOf(0L) }

    fun commitNavigation(action: Navigator.() -> Unit) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastNavCommitTimeMs < NAVIGATION_THROTTLE_WINDOW_MS) return
        lastNavCommitTimeMs = nowMs
        pageTransitionDirection = PageTransitionDirection.FORWARD
        navigator.action()
        encodedSnapshot = navigationState.snapshot().encode()
    }

    fun handleBack() {
        if (navigator.goBack() == BackNavigationResult.REQUEST_RETURN_TO_DESKTOP) {
            onReturnToDesktop()
        } else {
            pageTransitionDirection = PageTransitionDirection.BACKWARD
            encodedSnapshot = navigationState.snapshot().encode()
        }
    }

    fun navigateToScanMusic() {
        commitNavigation {
            navigate(ScanMusicRoute)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AeroBackground(
            preferredMode = if (playerExpanded) AeroMode.SOLID else aeroMode,
            signals = aeroSignals,
            modifier = Modifier.fillMaxSize(),
        ) {
          AppShell(
            drawerGesturesEnabled = !playerExpanded,
            playerSheetVisible = playerState.currentTrack != null,
            navigationContent = { policy, closeDrawer ->
                SidebarNavigation(
                    policy = policy,
                    selectedRoute = navigationState.currentTopLevelRoute,
                    themeMode = themeMode,
                    playerSheetVisible = playerState.currentTrack != null,
                    onSelect = { route ->
                        commitNavigation { navigate(route) }
                        closeDrawer()
                    },
                    onRequestExit = {
                        showExitDialog = true
                        //closeDrawer()
                    },
                    onCycleTheme = {
                        coroutineScope.launch {
                            messageBubbleQueue.enqueue(
                                if (onThemeModeChange(nextThemeMode)) {
                                    nextThemeMessageRes
                                } else {
                                    R.string.settings_action_failed
                                },
                            )
                        }
                    },
                    onEqualizer = {
                        messageBubbleQueue.enqueue(R.string.sidebar_equalizer_placeholder)
                    },
                )
            },
            content = { contentInsets, policy, openDrawer ->
                val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
                val bottomPadding = bottomInset + if (playerState.currentTrack != null) MusicTheme.dimensions.miniPlayerHeight else 0.dp
                val currentTopRoute = navigationState.currentBackStack.lastOrNull()
                val navigateToArtist: (String) -> Unit = { artistName ->
                    commitNavigation { navigate(ArtistDetailRoute(ArtistRouteKey.encode(artistName))) }
                }
                val navigateToAlbum: (AlbumId) -> Unit = { albumId ->
                    commitNavigation {
                        navigate(
                            AlbumDetailRoute(
                                volumeName = albumId.volumeName,
                                mediaStoreId = albumId.mediaStoreId,
                            ),
                        )
                    }
                }
                val destinationEntryProvider =
                    entryProvider<MusicNavKey> {
                        entry<TracksRoute> {
                            TracksScreenRoute(
                                viewModel = tracksViewModel,
                                contentInsets = contentInsets,
                                policy = policy,
                                openDrawer = openDrawer,
                                onScanMusic = ::navigateToScanMusic,
                                onArtistClick = navigateToArtist,
                                onAlbumClick = navigateToAlbum,
                                onShowMessage = { messageResId, formatArgs ->
                                    messageBubbleQueue.enqueue(messageResId, formatArgs)
                                },
                                bottomPadding = bottomPadding,
                                isActive = currentTopRoute == TracksRoute,
                            )
                        }
                        entry<ScanMusicRoute> {
                            ScanMusicScreenRoute(
                                viewModel = viewModel<ScanViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                onBack = ::handleBack,
                                permissionState = permissionState,
                                onConfirmPermission = onConfirmPermission,
                                onRetryPermission = onRetryPermission,
                                onOpenPermissionSettings = onOpenPermissionSettings,
                                onOpenApplicationSettings = onOpenApplicationSettings,
                                onScanMusic = onScanMusic,
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<AlbumsRoute> {
                            AlbumsScreenRoute(
                                viewModel = viewModel<AlbumsViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                openDrawer = openDrawer,
                                onScanMusic = ::navigateToScanMusic,
                                onAlbumClick = { album ->
                                    commitNavigation {
                                        navigate(
                                            AlbumDetailRoute(
                                                volumeName = album.id.volumeName,
                                                mediaStoreId = album.id.mediaStoreId,
                                                groupKey = album.groupKey.encode(),
                                            ),
                                        )
                                    }
                                },
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<ArtistsRoute> {
                            ArtistsScreenRoute(
                                viewModel = viewModel<ArtistsViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                openDrawer = openDrawer,
                                onScanMusic = ::navigateToScanMusic,
                                onArtistClick = { artistId ->
                                    commitNavigation { navigate(ArtistDetailRoute(ArtistRouteKey.encode(artistId.name))) }
                                },
                                bottomPadding = bottomPadding,
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
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<HistoryRoute> {
                            HistoryScreenRoute(
                                viewModel = viewModel<HistoryViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                onBack = ::handleBack,
                                onArtistClick = navigateToArtist,
                                onAlbumClick = navigateToAlbum,
                                onShowMessage = { messageResId, formatArgs ->
                                    messageBubbleQueue.enqueue(messageResId, formatArgs)
                                },
                                bottomPadding = bottomPadding,
                                isActive = currentTopRoute == HistoryRoute,
                            )
                        }
                        entry<FoldersRoute> {
                            FoldersScreenRoute(
                                viewModel = viewModel<FoldersViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                openDrawer = openDrawer,
                                onScanMusic = ::navigateToScanMusic,
                                onFolderClick = { folderId ->
                                    commitNavigation {
                                        navigate(FolderDetailRoute(folderId.volumeName, folderId.relativePath))
                                    }
                                },
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<SettingsRoute> {
                            SettingsScreenRoute(
                                viewModel = viewModel<SettingsViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                onBack = ::handleBack,
                                onShowMessage = { messageResId ->
                                    messageBubbleQueue.enqueue(messageResId)
                                },
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<AboutRoute> {
                            AboutScreenRoute(
                                viewModel = viewModel<AboutViewModel>(),
                                contentInsets = contentInsets,
                                policy = policy,
                                onBack = ::handleBack,
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<TrackInfoRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                        entry<AlbumDetailRoute> { key ->
                            val groupKey = key.groupKey?.let(AlbumGroupKey::decode)
                            AlbumDetailScreenRoute(
                                albumId = AlbumId(key.volumeName, key.mediaStoreId),
                                groupKey = groupKey,
                                viewModel = viewModel<AlbumDetailViewModel>(
                                    key = "album:${key.groupKey ?: "${key.volumeName}:${key.mediaStoreId}"}",
                                ),
                                contentInsets = contentInsets,
                                onBack = ::handleBack,
                                onArtistClick = navigateToArtist,
                                onAlbumClick = navigateToAlbum,
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<ArtistDetailRoute> { key ->
                            val artistName = ArtistRouteKey.decode(key.artistName) ?: key.artistName
                            ArtistDetailScreenRoute(
                                artistId = ArtistId(artistName),
                                viewModel = viewModel<ArtistDetailViewModel>(
                                    key = "artist:${key.artistName}",
                                ),
                                contentInsets = contentInsets,
                                onBack = ::handleBack,
                                onScanMusic = ::navigateToScanMusic,
                                onAlbumClick = { album ->
                                    commitNavigation {
                                        navigate(
                                            AlbumDetailRoute(
                                                volumeName = album.albumId.volumeName,
                                                mediaStoreId = album.albumId.mediaStoreId,
                                                groupKey = album.groupKey.encode(),
                                            ),
                                        )
                                    }
                                },
                                onArtistClick = navigateToArtist,
                                onAlbumIdClick = navigateToAlbum,
                                bottomPadding = bottomPadding,
                            )
                        }
                        entry<PlaylistDetailRoute> { key ->
                            PlaylistDetailScreenRoute(
                                playlistId = com.musicapp.player.core.domain.model.PlaylistId(key.playlistId),
                                viewModel = viewModel<PlaylistDetailViewModel>(key = "playlist:${key.playlistId}"),
                                contentInsets = contentInsets,
                                onBack = ::handleBack,
                                onArtistClick = navigateToArtist,
                                onAlbumClick = navigateToAlbum,
                                bottomPadding = bottomPadding,
                                isActive = currentTopRoute == key,
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
                                onArtistClick = navigateToArtist,
                                onAlbumClick = navigateToAlbum,
                                onShowMessage = { messageResId, formatArgs ->
                                    messageBubbleQueue.enqueue(messageResId, formatArgs)
                                },
                                bottomPadding = bottomPadding,
                                isActive = currentTopRoute == key,
                            )
                        }
                    }
                val decoratedBackStacks =
                    topLevelNavKeys.associateWith { route ->
                        // Each top-level stack owns its destination state while it is not visible.
                        rememberDecoratedNavEntries(
                            backStack = navigationState.backStack(route),
                            entryDecorators =
                                listOf(rememberSaveableStateHolderNavEntryDecorator<MusicNavKey>()),
                            entryProvider = destinationEntryProvider,
                        )
                    }
                val displayedEntries =
                    buildList {
                        // The current home stack stays underneath utility/detail destinations.
                        addAll(decoratedBackStacks.getValue(navigationState.homeTopLevelRoute))
                        if (navigationState.currentTopLevelRoute != navigationState.homeTopLevelRoute) {
                            addAll(decoratedBackStacks.getValue(navigationState.currentTopLevelRoute))
                        }
                    }
                NavDisplay(
                    entries = displayedEntries,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    onBack = ::handleBack,
                    // A sidebar selection can shrink entries while still being a forward action.
                    transitionSpec = { pageTransition(pageTransitionDirection) },
                    popTransitionSpec = { pageTransition(pageTransitionDirection) },
                    predictivePopTransitionSpec = { _ ->
                        pageTransition(PageTransitionDirection.BACKWARD)
                    },
                )
                BackHandler(
                    enabled =
                        navigationState.currentTopLevelRoute == navigationState.homeTopLevelRoute &&
                            navigationState.currentBackStack.size == 1,
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
        val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        MessageBubbleHost(
            request = messageBubbleRequest,
            message = messageBubbleText,
            onDismiss = { requestId -> messageBubbleQueue.dismiss(requestId) },
            onAction = { requestId -> messageBubbleQueue.performAction(requestId) },
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(
                        bottom =
                            (if (playerState.currentTrack != null) {
                                MusicTheme.dimensions.miniPlayerHeight
                            } else {
                                MusicTheme.dimensions.spaceMedium
                            }) + bottomInset + MusicTheme.dimensions.messageBubbleBottomLift,
                    ),
        )
    }
    pendingFeedback?.let { feedback ->
        LibrarySyncFeedbackDialog(
            feedback = feedback,
            onAcknowledge = onAcknowledgeSyncFeedback,
        )
    }
    if (showExitDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.sidebar_exit_dialog_title),
            text = stringResource(R.string.sidebar_exit_dialog_description),
            confirmLabel = stringResource(R.string.sidebar_exit_fully),
            cancelLabel = stringResource(R.string.sidebar_cancel),
            isDestructive = true,
            onConfirm = {
                showExitDialog = false
                onFullExit()
            },
            onDismiss = {
                showExitDialog = false
            },
        )
    }
}

private const val NAVIGATION_THROTTLE_WINDOW_MS: Long = 300L

private enum class PageTransitionDirection {
    FORWARD,
    BACKWARD,
}

private fun pageTransition(direction: PageTransitionDirection): ContentTransform =
    when (direction) {
        PageTransitionDirection.FORWARD ->
            slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) togetherWith
                slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
        PageTransitionDirection.BACKWARD ->
            slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }) togetherWith
                slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
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
            style = MusicTheme.typography.titleLarge,
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
        is ScanMusicRoute -> R.string.navigation_scan_music
    }
