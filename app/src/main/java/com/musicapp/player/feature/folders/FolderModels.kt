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
    val isVolumeRoot: Boolean
        get() = id.relativePath.isEmpty()

    val hasDirectTracks: Boolean
        get() = directTracks.isNotEmpty()

    val recursiveTracks: List<Track> =
        (directTracks + children.flatMap(FolderNode::recursiveTracks))
            .sortedWith(compareBy<Track>({ it.title.lowercase(Locale.ROOT) }, { it.id.volumeName }, { it.id.mediaStoreId }))
    val recursiveTrackCount: Int
        get() = recursiveTracks.size
}

/**
 * A volume root together with platform metadata used by the folders landing page.
 *
 * Metadata is nullable because storage APIs can temporarily fail while a volume is
 * being mounted or when a path is no longer readable. The [folder] remains the
 * stable source of truth for navigation and playback in that case.
 */
data class FolderVolumeItem(
    val folder: FolderNode,
    val displayName: String? = null,
    val rootPath: String? = null,
    val isPrimary: Boolean = false,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
) {
    init {
        require(folder.isVolumeRoot) { "volume item must point at a volume root" }
        require(usedBytes == null || usedBytes >= 0) { "usedBytes must be null or non-negative" }
        require(totalBytes == null || totalBytes >= 0) { "totalBytes must be null or non-negative" }
        require(usedBytes == null || totalBytes == null || usedBytes <= totalBytes) {
            "usedBytes must not exceed totalBytes"
        }
    }

    val id: FolderId
        get() = folder.id
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

    /**
     * Returns every non-root folder that directly contains at least one track.
     *
     * A folder may also have child folders; it is still a shortcut target and its
     * recursive track set includes all descendants. Folder identity includes the
     * volume name, so same-named paths on different volumes remain distinct.
     */
    fun musicFolders(roots: List<FolderNode>): List<FolderNode> =
        roots
            .asSequence()
            .flatMap { root -> root.descendantsAndSelf() }
            .filter { !it.isVolumeRoot && it.hasDirectTracks }
            .sortedWith(
                compareBy<FolderNode>({ it.displayName.lowercase(Locale.ROOT) })
                    .thenBy { it.displayName }
                    .thenBy { it.id.volumeName }
                    .thenBy { it.id.relativePath },
            )
            .toList()

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

    private fun FolderNode.descendantsAndSelf(): Sequence<FolderNode> =
        sequence {
            yield(this@descendantsAndSelf)
            children.forEach { child -> yieldAll(child.descendantsAndSelf()) }
        }

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
