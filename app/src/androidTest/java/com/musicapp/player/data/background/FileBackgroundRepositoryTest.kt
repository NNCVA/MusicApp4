package com.musicapp.player.data.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileBackgroundRepositoryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var directory: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.filesDir, "background-test-${UUID.randomUUID()}").apply { mkdirs() }
        directory = File(root, "background")
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        root.deleteRecursively()
    }

    @Test
    fun importedImageSurvivesSourceDeletionAndRepositoryRestart() = runTest {
        val source = createImage()
        val repository = repository()
        repository.importImage(Uri.fromFile(source))
        val selected = repository.state.value
        assertTrue(source.delete())

        val restored = repository()
        assertEquals(selected, restored.state.value)
        assertTrue(File(checkNotNull(selected.imagePath)).isFile)
        assertNoTemporaryFiles()
    }

    @Test
    fun successfulReplacementRemovesOldImageAndStaleFailureCannotInvalidateNewImage() = runTest {
        val repository = repository()
        val source = createImage()
        repository.importImage(Uri.fromFile(source))
        val firstPath = checkNotNull(repository.state.value.imagePath)
        repository.importImage(Uri.fromFile(source))
        val replaced = repository.state.value
        repository.markUnavailable(firstPath)

        assertNotEquals(firstPath, replaced.imagePath)
        assertFalse(File(firstPath).exists())
        assertEquals(replaced, repository.state.value)
        assertEquals(replaced, repository().state.value)
        assertNoTemporaryFiles()
    }

    @Test
    fun invalidInputAndMissingUriRetainOldImageWithoutOrphanFiles() = runTest {
        val repository = repository()
        repository.importImage(Uri.fromFile(createImage()))
        val previous = repository.state.value
        val malformed = File(root, "invalid.png").apply { writeText("invalid image") }
        listOf(malformed, File(root, "missing.png")).forEach { input ->
            val failure = runCatching { repository.importImage(Uri.fromFile(input)) }.exceptionOrNull()
            assertTrue(failure is BackgroundImportException)
            assertEquals(previous, repository.state.value)
            assertEquals(previous, repository().state.value)
            assertNoTemporaryFiles()
        }
    }

    @Test
    fun processingIoFailureAndOomRetainOldImageAndCleanPartialOutput() = runTest {
        val source = createImage()
        val initial = repository()
        initial.importImage(Uri.fromFile(source))
        val previous = initial.state.value
        listOf<Throwable>(IOException("disk full"), OutOfMemoryError("allocation rejected")).forEach { failure ->
            val repository = repository(BackgroundImageProcessor { _, output ->
                output.writeText("partial output")
                throw failure
            })
            val result = runCatching { repository.importImage(Uri.fromFile(source)) }.exceptionOrNull()
            assertTrue(result is BackgroundImportException)
            assertEquals(previous, repository.state.value)
            assertEquals(previous, repository().state.value)
            assertNoTemporaryFiles()
        }
    }

    @Test
    fun cancelledProcessingRetainsOldSelectionAndCleansPartialOutput() = runTest {
        val source = createImage()
        val initial = repository()
        initial.importImage(Uri.fromFile(source))
        val previous = initial.state.value
        val started = CompletableDeferred<Unit>()
        val repository = repository(BackgroundImageProcessor { _, output ->
            output.writeText("partial output")
            started.complete(Unit)
            awaitCancellation()
        })
        val importJob = launch { repository.importImage(Uri.fromFile(source)) }
        started.await()
        importJob.cancelAndJoin()

        assertEquals(previous, repository.state.value)
        assertEquals(previous, repository().state.value)
        assertNoTemporaryFiles()
    }

    @Test
    fun missingAndCorruptedImagesRestoreUnavailableState() = runTest {
        val source = createImage()
        val missing = repository()
        missing.importImage(Uri.fromFile(source))
        assertTrue(File(checkNotNull(missing.state.value.imagePath)).delete())
        assertTrue(repository().state.value.isUnavailable)

        val corrupt = repository()
        corrupt.importImage(Uri.fromFile(source))
        File(checkNotNull(corrupt.state.value.imagePath)).appendText("unexpected bytes")
        val restored = repository()
        assertTrue(restored.state.value.isUnavailable)
        assertEquals(BackgroundState(hasSelection = true), restored.state.value)
        assertNoTemporaryFiles()
    }

    @Test
    fun activeImageFailureIsPersistedAndDeleteRestoresEmptySelection() = runTest {
        val repository = repository()
        repository.importImage(Uri.fromFile(createImage()))
        val path = checkNotNull(repository.state.value.imagePath)
        repository.markUnavailable(path)
        assertEquals(BackgroundState(hasSelection = true), repository.state.value)
        assertFalse(File(path).exists())
        assertTrue(repository().state.value.isUnavailable)

        repository.deleteImage()
        assertEquals(BackgroundState(), repository.state.value)
        assertEquals(BackgroundState(), repository().state.value)
        assertNoTemporaryFiles()
    }

    @Test
    fun damagedMetadataFallsBackAndUnreferencedFilesAreRemovedOnStartup() = runTest {
        directory.mkdirs()
        File(directory, "selection").writeText("invalid record")
        File(directory, "background-orphan.png").writeText("orphan")
        File(directory, ".pending-interrupted.png").writeText("partial")
        val restored = repository()

        assertTrue(restored.state.value.isUnavailable)
        assertEquals(listOf("selection"), directory.listFiles()?.map { it.name })
        restored.deleteImage()
        assertEquals(BackgroundState(), repository().state.value)
    }

    @Test
    fun exifRotationAndMirroringAreAppliedToStoredPixels() = runTest {
        listOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE).forEach { orientation ->
            val source = createImage(width = 4, height = 2)
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val repository = repository()
            repository.importImage(Uri.fromFile(source))
            val bitmap = BitmapFactory.decodeFile(repository.state.value.imagePath)
            assertNotNull(bitmap)
            try {
                assertEquals(2, bitmap.width)
                assertEquals(4, bitmap.height)
                val expectedRedX = if (orientation == ExifInterface.ORIENTATION_ROTATE_90) 1 else 0
                assertEquals(Color.RED, bitmap.getPixel(expectedRedX, 0))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun largeSourceIsDownsampledWhileKeepingAspectRatio() = runTest {
        val source = createImage(width = 5200, height = 260)
        val repository = repository()
        repository.importImage(Uri.fromFile(source))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(repository.state.value.imagePath, bounds)

        assertTrue(bounds.outWidth <= BackgroundImportPolicy.MAX_LONG_EDGE)
        assertEquals(20, bounds.outWidth / bounds.outHeight)
        assertNoTemporaryFiles()
    }

    @Test
    fun animatedGifIsImportedAsItsStaticFirstFrame() = runTest {
        // A generated 1 x 1 GIF with a red first frame and a blue second frame.
        val source = File(root, "animated.gif").apply {
            writeBytes(Base64.getDecoder().decode("R0lGODlhAQABAIAAAP8AAAAA/yH5BAAKAAAALAAAAAABAAEAAAICRAEAIfkEAAoAAAAsAAAAAAEAAQAAAgJMAQA7"))
        }
        val repository = repository()
        repository.importImage(Uri.fromFile(source))
        val bitmap = BitmapFactory.decodeFile(repository.state.value.imagePath)
        try {
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertTrue(checkNotNull(repository.state.value.imagePath).endsWith(".png"))
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun repository(processor: BackgroundImageProcessor = AndroidBackgroundImageProcessor()): FileBackgroundRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return FileBackgroundRepository(context.contentResolver, directory, scope, processor = processor).also {
            // A nonmatching report is a no-op, and waits for startup validation to finish.
            it.markUnavailable("not-a-current-image")
        }
    }

    private fun createImage(width: Int = 40, height: Int = 20): File {
        val source = File(root, "source-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            bitmap.setPixel(0, 0, Color.RED)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return source
    }

    private fun assertNoTemporaryFiles() {
        val files = directory.listFiles().orEmpty()
        assertTrue(files.none { it.name.startsWith(".") })
        assertEquals(1, files.count { it.name == "selection" })
        assertTrue(files.count { it.name.startsWith("background-") } <= 1)
    }
}
