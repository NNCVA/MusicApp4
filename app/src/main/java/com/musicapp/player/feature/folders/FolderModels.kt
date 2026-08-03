package com.musicapp.player.feature.folders

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategorySortDirection
import java.util.Locale

data class FolderId(
    val volumeName: String,
    val relativePath: String,
) {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(relativePath == normalizeFolderPath(relativePath)) {
            "relativePath must be a normalized directory path without a trailing slash"
        }
    }

    val sourceId: String
        get() = "$volumeName|$relativePath"
}

enum class FolderSortField { NAME, TRACK_COUNT }

data class FolderSort(
    val field: FolderSortField = FolderSortField.NAME,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

data class FolderNode(
    val id: FolderId,
    val displayName: String,
    val directTracks: List<Track>,
    val children: List<FolderNode>,
) {
    val recursiveTracks: List<Track> =
        (directTracks + children.flatMap(FolderNode::recursiveTracks))
            .sortedWith(compareBy<Track>({ it.title.lowercase(Locale.ROOT) }, { it.id.volumeName }, { it.id.mediaStoreId }))
    val recursiveTrackCount: Int
        get() = recursiveTracks.size
}

object FolderTree {
    fun build(tracks: List<Track>): List<FolderNode> =
        tracks.groupBy { it.id.volumeName }
            .map { (volumeName, volumeTracks) -> buildVolume(volumeName, volumeTracks) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, FolderNode::displayName).thenBy(FolderNode::displayName))

    fun find(roots: List<FolderNode>, id: FolderId): FolderNode? {
        fun FolderNode.findRecursively(): FolderNode? =
            if (this.id == id) this else children.firstNotNullOfOrNull { it.findRecursively() }
        return roots.firstNotNullOfOrNull { it.findRecursively() }
    }

    fun sorted(nodes: List<FolderNode>, sort: FolderSort): List<FolderNode> {
        val primary =
            when (sort.field) {
                FolderSortField.NAME -> compareBy<FolderNode> { it.displayName.lowercase(Locale.ROOT) }
                FolderSortField.TRACK_COUNT -> compareBy(FolderNode::recursiveTrackCount)
            }
        val directed = if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed()
        return nodes.sortedWith(directed.thenBy { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.id.sourceId })
    }

    private fun buildVolume(volumeName: String, tracks: List<Track>): FolderNode {
        val root = MutableFolderNode(FolderId(volumeName, ""), volumeName)
        tracks.sortedWith(compareBy<Track>({ normalizeFolderPath(it.relativePath) }, { it.id.mediaStoreId }))
            .forEach { track ->
                var current = root
                var path = ""
                normalizeFolderPath(track.relativePath)
                    .split('/')
                    .filter(String::isNotEmpty)
                    .forEach { segment ->
                        path = if (path.isEmpty()) segment else "$path/$segment"
                        current = current.children.getOrPut(segment) {
                            MutableFolderNode(FolderId(volumeName, path), segment)
                        }
                    }
                current.directTracks += track
            }
        return root.freeze()
    }

    private fun MutableFolderNode.freeze(): FolderNode =
        FolderNode(
            id = id,
            displayName = displayName,
            directTracks = directTracks.sortedWith(
                compareBy<Track>({ it.title.lowercase(Locale.ROOT) }, { it.id.mediaStoreId }),
            ),
            children = children.values.map { it.freeze() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, FolderNode::displayName).thenBy(FolderNode::displayName)),
        )

    private data class MutableFolderNode(
        val id: FolderId,
        val displayName: String,
        val directTracks: MutableList<Track> = mutableListOf(),
        val children: LinkedHashMap<String, MutableFolderNode> = linkedMapOf(),
    )
}

private fun normalizeFolderPath(path: String): String {
    val segments = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}

internal fun FolderSort.next(field: FolderSortField): FolderSort =
    if (this.field == field) {
        copy(
            direction =
                if (direction == CategorySortDirection.ASCENDING) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    } else {
        FolderSort(
            field = field,
            direction =
                if (field == FolderSortField.NAME) {
                    CategorySortDirection.ASCENDING
                } else {
                    CategorySortDirection.DESCENDING
                },
        )
    }
