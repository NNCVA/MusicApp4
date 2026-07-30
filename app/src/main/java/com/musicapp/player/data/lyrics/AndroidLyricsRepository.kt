package com.musicapp.player.data.lyrics

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.Id3Decoder
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.core.lyrics.LyricsCandidates
import com.musicapp.player.core.lyrics.LyricsRepository
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.LyricsSourceResolver
import com.musicapp.player.core.lyrics.ResolvedLyrics
import com.musicapp.player.core.metadata.MetadataCacheKey
import com.musicapp.player.data.metadata.TrackContentUriResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
@OptIn(UnstableApi::class)
internal class AndroidLyricsRepository @Inject constructor(
    @param:ApplicationContext context: android.content.Context,
    private val trackUriResolver: TrackContentUriResolver,
    private val loadCoordinator: LyricsLoadCoordinator,
) : LyricsRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val lrcParser = LrcParser()
    private val sourceResolver = LyricsSourceResolver()
    private val frameSelector = EmbeddedLyricsFrameSelector(Id3LyricsFrameParser(lrcParser))

    override suspend fun load(track: Track): ResolvedLyrics {
        val key = MetadataCacheKey(track.id, track.dateModifiedMs)
        return loadCoordinator.load(key) { withContext(Dispatchers.IO) { readUncached(track) } }
    }

    private fun readUncached(track: Track): ResolvedLyrics {
        val external = readExternalLrc(track)
        val (sylt, uslt) = readEmbeddedLyrics(track)
        return sourceResolver.resolve(
            LyricsCandidates(
                externalLrc = external,
                embeddedSylt = sylt,
                embeddedUslt = uslt,
            ),
        )
    }

    private fun readExternalLrc(track: Track): LyricsCandidate? = runCatching {
        val stem = track.displayName.substringBeforeLast('.', track.displayName)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(track.id.volumeName)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val (selection, arguments) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? COLLATE NOCASE AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?" to
                arrayOf("$stem.lrc", track.relativePath)
        } else {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? COLLATE NOCASE AND " +
                "${MediaStore.Files.FileColumns.DATA} LIKE ?" to
                arrayOf("$stem.lrc", "%/${track.relativePath}$stem.lrc")
        }
        resolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
            resolver.openInputStream(uri)?.use { input ->
                lrcParser.parse(input.readAtMost(MAX_LRC_BYTES), LyricsSource.EXTERNAL_LRC)
            }
        }
    }.getOrNull()

    private fun readEmbeddedLyrics(track: Track): Pair<LyricsCandidate?, LyricsCandidate?> = runCatching {
        val tag = resolver.openInputStream(trackUriResolver.resolve(track.id))?.use(::readId3Tag)
            ?: return@runCatching null to null
        val metadata = Id3Decoder().decode(tag, tag.size) ?: return@runCatching null to null
        frameSelector.select(
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
        return header + (input.readExactly(payloadSize) ?: return null)
    }

    private fun InputStream.readExactly(byteCount: Int): ByteArray? {
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

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        while (output.size() <= maxBytes) {
            val count = read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        require(output.size() <= maxBytes) { "lyrics file exceeds size limit" }
        return output.toByteArray()
    }

    private companion object {
        const val ID3_HEADER_SIZE = 10
        const val MAX_ID3_BYTES = 4 * 1024 * 1024
        const val MAX_LRC_BYTES = 2 * 1024 * 1024
    }
}
