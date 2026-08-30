package com.musicapp.player.data.lyrics

import java.io.InputStream

internal object LyricsIoUtils {
    const val MAX_READ_BUFFER_SIZE = 8192

    fun InputStream.readExactly(byteCount: Int): ByteArray? {
        if (byteCount < 0) return null
        if (byteCount == 0) return ByteArray(0)
        val result = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val count = read(result, offset, byteCount - offset)
            if (count < 0) return null
            if (count == 0) continue
            offset += count
        }
        return result
    }

    fun InputStream.readUpTo(maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val count = read(buffer, offset, maxBytes - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return buffer.copyOf(offset)
    }

    fun InputStream.skipFully(byteCount: Long): Long {
        if (byteCount <= 0) return 0L
        var remaining = byteCount
        val buffer = ByteArray(minOf(remaining, MAX_READ_BUFFER_SIZE.toLong()).toInt())
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val count = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (count < 0) break
                if (count == 0) continue
                remaining -= count
            }
        }
        return byteCount - remaining
    }

    fun ByteArray.readUInt32LE(offset: Int = 0): Long =
        ((this[offset].toLong() and 0xFF)) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    fun ByteArray.readUInt32BE(offset: Int = 0): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            ((this[offset + 3].toLong() and 0xFF))

    fun ByteArray.readUInt24BE(offset: Int = 0): Int =
        ((this[offset].toInt() and 0xFF) shl 16) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF))

    fun ByteArray.readUInt16BE(offset: Int = 0): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or
            ((this[offset + 1].toInt() and 0xFF))

    fun ByteArray.readUInt16LE(offset: Int = 0): Int =
        ((this[offset].toInt() and 0xFF)) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}
