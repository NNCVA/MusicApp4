package com.musicapp.player.feature.scan

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.util.Locale

data class ScanFolderSelection(
    val volumeName: String,
    val directory: String,
)

object ScanFolderResolver {

    fun resolve(context: Context, uri: Uri): ScanFolderSelection? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        val externalVolumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        val storageVolumes = runCatching {
            context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
        }.getOrDefault(emptyList())

        return resolveFromDocumentId(
            documentId = documentId,
            sdkInt = Build.VERSION.SDK_INT,
            externalVolumeNames = externalVolumeNames,
            storageVolumes = storageVolumes,
        )
    }

    @SuppressLint("InlinedApi")
    internal fun resolveFromDocumentId(
        documentId: String,
        sdkInt: Int = Build.VERSION.SDK_INT,
        externalVolumeNames: Set<String>,
        storageVolumes: List<StorageVolume> = emptyList(),
    ): ScanFolderSelection? {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val storageId = documentId.substring(0, separator)
        val directory = documentId.substring(separator + 1).replace('\\', '/').trim('/')

        val volumeName = if (storageId.equals("primary", ignoreCase = true)) {
            if (sdkInt >= Build.VERSION_CODES.Q) {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            } else {
                "external"
            }
        } else {
            if (sdkInt >= Build.VERSION_CODES.Q) {
                val matchedVolume = externalVolumeNames.firstOrNull { it.equals(storageId, ignoreCase = true) }
                if (matchedVolume != null) {
                    matchedVolume
                } else {
                    val volumeFromStorageManager = storageVolumes.firstOrNull { volume ->
                        volume.uuid?.equals(storageId, ignoreCase = true) == true
                    }
                    val mediaStoreName = if (sdkInt >= Build.VERSION_CODES.R && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Api30Helper.mediaStoreVolumeName(volumeFromStorageManager)
                    } else {
                        null
                    }
                    if (mediaStoreName != null && externalVolumeNames.any { it.equals(mediaStoreName, ignoreCase = true) }) {
                        externalVolumeNames.first { it.equals(mediaStoreName, ignoreCase = true) }
                    } else if (storageId.lowercase(Locale.ROOT) in externalVolumeNames) {
                        storageId.lowercase(Locale.ROOT)
                    } else {
                        return null
                    }
                }
            } else {
                storageId
            }
        }

        if (sdkInt >= Build.VERSION_CODES.Q &&
            !externalVolumeNames.any { it.equals(volumeName, ignoreCase = true) }
        ) {
            return null
        }

        return ScanFolderSelection(volumeName, directory)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30Helper {
        fun mediaStoreVolumeName(volume: StorageVolume?): String? =
            runCatching { volume?.mediaStoreVolumeName }.getOrNull()
    }
}
