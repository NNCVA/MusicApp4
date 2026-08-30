package com.musicapp.player.data.lyrics

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUpTo
import java.io.BufferedInputStream
import java.io.InputStream

@OptIn(UnstableApi::class)
internal class EmbeddedLyricsExtractor(
    private val lrcParser: LrcParser = LrcParser(),
    private val id3FrameSelector: EmbeddedLyricsFrameSelector = EmbeddedLyricsFrameSelector(Id3LyricsFrameParser(lrcParser)),
    private val flacExtractor: FlacLyricsExtractor = FlacLyricsExtractor(VorbisCommentParser(lrcParser)),
    private val mp4Extractor: Mp4LyricsExtractor = Mp4LyricsExtractor(lrcParser),
    private val oggExtractor: OggLyricsExtractor = OggLyricsExtractor(VorbisCommentParser(lrcParser)),
) {
    fun extract(
        input: InputStream,
        mimeType: String? = null,
        displayName: String? = null,
    ): Pair<LyricsCandidate?, LyricsCandidate?> {
        val bufferedInput = if (input.markSupported()) input else BufferedInputStream(input, BUFFER_SIZE)
        bufferedInput.mark(PROBE_SIZE)

        val probeBytes = bufferedInput.readUpTo(PROBE_SIZE)
        bufferedInput.reset()

        if (probeBytes.size >= 4) {
            when {
                isId3(probeBytes) -> {
                    val id3Result = extractId3(bufferedInput)
                    if (id3Result.first != null || id3Result.second != null) {
                        return id3Result
                    }
                }
                isFlac(probeBytes) -> {
                    return flacExtractor.extract(bufferedInput)
                }
                isOgg(probeBytes) -> {
                    return oggExtractor.extract(bufferedInput)
                }
                isMp4(probeBytes) -> {
                    return mp4Extractor.extract(bufferedInput)
                }
            }
        }

        // Fallback by extension or MIME type if probe magic was inconclusive
        val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mime = mimeType?.lowercase().orEmpty()

        return when {
            extension == "flac" || mime == "audio/flac" || mime == "audio/x-flac" -> {
                flacExtractor.extract(bufferedInput)
            }
            extension == "ogg" || extension == "oga" || extension == "opus" ||
                mime == "audio/ogg" || mime == "audio/opus" || mime == "application/ogg" -> {
                oggExtractor.extract(bufferedInput)
            }
            extension == "m4a" || extension == "mp4" || extension == "aac" ||
                mime == "audio/mp4" || mime == "audio/m4a" || mime == "audio/aac" || mime == "audio/x-m4a" -> {
                mp4Extractor.extract(bufferedInput)
            }
            extension == "mp3" || mime == "audio/mpeg" || mime == "audio/mp3" -> {
                extractId3(bufferedInput)
            }
            else -> {
                // Try format extractors in sequence
                extractId3(bufferedInput)
            }
        }
    }

    private fun extractId3(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> = runCatching {
        val tag = readId3Tag(input) ?: return null to null
        val metadata = Id3Decoder().decode(tag, tag.size) ?: return null to null
        id3FrameSelector.select(
            buildList {
                for (index in 0 until metadata.length()) {
                    val frame = metadata[index] as? BinaryFrame ?: continue
                    if (frame.id == "SYLT" || frame.id == "SLT" || frame.id == "USLT" || frame.id == "ULT") {
                        add(EmbeddedLyricsFrame(frame.id, frame.data))
                    }
                }
            },
        )
    }.getOrElse { null to null }

    private fun readId3Tag(input: InputStream): ByteArray? {
        val header = input.readExactly(ID3_HEADER_SIZE) ?: return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }
        if ((6..9).any { header[it].toInt() and 0x80 != 0 }) return null
        val payloadSize = ((header[6].toInt() and 0x7F) shl 21) or
            ((header[7].toInt() and 0x7F) shl 14) or
            ((header[8].toInt() and 0x7F) shl 7) or
            (header[9].toInt() and 0x7F)
        if (payloadSize !in 1..MAX_ID3_BYTES) return null
        val payload = input.readExactly(payloadSize) ?: return null
        return header + payload
    }

    private fun isId3(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()

    private fun isFlac(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()

    private fun isOgg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()

    private fun isMp4(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
        return boxType == "ftyp" || boxType == "moov" || boxType == "mdat" ||
            boxType == "free" || boxType == "skip" || boxType == "wide" || boxType == "meta"
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROBE_SIZE = 64
        const val ID3_HEADER_SIZE = 10
        const val MAX_ID3_BYTES = 4 * 1024 * 1024
    }
}
