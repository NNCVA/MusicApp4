package com.musicapp.player.data.lyrics

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
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
internal class AndroidLyricsRepository @Inject constructor(
    @ApplicationContext context: android.content.Context,
    private val trackUriResolver: TrackContentUriResolver,
    private val loadCoordinator: LyricsLoadCoordinator,
) : LyricsRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val lrcParser = LrcParser()
    private val sourceResolver = LyricsSourceResolver()
    private val frameSelector = EmbeddedLyricsFrameSelector(Id3LyricsFrameParser(lrcParser))
    private val embeddedLyricsExtractor = EmbeddedLyricsExtractor(
        lrcParser = lrcParser,
        id3FrameSelector = frameSelector,
    )

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
        resolver.openInputStream(trackUriResolver.resolve(track.id))?.use { input ->
            embeddedLyricsExtractor.extract(
                input = input,
                mimeType = track.mimeType,
                displayName = track.displayName,
            )
        } ?: (null to null)
    }.getOrElse { null to null }

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
        const val MAX_LRC_BYTES = 2 * 1024 * 1024
    }
}
