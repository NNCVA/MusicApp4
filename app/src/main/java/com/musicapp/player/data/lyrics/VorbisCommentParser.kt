package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.LyricsTextDecoder
import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt32LE
import com.musicapp.player.data.lyrics.LyricsIoUtils.skipFully
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class VorbisCommentParser(
    private val lrcParser: LrcParser = LrcParser(),
) {
    fun parse(data: ByteArray): Pair<LyricsCandidate?, LyricsCandidate?> =
        parse(ByteArrayInputStream(data))

    fun parse(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> {
        val vendorLengthBytes = input.readExactly(4) ?: return null to null
        val vendorLength = vendorLengthBytes.readUInt32LE()
        if (vendorLength < 0 || vendorLength > MAX_VENDOR_LENGTH) return null to null
        if (input.skipFully(vendorLength) < vendorLength) return null to null

        val countBytes = input.readExactly(4) ?: return null to null
        val commentCount = countBytes.readUInt32LE()
        if (commentCount <= 0) return null to null

        val comments = mutableListOf<Pair<String, String>>()
        val safeCount = minOf(commentCount, MAX_COMMENT_COUNT)
        for (i in 0 until safeCount) {
            val lengthBytes = input.readExactly(4) ?: break
            val length = lengthBytes.readUInt32LE()
            if (length <= 0 || length > MAX_COMMENT_LENGTH) {
                if (length > 0) input.skipFully(length)
                continue
            }
            val commentBytes = input.readExactly(length.toInt()) ?: break
            val commentStr = LyricsTextDecoder.decode(commentBytes)
            val separatorIndex = commentStr.indexOf('=')
            if (separatorIndex > 0) {
                val key = commentStr.substring(0, separatorIndex).trim().uppercase()
                val value = commentStr.substring(separatorIndex + 1).trim()
                if (value.isNotEmpty()) {
                    comments.add(key to value)
                }
            }
        }

        return extractLyrics(comments)
    }

    private fun extractLyrics(comments: List<Pair<String, String>>): Pair<LyricsCandidate?, LyricsCandidate?> {
        if (comments.isEmpty()) return null to null

        val candidateMap = mutableListOf<ParsedComment>()
        for ((key, value) in comments) {
            val parsedSylt = lrcParser.parse(value, LyricsSource.EMBEDDED_SYLT)
            val parsedUslt = lrcParser.parse(value, LyricsSource.EMBEDDED_USLT)
            candidateMap.add(ParsedComment(key, value, parsedSylt, parsedUslt))
        }

        // 1. Resolve synchronized lyrics
        val syncedCandidate = findBestSynced(candidateMap)

        // 2. Resolve unsynchronized lyrics
        val unsyncedCandidate = findBestUnsynced(candidateMap, fallbackRawText = syncedCandidate?.rawText)

        if (syncedCandidate == null && unsyncedCandidate == null) return null to null
        return syncedCandidate to unsyncedCandidate
    }

    private fun findBestSynced(comments: List<ParsedComment>): LyricsCandidate? {
        // Priority 1: Explicit synced tags
        comments.firstOrNull { it.key in SYNCED_KEYS && it.syltCandidate.timedLines.isNotEmpty() }
            ?.let { return it.syltCandidate }

        // Priority 2: General lyrics tags with timestamps
        comments.firstOrNull { it.key in GENERAL_LYRICS_KEYS && it.syltCandidate.timedLines.isNotEmpty() }
            ?.let { return it.syltCandidate }

        // Priority 3: Unsynced tags with timestamps (some taggers write LRC to UNSYNCEDLYRICS)
        comments.firstOrNull { it.key in UNSYNCED_KEYS && it.syltCandidate.timedLines.isNotEmpty() }
            ?.let { return it.syltCandidate }

        // Priority 4: Fallback description tags with timestamps
        comments.firstOrNull { it.key in FALLBACK_KEYS && it.syltCandidate.timedLines.isNotEmpty() }
            ?.let { return it.syltCandidate }

        return null
    }

    private fun findBestUnsynced(comments: List<ParsedComment>, fallbackRawText: String?): LyricsCandidate? {
        // Priority 1: Explicit unsynced tags
        comments.firstOrNull { it.key in UNSYNCED_KEYS && !it.rawText.isBlank() }
            ?.let {
                val plainText = it.usltCandidate.rawText ?: it.rawText
                return LyricsCandidate(LyricsSource.EMBEDDED_USLT, plainText)
            }

        // Priority 2: General lyrics tags
        comments.firstOrNull { it.key in GENERAL_LYRICS_KEYS && !it.rawText.isBlank() }
            ?.let {
                val plainText = it.usltCandidate.rawText ?: it.rawText
                return LyricsCandidate(LyricsSource.EMBEDDED_USLT, plainText)
            }

        // Priority 3: Fallback keys
        comments.firstOrNull { it.key in FALLBACK_KEYS && !it.rawText.isBlank() }
            ?.let {
                val plainText = it.usltCandidate.rawText ?: it.rawText
                return LyricsCandidate(LyricsSource.EMBEDDED_USLT, plainText)
            }

        // Priority 4: Use raw text from synced candidate if available
        if (!fallbackRawText.isNullOrBlank()) {
            return LyricsCandidate(LyricsSource.EMBEDDED_USLT, fallbackRawText)
        }

        return null
    }

    private data class ParsedComment(
        val key: String,
        val rawText: String,
        val syltCandidate: LyricsCandidate,
        val usltCandidate: LyricsCandidate,
    )

    private companion object {
        val SYNCED_KEYS = setOf(
            "SYNCEDLYRICS",
            "SYNCED_LYRICS",
            "SYNCED LYRICS",
            "LYRICS_SYNCHRONISED",
            "LYRICS_SYNCHRONIZED",
        )

        val GENERAL_LYRICS_KEYS = setOf(
            "LYRICS",
            "LEAD_PERFORMER_LYRICS",
            "TEXT",
        )

        val UNSYNCED_KEYS = setOf(
            "UNSYNCEDLYRICS",
            "UNSYNCED_LYRICS",
            "UNSYNCED LYRICS",
        )

        val FALLBACK_KEYS = setOf(
            "DESCRIPTION",
            "COMMENT",
            "SUBTITLE",
        )

        const val MAX_VENDOR_LENGTH = 1024 * 1024L
        const val MAX_COMMENT_LENGTH = 4 * 1024 * 1024L
        const val MAX_COMMENT_COUNT = 10000L
    }
}
