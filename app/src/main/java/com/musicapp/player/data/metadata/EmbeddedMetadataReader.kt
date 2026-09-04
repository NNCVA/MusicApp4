package com.musicapp.player.data.metadata

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class EmbeddedMetadataPayload(
    val encoding: String?,
    val bitrateBps: Long?,
    val sampleRateHz: Int?,
    val bitDepth: Int? = null,
)

internal sealed interface PlatformArtworkResult {
    data class Present(val image: ArtworkImage) : PlatformArtworkResult

    data object Missing : PlatformArtworkResult
}

internal interface EmbeddedMetadataReader {
    suspend fun readMetadata(trackId: TrackId): EmbeddedMetadataPayload

    suspend fun readArtwork(trackId: TrackId, targetPx: Int): PlatformArtworkResult
}

internal interface TrackContentUriResolver {
    fun resolve(trackId: TrackId): Uri
}

@Singleton
internal class AndroidTrackContentUriResolver @Inject constructor() : TrackContentUriResolver {
    override fun resolve(trackId: TrackId): Uri {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(trackId.volumeName)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        return ContentUris.withAppendedId(collection, trackId.mediaStoreId)
    }
}

@Singleton
internal class AndroidEmbeddedMetadataReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val uriResolver: TrackContentUriResolver,
) : EmbeddedMetadataReader {
    override suspend fun readMetadata(trackId: TrackId): EmbeddedMetadataPayload =
        withContext(Dispatchers.IO) {
            val uri = uriResolver.resolve(trackId)
            val retrieverValues = readRetrieverValues(uri)
            ensureActive()
            val extractorValues = readExtractorValues(uri)
            if (!retrieverValues.opened && !extractorValues.opened) {
                throw extractorValues.failure ?: retrieverValues.failure ?: IllegalStateException("Unreadable media")
            }
            EmbeddedMetadataPayload(
                encoding = extractorValues.encoding ?: retrieverValues.encoding,
                bitrateBps = extractorValues.bitrateBps ?: retrieverValues.bitrateBps,
                sampleRateHz = extractorValues.sampleRateHz,
                bitDepth = extractorValues.bitDepth ?: retrieverValues.bitDepth,
            )
        }

    override suspend fun readArtwork(trackId: TrackId, targetPx: Int): PlatformArtworkResult =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uriResolver.resolve(trackId))
                ensureActive()
                val encoded = retriever.embeddedPicture ?: return@withContext PlatformArtworkResult.Missing
                decodeArtwork(encoded, targetPx)
            } finally {
                retriever.release()
            }
        }

    private fun readRetrieverValues(uri: Uri): RetrieverValues {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            RetrieverValues(
                opened = true,
                encoding =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf(String::isNotBlank),
                bitrateBps =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 },
                bitDepth =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    } else {
                        null
                    },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            RetrieverValues(opened = false, failure = exception)
        } finally {
            retriever.release()
        }
    }

    private fun readExtractorValues(uri: Uri): ExtractorValues {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val audioFormat = extractor.firstAudioFormat()
            ExtractorValues(
                opened = true,
                encoding = audioFormat?.optionalString(MediaFormat.KEY_MIME),
                bitrateBps = audioFormat?.optionalLong(MediaFormat.KEY_BIT_RATE),
                sampleRateHz = audioFormat?.optionalInt(MediaFormat.KEY_SAMPLE_RATE),
                bitDepth =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        audioFormat?.optionalInt("bits-per-sample")
                    } else {
                        null
                    },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ExtractorValues(opened = false, failure = exception)
        } finally {
            extractor.release()
        }
    }

    private fun decodeArtwork(encoded: ByteArray, targetPx: Int): PlatformArtworkResult {
        if (encoded.isEmpty() || encoded.size > MAX_EMBEDDED_ARTWORK_BYTES) return PlatformArtworkResult.Missing
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return PlatformArtworkResult.Missing
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
            ?: return PlatformArtworkResult.Missing
        return try {
            val pixelCount = bitmap.width.toLong() * bitmap.height
            if (pixelCount > MAX_DECODED_PIXELS) return PlatformArtworkResult.Missing
            val pixels = IntArray(pixelCount.toInt())
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PlatformArtworkResult.Present(ArtworkImage(bitmap.width, bitmap.height, pixels))
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > targetPx && sample <= Int.MAX_VALUE / 2) {
            sample *= 2
        }
        return sample
    }

    private fun MediaExtractor.firstAudioFormat(): MediaFormat? {
        for (index in 0 until trackCount) {
            val format = getTrackFormat(index)
            if (format.optionalString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return format
        }
        return null
    }

    private fun MediaFormat.optionalString(key: String): String? =
        if (containsKey(key)) getString(key)?.takeIf(String::isNotBlank) else null

    private fun MediaFormat.optionalInt(key: String): Int? =
        if (containsKey(key)) getInteger(key).takeIf { it > 0 } else null

    private fun MediaFormat.optionalLong(key: String): Long? =
        if (containsKey(key)) {
            runCatching { getInteger(key).toLong() }
                .recoverCatching { getLong(key) }
                .getOrNull()
                ?.takeIf { it > 0 }
        } else {
            null
        }

    private data class RetrieverValues(
        val opened: Boolean,
        val encoding: String? = null,
        val bitrateBps: Long? = null,
        val bitDepth: Int? = null,
        val failure: Exception? = null,
    )

    private data class ExtractorValues(
        val opened: Boolean,
        val encoding: String? = null,
        val bitrateBps: Long? = null,
        val sampleRateHz: Int? = null,
        val bitDepth: Int? = null,
        val failure: Exception? = null,
    )

    private companion object {
        const val MAX_EMBEDDED_ARTWORK_BYTES = 16 * 1024 * 1024
        const val MAX_DECODED_PIXELS = 4_194_304L
    }
}
