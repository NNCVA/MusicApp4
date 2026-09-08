package com.musicapp.player.feature.playlists

import com.musicapp.player.core.domain.model.Track

data class PlaylistTextDocument(
    val displayName: String,
    val paths: List<String>,
)

/** The small, versioned text format used for playlist transfer. */
object PlaylistTextCodec {
    const val HEADER = "# MusicApp4 Playlist v1"
    private const val NAME_PREFIX = "# name="
    private const val FALLBACK_FILE_NAME = "playlist"

    fun encode(displayName: String, paths: List<String>): String = buildString {
        appendLine(HEADER)
        append(NAME_PREFIX)
        appendLine(displayName)
        paths.forEach(::appendLine)
    }

    fun decode(text: String): PlaylistTextDocument {
        val lines = text.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        require(headerIndex >= 0 && lines[headerIndex] == HEADER) {
            "unsupported playlist text header"
        }
        val nameIndex = (headerIndex + 1 until lines.size).firstOrNull { index ->
            lines[index].isNotBlank()
        } ?: error("playlist name is missing")
        val nameLine = lines[nameIndex]
        require(nameLine.startsWith(NAME_PREFIX)) { "playlist name is missing" }
        val displayName = nameLine.removePrefix(NAME_PREFIX).trim()
        require(displayName.isNotBlank()) { "playlist name is blank" }

        return PlaylistTextDocument(
            displayName = displayName,
            paths = lines.drop(nameIndex + 1)
                .map(String::trim)
                .filter(String::isNotEmpty),
        )
    }

    fun pathFor(track: Track): String = canonicalPath(
        listOf(track.id.volumeName, track.relativePath, track.displayName).joinToString("/"),
    )

    fun canonicalPath(rawPath: String): String {
        val segments = rawPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        require(segments.size >= 2) { "playlist track path must include a volume and file name" }
        require(segments.none { it == ".." }) { "playlist track path must not contain parent traversal" }
        return segments.joinToString("/")
    }

    fun suggestedFileName(displayName: String): String {
        val safeName = displayName
            .replace(INVALID_FILE_NAME_CHARACTERS, "_")
            .trim()
            .ifBlank { FALLBACK_FILE_NAME }
        return "$safeName.txt"
    }

    private val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")
}
