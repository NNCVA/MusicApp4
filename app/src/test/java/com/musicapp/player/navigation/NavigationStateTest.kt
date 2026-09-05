package com.musicapp.player.navigation

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationStateTest {
    @Test
    fun routeTableContainsNineStableTopLevelStacks() {
        assertEquals(
            listOf(
                TracksRoute,
                AlbumsRoute,
                ArtistsRoute,
                PlaylistsRoute,
                HistoryRoute,
                FoldersRoute,
                ScanMusicRoute,
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
    fun selectingTopLevelRouteResetsDestinationToRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 11)
        val playlistDetail = PlaylistDetailRoute(playlistId = 7)

        navigator.navigate(AlbumsRoute)
        navigator.navigate(albumDetail)
        navigator.navigate(PlaylistsRoute)
        navigator.navigate(playlistDetail)
        navigator.navigate(AlbumsRoute)

        assertEquals(listOf(AlbumsRoute), state.currentBackStack)
        assertEquals(listOf(PlaylistsRoute, playlistDetail), state.backStack(PlaylistsRoute))
    }

    @Test
    fun navigatingToScanAndSwitchingTopLevelResetsToRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)

        navigator.navigate(ScanMusicRoute)
        assertEquals(listOf(ScanMusicRoute), state.currentBackStack)

        navigator.navigate(AlbumsRoute)
        assertEquals(listOf(AlbumsRoute), state.currentBackStack)
        assertEquals(listOf(ScanMusicRoute), state.backStack(ScanMusicRoute))

        navigator.navigate(TracksRoute)
        assertEquals(listOf(TracksRoute), state.currentBackStack)
    }

    @Test
    fun navigatingToSameDetailRouteIsSingleTopAndDoesNotDuplicateStackEntry() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 11)

        navigator.navigate(AlbumsRoute)
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

        navigator.navigate(FoldersRoute)
        navigator.navigate(folder1)
        navigator.navigate(folder2)

        assertEquals(listOf(FoldersRoute, folder1, folder2), state.currentBackStack)
    }

    @Test
    fun selectingCurrentTopLevelRouteAgainReturnsItsStackToRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)

        navigator.navigate(ArtistsRoute)
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
        navigator.navigate(AlbumsRoute)
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
    fun scanRouteBackReturnsToHomeRoot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(ArtistsRoute)

        navigator.navigate(ScanMusicRoute)

        assertEquals(ScanMusicRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ScanMusicRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute), state.currentBackStack)
        assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, navigator.goBack())
    }

    @Test
    fun detailNavigationPushesOntoCurrentTopLevelStack() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val folderDetail = FolderDetailRoute(volumeName = "sdcard", relativePath = "Music/Live")

        navigator.navigate(FoldersRoute)
        navigator.navigate(folderDetail)

        assertEquals(FoldersRoute, state.currentTopLevelRoute)
        assertEquals(listOf(FoldersRoute, folderDetail), state.currentBackStack)
    }

    @Test
    fun crossEntityDetailNavigationPushesOntoCurrentStackAndPopsSequentially() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val artistDetail = ArtistDetailRoute(artistName = "Artist 9")
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 12, groupKey = "group-1")

        // 1. 从艺术家列表进入艺术家详情
        navigator.navigate(ArtistsRoute)
        navigator.navigate(artistDetail)
        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, artistDetail), state.currentBackStack)

        // 2. 跨实体进入专辑详情，保持当前宿主栈为 ArtistsRoute，顺序压入
        navigator.navigate(albumDetail)
        assertEquals(ArtistsRoute, state.currentTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, artistDetail, albumDetail), state.currentBackStack)

        // 3. 第一次返回：弹出专辑详情，回到艺术家详情
        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(listOf(ArtistsRoute, artistDetail), state.currentBackStack)

        // 4. 第二次返回：弹出艺术家详情，回到艺术家列表根页面
        assertEquals(BackNavigationResult.CONSUMED, navigator.goBack())
        assertEquals(listOf(ArtistsRoute), state.currentBackStack)

        // 5. 第三次返回：根页面返回桌面
        assertEquals(BackNavigationResult.REQUEST_RETURN_TO_DESKTOP, navigator.goBack())
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
    fun serializedSnapshotPreservesAlbumGroupKey() {
        val state = NavigationState.initial()
        val route = AlbumDetailRoute(
            volumeName = "external",
            mediaStoreId = 3,
            groupKey = "stable-album-group-key",
        )
        Navigator(state).navigate(route)

        val restored = NavigationState.restore(NavigationSnapshot.decode(state.snapshot().encode()))

        assertEquals(route, restored.currentBackStack.last())
    }

    @Test
    fun previousSnapshotVersionWithoutAlbumGroupKeyRemainsReadable() {
        val state = NavigationState.initial()
        Navigator(state).navigate(AlbumDetailRoute(volumeName = "external", mediaStoreId = 3))
        val snapshot = state.snapshot()

        val restored = NavigationSnapshot.decode(previousSnapshot(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun serializedSnapshotPersistsHomeAndScanRoute() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        navigator.navigate(ArtistsRoute)
        navigator.navigate(ScanMusicRoute)

        val restored = NavigationState.restore(NavigationSnapshot.decode(state.snapshot().encode()))

        assertEquals(ArtistsRoute, restored.homeTopLevelRoute)
        assertEquals(ScanMusicRoute, restored.currentTopLevelRoute)
        assertEquals(listOf(ScanMusicRoute), restored.backStack(ScanMusicRoute))
        assertEquals(state.snapshot(), restored.snapshot())
    }

    @Test
    fun legacySnapshotMapsScanToTracksAndDerivesHomeFromCurrentRoute() {
        val snapshot =
            legacySnapshot(
                currentTopLevelRoute = ArtistsRoute,
                stacks =
                    topLevelNavKeys.map { root ->
                        NavigationStackSnapshot(
                            root = root,
                            routes = listOf(root),
                        )
                    },
            )

        val restored = NavigationState.restore(NavigationSnapshot.decode(snapshot))

        assertEquals(ArtistsRoute, restored.homeTopLevelRoute)
        assertEquals(listOf(ScanMusicRoute), restored.backStack(ScanMusicRoute))
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

    @Test
    fun crossEntityDetailNavigationPreservesMixedStackInSnapshot() {
        val state = NavigationState.initial()
        val navigator = Navigator(state)
        val artistDetail = ArtistDetailRoute(artistName = "Artist 9")
        val albumDetail = AlbumDetailRoute(volumeName = "external", mediaStoreId = 12, groupKey = "group-1")

        navigator.navigate(ArtistsRoute)
        navigator.navigate(artistDetail)
        navigator.navigate(albumDetail)

        val encoded = state.snapshot().encode()
        val restored = NavigationState.restore(NavigationSnapshot.decode(encoded))

        assertEquals(ArtistsRoute, restored.currentTopLevelRoute)
        assertEquals(ArtistsRoute, restored.homeTopLevelRoute)
        assertEquals(listOf(ArtistsRoute, artistDetail, albumDetail), restored.backStack(ArtistsRoute))
        assertEquals(state.snapshot(), restored.snapshot())
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

    private fun previousSnapshot(snapshot: NavigationSnapshot): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(3)
            output.writeLegacyRoute(snapshot.currentTopLevelRoute)
            output.writeLegacyRoute(snapshot.homeTopLevelRoute)
            output.writeInt(snapshot.stacks.size)
            snapshot.stacks.forEach { stack ->
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
            ScanMusicRoute -> writeByte(13)
            is AlbumDetailRoute -> {
                writeByte(9)
                writeUTF(route.volumeName)
                writeLong(route.mediaStoreId)
            }
            else -> error("legacy test fixture only supports top-level and scan routes")
        }
    }
}
