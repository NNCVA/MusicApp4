package com.musicapp.player.navigation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class NavigationStackSnapshot(
    val root: TopLevelNavKey,
    val routes: List<MusicNavKey>,
)

@Serializable
data class NavigationSnapshot(
    val currentTopLevelRoute: TopLevelNavKey,
    val stacks: List<NavigationStackSnapshot>,
) {
    fun encode(): String = NavigationSnapshotCodec.encode(this)

    companion object {
        fun decode(encoded: String): NavigationSnapshot = NavigationSnapshotCodec.decode(encoded)
    }
}

private object NavigationSnapshotCodec {
    private const val VERSION = 1
    private const val MAX_STACK_SIZE = 1_024

    fun encode(snapshot: NavigationSnapshot): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeRoute(snapshot.currentTopLevelRoute)
            output.writeInt(snapshot.stacks.size)
            snapshot.stacks.forEach { stack ->
                output.writeRoute(stack.root)
                output.writeInt(stack.routes.size)
                stack.routes.forEach { route -> output.writeRoute(route) }
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String): NavigationSnapshot {
        require(encoded.isNotBlank()) { "encoded snapshot must not be blank" }
        val input = DataInputStream(ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)))
        return input.use { stream ->
            require(stream.readInt() == VERSION) { "unsupported navigation snapshot version" }
            val currentTopLevelRoute = stream.readRoute().asTopLevel()
            val stacks =
                List(stream.readCollectionSize(maximum = topLevelNavKeys.size)) {
                    val root = stream.readRoute().asTopLevel()
                    val routes =
                        List(stream.readCollectionSize(maximum = MAX_STACK_SIZE)) {
                            stream.readRoute()
                        }
                    NavigationStackSnapshot(root = root, routes = routes)
                }
            require(stream.available() == 0) { "navigation snapshot has trailing data" }
            NavigationSnapshot(currentTopLevelRoute = currentTopLevelRoute, stacks = stacks)
        }
    }

    private fun DataOutputStream.writeRoute(route: MusicNavKey) {
        when (route) {
            TracksRoute -> writeByte(0)
            AlbumsRoute -> writeByte(1)
            ArtistsRoute -> writeByte(2)
            PlaylistsRoute -> writeByte(3)
            HistoryRoute -> writeByte(4)
            FoldersRoute -> writeByte(5)
            SettingsRoute -> writeByte(6)
            AboutRoute -> writeByte(7)
            is TrackInfoRoute -> {
                writeByte(8)
                writeUTF(route.volumeName)
                writeLong(route.mediaStoreId)
            }
            is AlbumDetailRoute -> {
                writeByte(9)
                writeUTF(route.volumeName)
                writeLong(route.mediaStoreId)
            }
            is ArtistDetailRoute -> {
                writeByte(10)
                writeLong(route.mediaStoreId)
            }
            is PlaylistDetailRoute -> {
                writeByte(11)
                writeLong(route.playlistId)
            }
            is FolderDetailRoute -> {
                writeByte(12)
                writeUTF(route.relativePath)
            }
        }
    }

    private fun DataInputStream.readRoute(): MusicNavKey =
        when (val routeType = readUnsignedByte()) {
            0 -> TracksRoute
            1 -> AlbumsRoute
            2 -> ArtistsRoute
            3 -> PlaylistsRoute
            4 -> HistoryRoute
            5 -> FoldersRoute
            6 -> SettingsRoute
            7 -> AboutRoute
            8 -> TrackInfoRoute(volumeName = readUTF(), mediaStoreId = readLong())
            9 -> AlbumDetailRoute(volumeName = readUTF(), mediaStoreId = readLong())
            10 -> ArtistDetailRoute(mediaStoreId = readLong())
            11 -> PlaylistDetailRoute(playlistId = readLong())
            12 -> FolderDetailRoute(relativePath = readUTF())
            else -> throw IllegalArgumentException("unknown navigation route type: $routeType")
        }

    private fun DataInputStream.readCollectionSize(maximum: Int): Int =
        readInt().also { size ->
            require(size in 0..maximum) { "invalid navigation collection size: $size" }
        }

    private fun MusicNavKey.asTopLevel(): TopLevelNavKey =
        this as? TopLevelNavKey
            ?: throw IllegalArgumentException("expected a top-level navigation route")
}

