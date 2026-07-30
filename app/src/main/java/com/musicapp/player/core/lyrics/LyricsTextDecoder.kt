package com.musicapp.player.core.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object LyricsTextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return when {
            bytes.startsWith(UTF8_BOM) -> bytes.decode(Charsets.UTF_8, UTF8_BOM.size)
            bytes.startsWith(UTF16_LE_BOM) -> bytes.decode(Charsets.UTF_16LE, UTF16_LE_BOM.size)
            bytes.startsWith(UTF16_BE_BOM) -> bytes.decode(Charsets.UTF_16BE, UTF16_BE_BOM.size)
            else -> decodeStrictUtf8(bytes) ?: bytes.toString(GB18030)
        }.trimStart('\uFEFF')
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun ByteArray.decode(charset: Charset, offset: Int): String =
        String(this, offset, size - offset, charset)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val GB18030: Charset = Charset.forName("GB18030")
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
