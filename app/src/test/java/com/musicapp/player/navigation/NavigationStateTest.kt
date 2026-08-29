package com.musicapp.player.navigation

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        assertEquals(
            listOf(TracksRoute, AlbumsRoute, ArtistsRoute, FoldersRoute, PlaylistsRoute),
            homeTopLevelNavKeys,
        )
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
    fun navigatingToSameDetailRouteIsSingleTopAndDoesNotDuplicateStackEntry() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 11)

        navigator.navigate(albumDetail)
        navigator.navigate(albumDetail)
        navigator.navigate(albumDetail)

        assertEquals(listOf(AlbumsRoute, albumDetail), state.currentBackStack)
        assertEquals(listOf(AlbumsRoute, albumDetail), state.backStack(AlbumsRoute))
    }

    @Test
    fun navigatingToDifferentDetailRoutesMaintainsSequentialStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val folder1 = FolderDetailRoute(volumeName = "primary", relativePath = "Music")
        val folder2 = FolderDetailRoute(volumeName = "primary", relativePath = "Music/Rock")

        navigator.navigate(folder1)
        navigator.navigate(folder2)

        assertEquals(listOf(FoldersRoute, folder1, folder2), state.currentBackStack)
    }

    @Test
    fun selectingCurrentTopLevelRouteAgainReturnsItsStackToRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)

        navigator.navigate(ArtistDetailRoute(artistName = "Artist 9"))
        navigator.navigate(ArtistsRoute)

        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute), state.currentBackStack)
    }

    @Test
    fun backFromNonHomeRootSelectsMostRecentHomeAndPreservesOtherStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(ArtistsRoute)
        navigator.navigate(SettingsRoute)

        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(ArtistsRoute, state.homeTopLevelRoute)
        assertEquals(listOf(SettingsRoute), state.backStack(SettingsRoute))
    }

    @Test
    fun backPopsDetailBeforeReturningToHomeRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(AlbumDetailRoute(volumeName = "external", mediaStoreId = 12))

        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(listOf(AlbumsRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, navigator.goBack())
        assertEquals(AlbumsRoute, state.currentTopLevelRoute)
    }

    @Test
    fun backFromHomeRootRequestsReturnToDesktop() {
        val state = NavigationState.initial()

        assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, Navigator(state).goBack())
        assertEquals(listOf(TracksRoute), state.currentBackStack)
    }

    @Test
    fun everyMediaBrowseRootCanBecomeHomeAndReturnToDesktop() {
        homeTopLevelNavKeys.forEach { route ->
            val state = NavigationState.initial()
            val navigator = Navigator(state)

            navigator.navigate(route)

            assertEquals(route, state.currentTopLevelRoute)
            assertEquals(route, state.homeTopLevelRoute)
            assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, navigator.goBack())
        }
    }

    @Test
    fun selectingHomeAndItsDetailsUpdatesHomeAnchor() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)

        navigator.navigate(ArtistsRoute)
        assertEquals(ArtistsRoute, state.homeTopLevelRoute)

        navigator.navigate(ArtistDetailRoute(artistName = "Artist 9"))
        assertEquals(ArtistsRoute, state.homeTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, ArtistDetailRoute(artistName = "Artist 9")), state.currentBackStack)
    }

    @Test
    fun scanReturnsToTheRouteThatOpenedIt() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(ArtistsRoute)
        val scanRoute = ScanMusicRoute(returnRoute = ArtistsRoute)

        navigator.navigate(scanRoute)

        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, scanRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, navigator.goBack())
    }

    @Test
    fun detailNavigationUsesItsOwningStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val folderDetail = FolderDetailRoute(volumeName = "sdcard", relativePath = "Music/Live")

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
        navigator.navigate(ArtistDetailRoute(artistName = "Artist 5"))
        navigator.navigate(FolderDetailRoute(volumeName = "sdcard", relativePath = "Music"))
        navigator.navigate(PlaylistDetailRoute(playlistId = 4))
        navigator.navigate(AlbumsRoute)

        val restored = NavigationState.restore(NavigationSnapshot.decode(state.snapshot().encode()))

        assertEquals(AlbumsRoute, restored.currentTopLevelRoute)
        assertEquals(AlbumsRoute, restored.homeTopLevelRoute)
        assertEquals(state.snapshot(), restored.snapshot())
    }

    @Test
    fun serializedSnapshotPersistsHomeAndScanReturnRoute() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(ArtistsRoute)
        val scanRoute = ScanMusicRoute(returnRoute = ArtistsRoute)
        navigator.navigate(scanRoute)

        val restored = NavigationState.restore(NavigationSnapshot.decode(state.snapshot().encode()))

        assertEquals(ArtistsRoute, restored.homeTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, scanRoute), restored.backStack(ArtistsRoute))
        assertEquals(state.snapshot(), restored.snapshot())
    }

    @Test
    fun legacySnapshotMapsScanToTracksAndDerivesHomeFromCurrentRoute() {
        val scanRoute = ScanMusicRoute(returnRoute = TracksRoute)
        val snapshot =
            legacySnapshot(
                currentTopLevelRoute = ArtistsRoute,
                stacks =
                    topLevelNavKeys.map { root ->
                        NavigationStackSnapshot(
                            root = root,
                            routes = if (root == TracksRoute) listOf(root, scanRoute) else listOf(root),
                        )
                    },
            )

        val restored = NavigationState.restore(NavigationSnapshot.decode(snapshot))

        assertEquals(ArtistsRoute, restored.homeTopLevelRoute)
        assertEquals(listOf(TracksRoute, scanRoute), restored.backStack(TracksRoute))
    }

    @Test
    fun legacySnapshotUsesTracksHomeForUtilityCurrentRoute() {
        val snapshot =
            legacySnapshot(
                currentTopLevelRoute = SettingsRoute,
                stacks = topLevelNavKeys.map { root -> NavigationStackSnapshot(root, listOf(root)) },
            )

        val restored = NavigationState.restore(NavigationSnapshot.decode(snapshot))

        assertEquals(TracksRoute, restored.homeTopLevelRoute)
        assertEquals(SettingsRoute, restored.currentTopLevelRoute)
    }

    @Test
    fun restoreRejectsUtilityRouteAsHome() {
        val initial = NavigationState.initial().snapshot()
        val invalid = initial.copy(homeTopLevelRoute = HistoryRoute)

        assertThrows(IllegalArgumentException::class.java) { NavigationState.restore(invalid) }
    }

    @Test
    fun restoreRejectsHomeCurrentRouteWithDifferentHomeAnchor() {
        val initial = NavigationState.initial().snapshot()
        val invalid = initial.copy(currentTopLevelRoute = ArtistsRoute)

        assertThrows(IllegalArgumentException::class.java) { NavigationState.restore(invalid) }
    }

    @Test
    fun invalidProcessSnapshotFallsBackToInitialNavigationState() {
        val restored = NavigationState.restoreOrInitial("not-a-valid-navigation-snapshot")

        assertEquals(TracksRoute, restored.currentTopLevelRoute)
        assertEquals(NavigationState.initial().snapshot(), restored.snapshot())
    }

    private fun legacySnapshot(
        currentTopLevelRoute: TopLevelNavKey,
        stacks: List<NavigationStackSnapshot>,
    ): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(2)
            output.writeLegacyRoute(currentTopLevelRoute)
            output.writeInt(stacks.size)
            stacks.forEach { stack ->
                output.writeLegacyRoute(stack.root)
                output.writeInt(stack.routes.size)
                stack.routes.forEach { route -> output.writeLegacyRoute(route) }
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
    }

    private fun DataOutputStream.writeLegacyRoute(route: MusicNavKey) {
        when (route) {
            TracksRoute -> writeByte(0)
            AlbumsRoute -> writeByte(1)
            ArtistsRoute -> writeByte(2)
            PlaylistsRoute -> writeByte(3)
            HistoryRoute -> writeByte(4)
            FoldersRoute -> writeByte(5)
            SettingsRoute -> writeByte(6)
            AboutRoute -> writeByte(7)
            is ScanMusicRoute -> writeByte(13)
            else -> error("legacy test fixture only supports top-level and scan routes")
        }
    }
}
