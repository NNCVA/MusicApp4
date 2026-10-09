package com.musicapp.player.data.background

import java.io.IOException

internal sealed interface StoredBackgroundSelection {
    data object None : StoredBackgroundSelection
    data object Unavailable : StoredBackgroundSelection
    data class Ready(val fileName: String, val sha256: String) : StoredBackgroundSelection

    fun encode(): ByteArray = when (this) {
        None -> "1\nnone\n"
        Unavailable -> "1\nunavailable\n"
        is Ready -> "1\nready\n$fileName\n$sha256\n"
    }.toByteArray(Charsets.UTF_8)

    companion object {
        const val MAX_METADATA_BYTES = 1024
        private val fileNamePattern = Regex("background-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")
        private val digestPattern = Regex("[0-9a-f]{64}")

        fun decode(bytes: ByteArray): StoredBackgroundSelection {
            if (bytes.size > MAX_METADATA_BYTES) throw IOException("Invalid background metadata")
            val lines = bytes.toString(Charsets.UTF_8).split('\n')
            if (lines.firstOrNull() != "1" || lines.lastOrNull() != "") {
                throw IOException("Invalid background metadata")
            }
            return when {
                lines == listOf("1", "none", "") -> None
                lines == listOf("1", "unavailable", "") -> Unavailable
                lines.size == 5 && lines[1] == "ready" &&
                    fileNamePattern.matches(lines[2]) && digestPattern.matches(lines[3]) -> Ready(lines[2], lines[3])
                else -> throw IOException("Invalid background metadata")
            }
        }
    }
}
