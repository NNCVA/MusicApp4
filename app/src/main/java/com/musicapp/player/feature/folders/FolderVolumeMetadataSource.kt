package com.musicapp.player.feature.folders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Platform metadata for one mounted media volume. */
data class FolderVolumeMetadata(
    val volumeName: String,
    val displayName: String?,
    val rootPath: String?,
    val isPrimary: Boolean,
    val usedBytes: Long?,
    val totalBytes: Long?,
) {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(usedBytes == null || usedBytes >= 0) { "usedBytes must be null or non-negative" }
        require(totalBytes == null || totalBytes >= 0) { "totalBytes must be null or non-negative" }
        require(usedBytes == null || totalBytes == null || usedBytes <= totalBytes) {
            "usedBytes must not exceed totalBytes"
        }
    }
}

/** Replaceable boundary for platform storage metadata and mount-change signals. */
fun interface FolderVolumeMetadataSource {
    fun observe(): Flow<List<FolderVolumeMetadata>>
}

/** Empty source used by JVM callers that do not need platform metadata. */
object EmptyFolderVolumeMetadataSource : FolderVolumeMetadataSource {
    override fun observe(): Flow<List<FolderVolumeMetadata>> = kotlinx.coroutines.flow.flowOf(emptyList())
}

@Singleton
class AndroidFolderVolumeMetadataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : FolderVolumeMetadataSource {
    override fun observe(): Flow<List<FolderVolumeMetadata>> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        trySend(snapshot())
                    }
                }
            trySend(snapshot())

            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_MEDIA_MOUNTED)
                    addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                    addAction(Intent.ACTION_MEDIA_EJECT)
                    addAction(Intent.ACTION_MEDIA_REMOVED)
                    addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                    addAction(Intent.ACTION_MEDIA_CHECKING)
                    addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
                    addDataScheme("file")
                }
            val registered =
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.registerReceiver(
                            context,
                            receiver,
                            filter,
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                    } else {
                        @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                        context.registerReceiver(receiver, filter)
                    }
                }.isSuccess
            awaitClose {
                if (registered) {
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }.distinctUntilChanged()

    private fun snapshot(): List<FolderVolumeMetadata> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val storageVolumes = runCatching { storageManager?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        val volumeNames = externalVolumeNames()
        return volumeNames
            .mapNotNull { volumeName -> metadataFor(volumeName, storageVolumes) }
            .sortedBy { it.volumeName }
    }

    private fun externalVolumeNames(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(emptySet())
        } else {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val primary =
                storageManager?.storageVolumes.orEmpty().firstOrNull { it.isPrimary && it.isMounted() }
            if (primary != null) setOf(LEGACY_EXTERNAL_VOLUME_NAME) else emptySet()
        }

    private fun metadataFor(
        volumeName: String,
        storageVolumes: List<StorageVolume>,
    ): FolderVolumeMetadata? {
        val volume = storageVolumes.firstOrNull { it.matches(volumeName) }
        val isPrimary = volume?.isPrimary ?: volumeName.isPrimaryVolumeName()
        val rootPath = volume?.rootPath(isPrimary)
        val capacities = rootPath?.let(::capacityForPath) ?: Capacities(null, null)
        val displayName =
            volume?.let {
                runCatching { it.getDescription(context) }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
            }
        return FolderVolumeMetadata(
            volumeName = volumeName,
            displayName = displayName,
            rootPath = rootPath,
            isPrimary = isPrimary,
            usedBytes = capacities.usedBytes,
            totalBytes = capacities.totalBytes,
        )
    }

    private fun StorageVolume.matches(volumeName: String): Boolean {
        if (volumeName == LEGACY_EXTERNAL_VOLUME_NAME) return isPrimary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching { mediaStoreVolumeName == volumeName }.getOrDefault(false)
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
            return isPrimary
        }
        return uuid?.equals(volumeName, ignoreCase = true) == true ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && directory?.name == volumeName)
    }

    private fun StorageVolume.rootPath(isPrimary: Boolean): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching { directory?.absolutePath?.takeIf(String::isNotBlank) }.getOrNull()
                ?: legacyExternalFilesRoot(this, isPrimary)
        }
        if (isPrimary) {
            @Suppress("DEPRECATION")
            return Environment.getExternalStorageDirectory().absolutePath
        }
        // StorageVolume had no public directory accessor on API 26–29. An app
        // external-files directory is a safe, volume-specific fallback when its
        // UUID can be matched; otherwise leave the path unavailable.
        return legacyExternalFilesRoot(this, isPrimary)
    }

    private fun legacyExternalFilesRoot(volume: StorageVolume, isPrimary: Boolean): String? {
        if (isPrimary) {
            @Suppress("DEPRECATION")
            return Environment.getExternalStorageDirectory().absolutePath
        }
        val uuid = volume.uuid?.takeIf(String::isNotBlank) ?: return null
        val suffix = "/Android/data/${context.packageName}/files"
        return context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .map(File::getAbsolutePath)
            .firstOrNull { path ->
                val normalized = path.replace('\\', '/')
                if (!normalized.endsWith(suffix, ignoreCase = true)) {
                    false
                } else {
                    normalized.substring(0, normalized.length - suffix.length)
                        .substringAfterLast('/')
                        .equals(uuid, ignoreCase = true)
                }
            }
            ?.let { path -> path.replace('\\', '/').dropLast(suffix.length) }
    }

    private fun capacityForPath(path: String): Capacities {
        val statFs = runCatching { StatFs(File(path).absolutePath) }.getOrNull() ?: return Capacities(null, null)
        val total = multiplyOrNull(statFs.blockCountLong, statFs.blockSizeLong)
            ?.takeIf { it >= 0 }
            ?: return Capacities(null, null)
        val available = statFs.availableBytes.coerceIn(0, total)
        return Capacities(
            usedBytes = (total - available).coerceIn(0, total),
            totalBytes = total,
        )
    }

    private fun multiplyOrNull(left: Long, right: Long): Long? =
        runCatching { Math.multiplyExact(left, right) }.getOrNull()

    private fun StorageVolume.isMounted(): Boolean =
        state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY

    private fun String.isPrimaryVolumeName(): Boolean =
        this == LEGACY_EXTERNAL_VOLUME_NAME ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this == MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private data class Capacities(val usedBytes: Long?, val totalBytes: Long?)

    private companion object {
        const val LEGACY_EXTERNAL_VOLUME_NAME = "external"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FolderVolumeMetadataModule {
    @Binds
    @Singleton
    abstract fun bindFolderVolumeMetadataSource(
        implementation: AndroidFolderVolumeMetadataSource,
    ): FolderVolumeMetadataSource
}
