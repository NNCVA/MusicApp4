package com.musicapp.player.data.mediastore

import android.content.ContentProvider
import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreQuerySpecTest {
    @Test
    fun `api 29 projection uses relative path and excludes data`() {
        val spec = MediaStoreQuerySpec.forApiLevel(29)

        assertTrue(MediaStore.MediaColumns.RELATIVE_PATH in spec.projection)
        assertFalse(MediaStore.MediaColumns.DATA in spec.projection)
        assertEquals(MediaStore.MediaColumns.RELATIVE_PATH, spec.pathColumn)
    }

    @Test
    fun `api 28 projection uses data and excludes relative path`() {
        val spec = MediaStoreQuerySpec.forApiLevel(28)

        assertTrue(MediaStore.MediaColumns.DATA in spec.projection)
        assertFalse(MediaStore.MediaColumns.RELATIVE_PATH in spec.projection)
        assertEquals(MediaStore.MediaColumns.DATA, spec.pathColumn)
    }

    @Test
    fun `api 29 queries every external volume and maps relative directories`() {
        val provider = RecordingMediaProvider { uri, projection ->
            audioCursor(
                projection = projection,
                mediaStoreId = if (uri.pathSegments.first() == "external_primary") 11 else 22,
                path = "Music/Live/",
            )
        }
        val adapter = adapter(
            provider = provider,
            apiLevel = 29,
            volumes = setOf("external_primary", "1234-5678"),
        )

        val rows = adapter.queryAudio()

        assertEquals(
            listOf(
                "content://media/1234-5678/audio/media",
                "content://media/external_primary/audio/media",
            ),
            provider.queriedUris.map(Uri::toString),
        )
        provider.projections.forEach { projection ->
            assertTrue(MediaStore.MediaColumns.RELATIVE_PATH in projection)
            assertFalse(MediaStore.MediaColumns.DATA in projection)
        }
        assertEquals(setOf("1234-5678", "external_primary"), rows.map { it.volumeName }.toSet())
        assertEquals(listOf("Music/Live", "Music/Live"), rows.map { it.relativeDirectory })
        assertEquals(listOf(100_000L, 100_000L), rows.map { it.dateAddedMs })
        assertEquals(listOf(200_000L, 200_000L), rows.map { it.dateModifiedMs })
    }

    @Test
    fun `api 28 queries external collection and strips storage root from data`() {
        val provider = RecordingMediaProvider { _, projection ->
            audioCursor(
                projection = projection,
                mediaStoreId = 31,
                path = "/storage/emulated/0/Music/Live/track.flac",
            )
        }
        val adapter = adapter(
            provider = provider,
            apiLevel = 28,
            volumes = emptySet(),
            storageRoots = setOf("/storage/emulated/0"),
        )

        val row = adapter.queryAudio().single()

        assertEquals(listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI), provider.queriedUris)
        assertTrue(MediaStore.MediaColumns.DATA in provider.projections.single())
        assertFalse(MediaStore.MediaColumns.RELATIVE_PATH in provider.projections.single())
        assertEquals(MediaStore.VOLUME_EXTERNAL, row.volumeName)
        assertEquals("Music/Live", row.relativeDirectory)
        assertFalse(row.relativeDirectory.startsWith('/'))
    }

    @Test
    fun `cursor closes after successful query`() {
        lateinit var cursor: TrackingMatrixCursor
        val provider = RecordingMediaProvider { _, projection ->
            TrackingMatrixCursor(projection).also {
                cursor = it
                it.addRow(audioValues(projection, 41, "Music/track.mp3"))
            }
        }

        adapter(provider, apiLevel = 29).queryAudio()

        assertTrue(cursor.wasClosed)
    }

    @Test
    fun `invalid row is skipped without discarding valid rows`() {
        val provider = RecordingMediaProvider { _, projection ->
            MatrixCursor(projection).apply {
                addRow(audioValues(projection, 61, "Music/first.mp3"))
                addRow(audioValues(projection, 0, "Music/broken.mp3"))
                addRow(audioValues(projection, 62, "Music/second.mp3"))
            }
        }

        val rows = adapter(provider, apiLevel = 29).queryAudio()

        assertEquals(listOf(61L, 62L), rows.map { it.mediaStoreId })
    }

    @Test
    fun `cursor closes and platform failure is wrapped when row cannot be read`() {
        lateinit var cursor: TrackingMatrixCursor
        val provider = RecordingMediaProvider { _, _ ->
            TrackingMatrixCursor(arrayOf(MediaStore.Audio.Media._ID)).also {
                cursor = it
                it.addRow(arrayOf(51L))
            }
        }

        val failure =
            assertThrows(MediaStoreQueryException::class.java) {
                adapter(provider, apiLevel = 29).queryAudio()
            }

        assertTrue(cursor.wasClosed)
        assertTrue(failure.cause is IllegalArgumentException)
    }

    @Test
    fun `query platform exception is exposed only as adapter exception`() {
        val provider = RecordingMediaProvider { _, _ -> throw SecurityException("denied") }

        val failure =
            assertThrows(MediaStoreQueryException::class.java) {
                adapter(provider, apiLevel = 29).queryAudio()
            }

        assertEquals("Unable to query the device media library", failure.message)
        assertTrue(failure.cause is SecurityException)
    }

    @Test
    fun `legacy normalization never returns an absolute or unknown device path`() {
        assertEquals(
            "Podcasts/Season 1",
            normalizeLegacyDirectory(
                absoluteFilePath = "/storage/ABCD-1234/Podcasts/Season 1/episode.opus",
                storageRoots = emptySet(),
            ),
        )
        assertEquals(
            "",
            normalizeLegacyDirectory(
                absoluteFilePath = "/private/device/root/secret/song.mp3",
                storageRoots = emptySet(),
            ),
        )
    }

    private fun adapter(
        provider: RecordingMediaProvider,
        apiLevel: Int,
        volumes: Set<String> = setOf("external_primary"),
        storageRoots: Set<String> = emptySet(),
    ): AndroidMediaStoreQueryAdapter {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        return AndroidMediaStoreQueryAdapter(
            contentResolver = resolver,
            apiLevel = apiLevel,
            externalVolumeNamesProvider = { volumes },
            legacyStorageRootsProvider = { storageRoots },
        )
    }

    private class RecordingMediaProvider(
        private val cursorFactory: (Uri, Array<String>) -> Cursor,
    ) : ContentProvider() {
        val queriedUris = mutableListOf<Uri>()
        val projections = mutableListOf<List<String>>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val requiredProjection = requireNotNull(projection).map(String::toString).toTypedArray()
            queriedUris += uri
            projections += requiredProjection.toList()
            return cursorFactory(uri, requiredProjection)
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? =
            throw UnsupportedOperationException()

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = throw UnsupportedOperationException()
    }

    private class TrackingMatrixCursor(columns: Array<String>) : MatrixCursor(columns) {
        var wasClosed = false

        override fun close() {
            wasClosed = true
            super.close()
        }
    }

    private companion object {
        fun audioCursor(
            projection: Array<String>,
            mediaStoreId: Long,
            path: String,
        ): Cursor = MatrixCursor(projection).apply {
            addRow(audioValues(projection, mediaStoreId, path))
        }

        fun audioValues(
            projection: Array<String>,
            mediaStoreId: Long,
            path: String,
        ): Array<Any?> =
            projection.map { column ->
                when (column) {
                    MediaStore.Audio.Media._ID -> mediaStoreId
                    MediaStore.Audio.Media.TITLE -> "Track $mediaStoreId"
                    MediaStore.Audio.Media.ARTIST -> "Artist"
                    MediaStore.Audio.Media.ARTIST_ID -> 7L
                    MediaStore.Audio.Media.ALBUM -> "Album"
                    MediaStore.Audio.Media.ALBUM_ID -> 8L
                    MediaStore.Audio.Media.DURATION -> 120_000L
                    MediaStore.Audio.Media.DATE_ADDED -> 100L
                    MediaStore.Audio.Media.DATE_MODIFIED -> 200L
                    MediaStore.Audio.Media.DISPLAY_NAME -> "track.mp3"
                    MediaStore.Audio.Media.MIME_TYPE -> "audio/mpeg"
                    MediaStore.Audio.Media.SIZE -> 4_096L
                    MediaStore.Audio.Media.IS_RINGTONE,
                    MediaStore.Audio.Media.IS_ALARM,
                    MediaStore.Audio.Media.IS_NOTIFICATION,
                    -> 0
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATA,
                    -> path
                    else -> error("Unexpected projection column: $column")
                }
            }.toTypedArray()
    }
}
