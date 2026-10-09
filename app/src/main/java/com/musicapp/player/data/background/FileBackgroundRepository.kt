package com.musicapp.player.data.background

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import android.util.Log
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class FileBackgroundRepository internal constructor(
    private val contentResolver: ContentResolver,
    private val directory: File,
    applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val processor: BackgroundImageProcessor = AndroidBackgroundImageProcessor(),
) : BackgroundRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationCoroutineScope applicationScope: CoroutineScope,
    ) : this(context.contentResolver, File(context.filesDir, DIRECTORY_NAME), applicationScope)

    private val mutableState = MutableStateFlow(BackgroundState())
    override val state: StateFlow<BackgroundState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val metadataFile = File(directory, METADATA_FILE_NAME)
    private val initialization = applicationScope.async(ioDispatcher) {
        mutex.withLock { restoreSelection() }
    }

    override suspend fun importImage(uri: Uri) {
        initialization.await()
        withContext(ioDispatcher) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val source = File(directory, ".source-$id.tmp")
                val pendingImage = File(directory, ".pending-$id.png")
                val image = File(directory, "background-$id.png")
                var published = false
                try {
                    ensureDirectory()
                    copySource(uri, source)
                    processor.process(source, pendingImage)
                    val selection = StoredBackgroundSelection.Ready(image.name, sha256(pendingImage))
                    val nextState = BackgroundState(image.absolutePath, hasSelection = true)
                    currentCoroutineContext().ensureActive()
                    Os.rename(pendingImage.absolutePath, image.absolutePath)
                    // No suspension between the final cancellation check and publication.
                    // The complete new image exists before its metadata can reference it.
                    currentCoroutineContext().ensureActive()
                    writeSelection(selection)
                    published = true
                    mutableState.value = nextState
                    cleanObsoleteFiles(image.name)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    throw BackgroundImportException(failure)
                } catch (failure: OutOfMemoryError) {
                    throw BackgroundImportException(failure)
                } finally {
                    removeFile(source)
                    removeFile(pendingImage)
                    if (!published) removeFile(image)
                }
            }
        }
    }

    override suspend fun deleteImage() {
        initialization.await()
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureDirectory()
                val nextState = BackgroundState()
                currentCoroutineContext().ensureActive()
                writeSelection(StoredBackgroundSelection.None)
                mutableState.value = nextState
                cleanObsoleteFiles(activeFileName = null)
            }
        }
    }

    override suspend fun markUnavailable(imagePath: String) {
        initialization.await()
        withContext(ioDispatcher) {
            mutex.withLock {
                if (mutableState.value.imagePath != imagePath) return@withLock
                currentCoroutineContext().ensureActive()
                mutableState.value = BackgroundState(hasSelection = true)
                persistUnavailable()
                cleanObsoleteFiles(activeFileName = null)
            }
        }
    }

    private suspend fun restoreSelection() {
        try {
            val selection = readSelection()
            val ready = selection as? StoredBackgroundSelection.Ready
            val image = ready?.let { File(directory, it.fileName) }
            val usable = ready != null && image != null && isUsable(image, ready.sha256)
            mutableState.value = when {
                usable -> BackgroundState(image!!.absolutePath, hasSelection = true)
                selection != StoredBackgroundSelection.None -> BackgroundState(hasSelection = true)
                else -> BackgroundState()
            }
            if (selection is StoredBackgroundSelection.Ready && !usable) persistUnavailable()
            cleanObsoleteFiles(image?.name.takeIf { usable })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            restoreUnavailable(failure)
        } catch (failure: OutOfMemoryError) {
            restoreUnavailable(failure)
        }
    }

    private fun restoreUnavailable(failure: Throwable) {
        Log.w(TAG, "Unable to restore background image", failure)
        mutableState.value = BackgroundState(hasSelection = metadataFile.exists())
        if (mutableState.value.hasSelection) persistUnavailable()
        cleanObsoleteFiles(activeFileName = null)
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create background directory")
    }

    private suspend fun copySource(uri: Uri, destination: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    BackgroundImportPolicy.checkInputSize(copied)
                    output.write(buffer, 0, read)
                }
                if (copied == 0L) throw IOException("Empty background image")
            }
        } ?: throw IOException("Unable to open background image")
    }

    private fun readSelection(): StoredBackgroundSelection {
        if (!metadataFile.exists()) return StoredBackgroundSelection.None
        val bytes = metadataFile.inputStream().use { input ->
            val buffer = ByteArray(StoredBackgroundSelection.MAX_METADATA_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
        return StoredBackgroundSelection.decode(bytes)
    }

    private fun writeSelection(selection: StoredBackgroundSelection) {
        val pending = File(directory, ".selection-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(pending).use { output ->
                output.write(selection.encode())
                output.fd.sync()
            }
            // rename(2) switches one complete metadata record atomically and reports write failures.
            Os.rename(pending.absolutePath, metadataFile.absolutePath)
        } finally {
            removeFile(pending)
        }
    }

    private fun persistUnavailable() {
        try {
            writeSelection(StoredBackgroundSelection.Unavailable)
        } catch (failure: Exception) {
            Log.w(TAG, "Unable to persist unavailable background", failure)
        } catch (failure: OutOfMemoryError) {
            Log.w(TAG, "Unable to persist unavailable background", failure)
        }
    }

    private suspend fun isUsable(image: File, expectedDigest: String): Boolean {
        if (!image.isFile || image.length() !in 1..BackgroundImportPolicy.MAX_STORED_BYTES) return false
        if (sha256(image) != expectedDigest) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.absolutePath, bounds)
        if (bounds.outWidth !in 1..BackgroundImportPolicy.MAX_LONG_EDGE || bounds.outHeight !in 1..BackgroundImportPolicy.MAX_LONG_EDGE) {
            return false
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = BackgroundImportPolicy.sampleSize(bounds.outWidth, bounds.outHeight, targetLongEdge = 64)
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeFile(image.absolutePath, options) ?: return false
        bitmap.recycle()
        return true
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun cleanObsoleteFiles(activeFileName: String?) {
        try {
            directory.listFiles()?.forEach { file ->
                if (
                    file.name != activeFileName &&
                    (file.name.startsWith("background-") || file.name.startsWith(".source-") ||
                        file.name.startsWith(".pending-") || file.name.startsWith(".selection-"))
                ) {
                    removeFile(file)
                }
            }
        } catch (failure: Exception) {
            Log.w(TAG, "Unable to clean background files", failure)
        } catch (failure: OutOfMemoryError) {
            Log.w(TAG, "Unable to clean background files", failure)
        }
    }

    private fun removeFile(file: File) {
        try {
            if (file.exists() && !file.delete()) Log.w(TAG, "Unable to remove obsolete background file")
        } catch (failure: Exception) {
            Log.w(TAG, "Unable to remove obsolete background file", failure)
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "background"
        const val METADATA_FILE_NAME = "selection"
        const val TAG = "BackgroundRepository"
    }
}
