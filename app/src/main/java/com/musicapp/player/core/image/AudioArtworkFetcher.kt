package com.musicapp.player.core.image

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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
        const val MAX_CONCURRENT_READS = 2
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

    /**
     * Extracts artwork for a requested rendition while keeping existing two-argument test
     * implementations source-compatible. Custom extractors that do not distinguish renditions
     * continue to use their existing extraction behavior.
     */
    suspend fun extract(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): ByteArray? = extract(context, uri)

    /**
     * Optionally returns a decoded bitmap for thumbnail renditions to avoid a PNG round trip.
     * Extractors that only support byte-based results keep the existing behavior by returning null.
     */
    suspend fun extractBitmap(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): Bitmap? = null

    /**
     * Extracts bytes after the direct bitmap path is unavailable. The default keeps custom
     * extractors source-compatible by delegating to their existing rendition-aware extraction.
     */
    suspend fun extractAfterBitmap(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): ByteArray? = extract(context, uri, rendition)
}

/**
 * Extraction order is kept explicit so list thumbnails can avoid opening the full embedded
 * picture on Android Q+, while full-size requests retain the existing high-resolution path.
 */
internal enum class ArtworkReadPath {
    THUMBNAIL,
    EMBEDDED,
}

internal fun artworkReadOrder(
    rendition: ArtworkRendition,
    apiLevel: Int,
): List<ArtworkReadPath> = when (rendition) {
    ArtworkRendition.LIST_THUMBNAIL,
    ArtworkRendition.GRID_THUMBNAIL,
    ->
        if (apiLevel >= Build.VERSION_CODES.Q) {
            listOf(ArtworkReadPath.THUMBNAIL, ArtworkReadPath.EMBEDDED)
        } else {
            listOf(ArtworkReadPath.EMBEDDED)
        }

    ArtworkRendition.FULL_SIZE ->
        if (apiLevel >= Build.VERSION_CODES.Q) {
            listOf(ArtworkReadPath.EMBEDDED, ArtworkReadPath.THUMBNAIL)
        } else {
            listOf(ArtworkReadPath.EMBEDDED)
        }
}

internal const val LIST_THUMBNAIL_PX = 256
internal const val GRID_THUMBNAIL_PX = 512
private const val FULL_SIZE_FALLBACK_THUMBNAIL_PX = 1024

private fun extractEmbeddedArtwork(context: Context, uri: Uri): ByteArray? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val picture = retriever.embeddedPicture
        if (picture != null && picture.isNotEmpty()) {
            return picture
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        // Fall through to the next extraction path.
    } finally {
        try {
            retriever.release()
        } catch (_: Throwable) {
            // Defensively suppress native release exceptions
        }
    }
    return null
}

private fun extractThumbnailBitmap(
    context: Context,
    uri: Uri,
    sizePx: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

    val cancellationSignal = CancellationSignal()
    return try {
        context.contentResolver.loadThumbnail(
            uri,
            Size(sizePx, sizePx),
            cancellationSignal,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    } finally {
        cancellationSignal.cancel()
    }
}

private fun extractThumbnailArtwork(
    context: Context,
    uri: Uri,
    sizePx: Int,
): ByteArray? {
    val bitmap = extractThumbnailBitmap(context, uri, sizePx) ?: return null
    val stream = ByteArrayOutputStream()
    return if (bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)) {
        stream.toByteArray().takeIf { it.isNotEmpty() }
    } else {
        null
    }
}

private fun extractArtwork(
    context: Context,
    uri: Uri,
    rendition: ArtworkRendition,
    includeThumbnail: Boolean = true,
): ByteArray? {
    val thumbnailSize = when (rendition) {
        ArtworkRendition.LIST_THUMBNAIL -> LIST_THUMBNAIL_PX
        ArtworkRendition.GRID_THUMBNAIL -> GRID_THUMBNAIL_PX
        ArtworkRendition.FULL_SIZE -> FULL_SIZE_FALLBACK_THUMBNAIL_PX
    }
    for (path in artworkReadOrder(rendition, Build.VERSION.SDK_INT)) {
        if (!includeThumbnail && path == ArtworkReadPath.THUMBNAIL) continue
        val bytes = when (path) {
            ArtworkReadPath.THUMBNAIL -> extractThumbnailArtwork(context, uri, thumbnailSize)
            ArtworkReadPath.EMBEDDED -> extractEmbeddedArtwork(context, uri)
        }
        if (bytes != null && bytes.isNotEmpty()) return bytes
    }
    return null
}

/**
 * Default [ArtworkExtractor] using a 256px ContentResolver thumbnail for list requests and
 * preserving the embedded-picture-first full-size path with a 1024px fallback.
 */
val DefaultArtworkExtractor = object : ArtworkExtractor {
    override suspend fun extract(context: Context, uri: Uri): ByteArray? =
        extractArtwork(context, uri, ArtworkRendition.FULL_SIZE)

    override suspend fun extract(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): ByteArray? = extractArtwork(context, uri, rendition)

    override suspend fun extractAfterBitmap(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): ByteArray? = extractArtwork(context, uri, rendition, includeThumbnail = false)

    override suspend fun extractBitmap(
        context: Context,
        uri: Uri,
        rendition: ArtworkRendition,
    ): Bitmap? {
        val thumbnailSize = when (rendition) {
            ArtworkRendition.LIST_THUMBNAIL -> LIST_THUMBNAIL_PX
            ArtworkRendition.GRID_THUMBNAIL -> GRID_THUMBNAIL_PX
            ArtworkRendition.FULL_SIZE -> return null
        }
        return extractThumbnailBitmap(context, uri, thumbnailSize)
    }
}

/**
 * Coil 3 Fetcher for extracting embedded audio artwork (ID3 / FLAC / MP4 APIC) from local media files.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val trackId: TrackId?,
    private val rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
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
        rendition = ArtworkRendition.FULL_SIZE,
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
        rendition = data.rendition,
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
                    if (rendition != ArtworkRendition.FULL_SIZE) {
                        val bitmap = try {
                            extractor.extractBitmap(context, uri, rendition)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            null
                        }
                        bitmap?.let {
                            return@withPermit ImageFetchResult(
                                image = it.asImage(),
                                isSampled = true,
                                dataSource = DataSource.MEMORY,
                            )
                        }
                    }
                    val pictureBytes = if (rendition == ArtworkRendition.FULL_SIZE) {
                        extractor.extract(context, uri, rendition)
                    } else {
                        extractor.extractAfterBitmap(context, uri, rendition)
                    }
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
                rendition = data.rendition,
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