class NavigationState private constructor(
    currentTopLevelRoute: TopLevelNavKey,
    private val mutableBackStacks: Map<TopLevelNavKey, MutableList<MusicNavKey>>,
) {
    var currentTopLevelRoute: TopLevelNavKey = currentTopLevelRoute
        private set

    val currentBackStack: List<MusicNavKey>
        get() = backStack(currentTopLevelRoute)

    fun backStack(route: TopLevelNavKey): List<MusicNavKey> =
        checkNotNull(mutableBackStacks[route]).toList()

    fun snapshot(): NavigationSnapshot =
        NavigationSnapshot(
            currentTopLevelRoute = currentTopLevelRoute,
            stacks =
                topLevelNavKeys.map { root ->
                    NavigationStackSnapshot(root = root, routes = backStack(root))
                },
        )

    internal fun select(route: TopLevelNavKey) {
        currentTopLevelRoute = route
    }

    internal fun reset(route: TopLevelNavKey) {
        stack(route).apply {
            clear()
            add(route)
        }
    }

    internal fun push(route: MusicNavKey) {
        stack(route.owner()).add(route)
    }

    internal fun popCurrent() {
        check(currentBackStack.size > 1) { "cannot pop a top-level root" }
        stack(currentTopLevelRoute).let { currentStack ->
            currentStack.removeAt(currentStack.lastIndex)
        }
    }

    private fun stack(route: TopLevelNavKey): MutableList<MusicNavKey> =
        checkNotNull(mutableBackStacks[route])

    companion object {
        fun initial(): NavigationState =
            NavigationState(
                currentTopLevelRoute = TracksRoute,
                mutableBackStacks = topLevelNavKeys.associateWith { mutableListOf<MusicNavKey>(it) },
            )

        fun restoreOrInitial(encodedSnapshot: String): NavigationState =
            try {
                restore(NavigationSnapshot.decode(encodedSnapshot))
            } catch (_: Exception) {
                initial()
            }

        fun restore(snapshot: NavigationSnapshot): NavigationState {
            val expectedRoots = topLevelNavKeys.toSet()
            val stackByRoot = snapshot.stacks.associateBy(NavigationStackSnapshot::root)
            require(snapshot.stacks.size == expectedRoots.size && stackByRoot.keys == expectedRoots) {
                "snapshot must contain exactly one stack for every top-level route"
            }
            require(snapshot.currentTopLevelRoute in expectedRoots) { "unknown current top-level route" }

            stackByRoot.forEach { (root, stack) ->
                require(stack.routes.isNotEmpty() && stack.routes.first() == root) {
                    "stack must start with its root"
                }
                require(stack.routes.withIndex().all { (index, route) ->
                    route.owner() == root && (index == 0 || route !is TopLevelNavKey)
                }) {
                    "stack contains a route owned by another top-level route"
                }
            }

            return NavigationState(
                currentTopLevelRoute = snapshot.currentTopLevelRoute,
                mutableBackStacks =
                    topLevelNavKeys.associateWith { root ->
                        checkNotNull(stackByRoot[root]).routes.toMutableList()
                    },
            )
        }
    }
}

enum class BackNavigationResult {
    CONSUMED,
    REQUEST_EXIT,
}

class Navigator(private val state: NavigationState) {
    fun navigate(route: MusicNavKey) {
        if (route is TopLevelNavKey) {
            if (route == state.currentTopLevelRoute) state.reset(route) else state.select(route)
            return
        }

        val owner = route.owner()
        state.select(owner)
        state.push(route)
    }

    fun goBack(): BackNavigationResult {
        if (state.currentBackStack.size > 1) {
            state.popCurrent()
            return BackNavigationResult.CONSUMED
        }
        if (state.currentTopLevelRoute != TracksRoute) {
            state.select(TracksRoute)
            return BackNavigationResult.CONSUMED
        }
        return BackNavigationResult.REQUEST_EXIT
    }
}
