package com.musicapp.player.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateTest {
    @Test
    fun routeTableContainsEightStableTopLevelStacks() {
        assertEquals(
            listOf(
                TracksRoute,
                AlbumsRoute,
                ArtistsRoute,
                PlaylistsRoute,
                HistoryRoute,
                FoldersRoute,
                SettingsRoute,
                AboutRoute,
            ),
            topLevelNavKeys,
        )
        assertEquals(topLevelNavKeys, NavigationState.initial().snapshot().stacks.map { it.root })
    }

    @Test
    fun switchingTopLevelRoutesRetainsEachStackHistory() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 11)
        val playlistDetail = PlaylistDetailRoute(playlistId = 7)

        navigator.navigate(albumDetail)
        navigator.navigate(playlistDetail)
        navigator.navigate(AlbumsRoute)

        assertEquals(listOf(AlbumsRoute, albumDetail), state.currentBackStack)
        assertEquals(listOf(PlaylistsRoute, playlistDetail), state.backStack(PlaylistsRoute))
    }

    @Test
    fun selectingCurrentTopLevelRouteAgainReturnsItsStackToRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)

        navigator.navigate(ArtistDetailRoute(mediaStoreId = 9))
        navigator.navigate(ArtistsRoute)

        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute), state.currentBackStack)
    }

    @Test
    fun backFromNonTracksRootSelectsTracksAndPreservesOtherStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(SettingsRoute)

        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(TracksRoute, state.currentTopLevelRoute)
        assertEquals(listOf(SettingsRoute), state.backStack(SettingsRoute))
    }

    @Test
    fun backPopsDetailBeforeReturningToTracksRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(AlbumDetailRoute(volumeName = "external", mediaStoreId = 12))

        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(listOf(AlbumsRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(TracksRoute, state.currentTopLevelRoute)
    }

    @Test
    fun backFromTracksRootRequestsExit() {
        val state = NavigationState.initial()

        assertEquals(BackNavigationResult.REQUEST_EXIT, Navigator(state).goBack())
        assertEquals(listOf(TracksRoute), state.currentBackStack)
    }

    @Test
    fun detailNavigationUsesItsOwningStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val folderDetail = FolderDetailRoute(relativePath = "Music/Live/")

        navigator.navigate(AboutRoute)
        navigator.navigate(folderDetail)

        assertEquals(FoldersRoute, state.currentTopLevelRoute)
        assertEquals(listOf(FoldersRoute, folderDetail), state.backStack(FoldersRoute))
        assertEquals(listOf(AboutRoute), state.backStack(AboutRoute))
    }

    @Test
    fun serializedSnapshotRestoresCurrentRouteAndEveryStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(TrackInfoRoute(volumeName = "external", mediaStoreId = 2))
        navigator.navigate(AlbumDetailRoute(volumeName = "external", mediaStoreId = 3))
        navigator.navigate(PlaylistDetailRoute(playlistId = 4))
        navigator.navigate(AlbumsRoute)

        val restored = NavigationState.restore(NavigationSnapshot.decode(state.snapshot().encode()))

        assertEquals(AlbumsRoute, restored.currentTopLevelRoute)
        assertEquals(state.snapshot(), restored.snapshot())
    }

    @Test
    fun invalidProcessSnapshotFallsBackToInitialNavigationState() {
        val restored = NavigationState.restoreOrInitial("not-a-valid-navigation-snapshot")

        assertEquals(TracksRoute, restored.currentTopLevelRoute)
        assertEquals(NavigationState.initial().snapshot(), restored.snapshot())
    }
}
