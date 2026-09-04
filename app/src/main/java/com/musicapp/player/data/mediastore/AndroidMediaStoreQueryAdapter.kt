package com.musicapp.player.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.musicapp.player.core.media.MediaAudioCandidate

class MediaStoreQueryException internal constructor(
    cause: Exception,
) : Exception("Unable to query the device media library", cause)

interface MediaStoreQueryAdapter {
    @Throws(MediaStoreQueryException::class)
    fun queryAudio(): List<MediaAudioCandidate>

    @Throws(MediaStoreQueryException::class)
    fun queryAudioWithReport(): MediaStoreQueryResult = MediaStoreQueryResult(queryAudio(), emptyList())
}

data class MediaStoreQueryResult(
    val candidates: List<MediaAudioCandidate>,
    val unreadableDisplayNames: List<String?>,
)

class AndroidMediaStoreQueryAdapter internal constructor(
    private val contentResolver: ContentResolver,
    private val apiLevel: Int,
    private val externalVolumeNamesProvider: () -> Set<String>,
    private val legacyStorageRootsProvider: () -> Set<String>,
) : MediaStoreQueryAdapter {
    constructor(context: Context) : this(
        contentResolver = context.contentResolver,
        apiLevel = Build.VERSION.SDK_INT,
        externalVolumeNamesProvider = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                emptySet()
            }
        },
        legacyStorageRootsProvider = {
            @Suppress("DEPRECATION")
            setOf(Environment.getExternalStorageDirectory().absolutePath)
        },
    )

    override fun queryAudio(): List<MediaAudioCandidate> = queryAudioWithReport().candidates

    override fun queryAudioWithReport(): MediaStoreQueryResult =
        try {
            val spec = MediaStoreQuerySpec.forApiLevel(apiLevel)
            val legacyStorageRoots =
                if (apiLevel < 29) legacyStorageRootsProvider() else emptySet()
            val results = queryTargets().map { target ->
                val cursor =
                    contentResolver.query(
                        target.uri,
                        spec.projection.toTypedArray(),
                        spec.selection,
                        spec.selectionArgs,
                        spec.sortOrder,
                    ) ?: throw IllegalStateException("MediaStore returned no cursor")
                cursor.use {
                    it.readRows(target.volumeName, spec.pathColumn, legacyStorageRoots)
                }
            }
            MediaStoreQueryResult(
                candidates = results.flatMap(ReadRowsResult::candidates),
                unreadableDisplayNames = results.flatMap(ReadRowsResult::unreadableDisplayNames),
            )
        } catch (exception: Exception) {
            if (exception is MediaStoreQueryException) throw exception
            throw MediaStoreQueryException(exception)
        }

    private fun queryTargets(): List<QueryTarget> =
        if (apiLevel >= 29) {
            externalVolumeNamesProvider()
                .sorted()
                .map { volumeName ->
                    QueryTarget(
                        volumeName = volumeName,
                        uri = MediaStore.Audio.Media.getContentUri(volumeName),
                    )
                }
        } else {
            listOf(
                QueryTarget(
                    volumeName = LEGACY_EXTERNAL_VOLUME_NAME,
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ),
            )
        }

    private fun Cursor.readRows(
        volumeName: String,
        pathColumn: String,
        legacyStorageRoots: Set<String>,
    ): ReadRowsResult {
        val columns = Columns(this, pathColumn)
        val candidates = mutableListOf<MediaAudioCandidate>()
        val unreadableDisplayNames = mutableListOf<String?>()
        while (moveToNext()) {
            val candidate = readCandidateOrNull(volumeName, columns, legacyStorageRoots)
            if (candidate == null) {
                unreadableDisplayNames += runCatching { columns.displayName.stringOrNull() }.getOrNull()
            } else {
                candidates += candidate
            }
        }
        return ReadRowsResult(candidates, unreadableDisplayNames)
    }

    private fun readCandidateOrNull(
        volumeName: String,
        columns: Columns,
        legacyStorageRoots: Set<String>,
    ): MediaAudioCandidate? =
        try {
            val relativeDirectory =
                if (apiLevel >= 29) {
                    normalizeRelativeDirectory(columns.path.stringOrNull())
                } else {
                    normalizeLegacyDirectory(
                        absoluteFilePath = columns.path.stringOrNull(),
                        storageRoots = legacyStorageRoots,
                    )
                }
            val (discNumber, trackNumber) = parseTrackAndDiscNumber(columns.track?.intOrNull())
            val releaseYear = columns.year?.intOrNull()?.takeIf { it in 1000..9999 }
            MediaAudioCandidate(
                volumeName = volumeName,
                mediaStoreId = columns.id.long(),
                title = columns.title.stringOrNull(),
                artistName = columns.artist.stringOrNull(),
                artistId = columns.artistId.longOrNull(),
                albumTitle = columns.album.stringOrNull(),
                albumId = columns.albumId.longOrNull(),
                durationMs = columns.duration.long(),
                dateAddedMs = secondsToMilliseconds(columns.dateAdded.long()),
                dateModifiedMs = secondsToMilliseconds(columns.dateModified.long()),
                relativeDirectory = relativeDirectory,
                displayName = columns.displayName.stringOrNull().orEmpty(),
                mimeType = columns.mimeType.stringOrNull(),
                sizeBytes = columns.size.long(),
                isRingtone = columns.isRingtone.boolean(),
                isAlarm = columns.isAlarm.boolean(),
                isNotification = columns.isNotification.boolean(),
                trackNumber = trackNumber,
                discNumber = discNumber,
                releaseYear = releaseYear,
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        }

    private data class QueryTarget(
        val volumeName: String,
        val uri: android.net.Uri,
    )

    private data class ReadRowsResult(
        val candidates: List<MediaAudioCandidate>,
        val unreadableDisplayNames: List<String?>,
    )

    private class Columns(cursor: Cursor, pathColumn: String) {
        val id = cursor.column(MediaStore.Audio.Media._ID)
        val title = cursor.column(MediaStore.Audio.Media.TITLE)
        val artist = cursor.column(MediaStore.Audio.Media.ARTIST)
        val artistId = cursor.column(MediaStore.Audio.Media.ARTIST_ID)
        val album = cursor.column(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.column(MediaStore.Audio.Media.ALBUM_ID)
        val duration = cursor.column(MediaStore.Audio.Media.DURATION)
        val dateAdded = cursor.column(MediaStore.Audio.Media.DATE_ADDED)
        val dateModified = cursor.column(MediaStore.Audio.Media.DATE_MODIFIED)
        val path = cursor.column(pathColumn)
        val displayName = cursor.column(MediaStore.Audio.Media.DISPLAY_NAME)
        val mimeType = cursor.column(MediaStore.Audio.Media.MIME_TYPE)
        val size = cursor.column(MediaStore.Audio.Media.SIZE)
        val isRingtone = cursor.column(MediaStore.Audio.Media.IS_RINGTONE)
        val isAlarm = cursor.column(MediaStore.Audio.Media.IS_ALARM)
        val isNotification = cursor.column(MediaStore.Audio.Media.IS_NOTIFICATION)
        val track = cursor.columnOrNull(MediaStore.Audio.Media.TRACK)
        val year = cursor.columnOrNull(MediaStore.Audio.Media.YEAR)
    }
}

private data class CursorColumn(
    val cursor: Cursor,
    val index: Int,
) {
    fun long(): Long = cursor.getLong(index)

    fun longOrNull(): Long? = if (cursor.isNull(index)) null else cursor.getLong(index)

    fun intOrNull(): Int? = if (cursor.isNull(index)) null else cursor.getInt(index)

    fun stringOrNull(): String? = if (cursor.isNull(index)) null else cursor.getString(index)

    fun boolean(): Boolean = cursor.getInt(index) != 0
}

private fun Cursor.column(name: String): CursorColumn =
    CursorColumn(this, getColumnIndexOrThrow(name))

private fun Cursor.columnOrNull(name: String): CursorColumn? {
    val index = getColumnIndex(name)
    return if (index >= 0) CursorColumn(this, index) else null
}

internal fun parseTrackAndDiscNumber(rawTrack: Int?): Pair<Int?, Int?> {
    if (rawTrack == null || rawTrack <= 0) return Pair(null, null)
    return if (rawTrack >= 1000) {
        val disc = rawTrack / 1000
        val track = rawTrack % 1000
        Pair(disc.takeIf { it > 0 }, track.takeIf { it > 0 })
    } else {
        Pair(null, rawTrack)
    }
}

private fun secondsToMilliseconds(seconds: Long): Long =
    try {
        Math.multiplyExact(seconds, 1_000L)
    } catch (_: ArithmeticException) {
        if (seconds >= 0) Long.MAX_VALUE else Long.MIN_VALUE
    }

internal fun normalizeRelativeDirectory(path: String?): String =
    normalizeSegments(path.orEmpty())

internal fun normalizeLegacyDirectory(
    absoluteFilePath: String?,
    storageRoots: Set<String>,
): String {
    val normalizedFilePath = normalizeAbsolutePath(absoluteFilePath) ?: return ""
    val directory = normalizedFilePath.substringBeforeLast('/', missingDelimiterValue = "")
    val matchingRoot =
        storageRoots
            .mapNotNull(::normalizeAbsolutePath)
            .filter { directory == it || directory.startsWith("$it/") }
            .maxByOrNull(String::length)
    if (matchingRoot != null) {
        return normalizeSegments(directory.removePrefix(matchingRoot))
    }

    val knownRootPattern =
        Regex(
            pattern = "^/(?:storage/(?:emulated/\\d+|self/primary|[^/]+)|mnt/media_rw/[^/]+|mnt/sdcard|sdcard|data/media/\\d+)(?:/|$)",
        )
    val withoutKnownRoot = directory.replaceFirst(knownRootPattern, "")
    return if (withoutKnownRoot == directory) "" else normalizeSegments(withoutKnownRoot)
}

private fun normalizeAbsolutePath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val slashPath = path.replace('\\', '/')
    if (!slashPath.startsWith('/')) return null
    return "/${normalizeSegments(slashPath)}"
}

private fun normalizeSegments(path: String): String {
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

private const val LEGACY_EXTERNAL_VOLUME_NAME = "external"
