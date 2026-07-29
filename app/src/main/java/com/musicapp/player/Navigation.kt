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
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.AppShell
import com.musicapp.player.ui.shell.PlayerSheetPlaceholder
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun MainNavigation(onExit: () -> Unit) {
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

    AppShell(
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
                            entry<TracksRoute> { DestinationPlaceholder(TracksRoute, contentInsets, policy, openDrawer) }
                            entry<AlbumsRoute> { DestinationPlaceholder(AlbumsRoute, contentInsets, policy, openDrawer) }
                            entry<ArtistsRoute> { DestinationPlaceholder(ArtistsRoute, contentInsets, policy, openDrawer) }
                            entry<PlaylistsRoute> { DestinationPlaceholder(PlaylistsRoute, contentInsets, policy, openDrawer) }
                            entry<HistoryRoute> { DestinationPlaceholder(HistoryRoute, contentInsets, policy, openDrawer) }
                            entry<FoldersRoute> { DestinationPlaceholder(FoldersRoute, contentInsets, policy, openDrawer) }
                            entry<SettingsRoute> { DestinationPlaceholder(SettingsRoute, contentInsets, policy, openDrawer) }
                            entry<AboutRoute> { DestinationPlaceholder(AboutRoute, contentInsets, policy, openDrawer) }
                            entry<TrackInfoRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                            entry<AlbumDetailRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                            entry<ArtistDetailRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                            entry<PlaylistDetailRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                            entry<FolderDetailRoute> { key -> DestinationPlaceholder(key, contentInsets, policy, openDrawer) }
                        },
                )
            }
            BackHandler(
                enabled = navigationState.currentBackStack.size == 1,
                onBack = ::handleBack,
            )
        },
        playerSheetContent = { PlayerSheetPlaceholder() },
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
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            TextButton(
                onClick = openDrawer,
                modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
                shape = MusicTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.open_navigation),
                    style = MusicTheme.typography.labelLarge,
                )
            }
        }
        Text(
            text = stringResource(route.titleResId()),
            color = MusicTheme.colors.onSurface,
            style = MusicTheme.typography.headlineMedium,
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
