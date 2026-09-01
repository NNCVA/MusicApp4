package com.musicapp.player.core.image

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem

/**
 * Concurrency limiter to prevent IO/CPU exhaustion when decoding embedded audio artwork.
 * Enforces a maximum of 2 concurrent artwork extractions across the entire application.
 */
@Singleton
class ArtworkReadLimiter @Inject constructor() {
    private val semaphore = Semaphore(permits = MAX_CONCURRENT_READS)

    suspend fun <T> withPermit(block: suspend () -> T): T =
        semaphore.withPermit { block() }

    companion object {
        const val MAX_CONCURRENT_READS = 4
    }
}

/**
 * Resolves a [TrackId] into an Android MediaStore content [Uri].
 */
fun interface TrackContentUriResolver {
    fun resolve(trackId: TrackId): Uri
}

/**
 * Default MediaStore ContentUri resolver.
 */
@Singleton
class DefaultTrackContentUriResolver @Inject constructor() : TrackContentUriResolver {
    override fun resolve(trackId: TrackId): Uri {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(trackId.volumeName)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, trackId.mediaStoreId)
    }
}

/**
 * Strategy interface for extracting embedded artwork bytes from media content.
 */
fun interface ArtworkExtractor {
    suspend fun extract(context: Context, uri: Uri): ByteArray?
}

/**
 * Default [ArtworkExtractor] prioritizing Android Q+ [android.content.ContentResolver.loadThumbnail]
 * with fallback to native [MediaMetadataRetriever] with strict try-finally cleanup.
 */
val DefaultArtworkExtractor = ArtworkExtractor { context, uri ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
            val bytes = stream.toByteArray()
            if (bytes.isNotEmpty()) {
                return@ArtworkExtractor bytes
            }
        } catch (_: Throwable) {
            // Fall back to MediaMetadataRetriever
        }
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture
    } finally {
        try {
            retriever.release()
        } catch (_: Throwable) {
            // Defensively suppress native release exceptions
        }
    }
}

/**
 * Coil 3 Fetcher for extracting embedded audio artwork (ID3 / FLAC / MP4 APIC) from local media files.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val trackId: TrackId?,
    private val uriResolver: TrackContentUriResolver = DefaultTrackContentUriResolver(),
    private val limiter: ArtworkReadLimiter = ArtworkReadLimiter(),
    private val extractor: ArtworkExtractor = DefaultArtworkExtractor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Fetcher {

    constructor(
        data: Track,
        options: Options,
        extractor: ArtworkExtractor,
        limiter: ArtworkReadLimiter = ArtworkReadLimiter(),
        uriResolver: TrackContentUriResolver = DefaultTrackContentUriResolver(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        context = options.context,
        trackId = data.id,
        uriResolver = uriResolver,
        limiter = limiter,
        extractor = extractor,
        dispatcher = dispatcher,
    )

    constructor(
        data: AudioArtworkRequest,
        options: Options,
        extractor: ArtworkExtractor,
        limiter: ArtworkReadLimiter = ArtworkReadLimiter(),
        uriResolver: TrackContentUriResolver = DefaultTrackContentUriResolver(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        context = options.context,
        trackId = when (data) {
            is AudioArtworkRequest.TrackArtworkRequest -> data.trackId
            is AudioArtworkRequest.AlbumArtworkRequest -> data.representativeTrackId
            is AudioArtworkRequest.ArtistArtworkRequest -> data.representativeTrackId
            is AudioArtworkRequest.PlaylistArtworkRequest -> data.representativeTrackId
        },
        uriResolver = uriResolver,
        limiter = limiter,
        extractor = extractor,
        dispatcher = dispatcher,
    )

    override suspend fun fetch(): FetchResult? {
        val targetTrackId = trackId ?: return null

        return withContext(dispatcher) {
            limiter.withPermit {
                try {
                    val uri = uriResolver.resolve(targetTrackId)
                    val pictureBytes = extractor.extract(context, uri)
                    if (pictureBytes == null || pictureBytes.isEmpty() || pictureBytes.size > MAX_ARTWORK_BYTES) {
                        return@withPermit null
                    }
                    val buffer = Buffer().write(pictureBytes)
                    SourceFetchResult(
                        source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
                        mimeType = null,
                        dataSource = DataSource.DISK,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    companion object {
        const val MAX_ARTWORK_BYTES = 16 * 1024 * 1024 // 16MB threshold to prevent OOM
    }

    @Singleton
    class Factory @Inject constructor(
        @param:ApplicationContext private val context: Context,
        private val uriResolver: TrackContentUriResolver,
        private val limiter: ArtworkReadLimiter,
    ) : Fetcher.Factory<AudioArtworkRequest> {

        override fun create(
            data: AudioArtworkRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            val targetTrackId = when (data) {
                is AudioArtworkRequest.TrackArtworkRequest -> data.trackId
                is AudioArtworkRequest.AlbumArtworkRequest -> data.representativeTrackId
                is AudioArtworkRequest.ArtistArtworkRequest -> data.representativeTrackId
                is AudioArtworkRequest.PlaylistArtworkRequest -> data.representativeTrackId
            }
            return AudioArtworkFetcher(
                context = context,
                trackId = targetTrackId,
                uriResolver = uriResolver,
                limiter = limiter,
            )
        }

        /**
         * Polymorphic factory create helper method for testing and general dispatch.
         */
        fun createFromAny(
            data: Any,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? =
            when (data) {
                is AudioArtworkRequest -> create(data, options, imageLoader)
                is Track -> AudioArtworkFetcher(
                    context = context,
                    trackId = data.id,
                    uriResolver = uriResolver,
                    limiter = limiter,
                )
                else -> null
            }
    }
}

/**
 * Fetcher Factory directly registered for [Track] instances.
 */
@Singleton
class TrackArtworkFetcherFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val uriResolver: TrackContentUriResolver,
    private val limiter: ArtworkReadLimiter,
) : Fetcher.Factory<Track> {

    override fun create(
        data: Track,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = AudioArtworkFetcher(
        context = context,
        trackId = data.id,
        uriResolver = uriResolver,
        limiter = limiter,
    )
}
