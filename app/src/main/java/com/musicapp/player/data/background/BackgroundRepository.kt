package com.musicapp.player.data.background

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

data class BackgroundState(
    val imagePath: String? = null,
    val hasSelection: Boolean = false,
) {
    val isUnavailable: Boolean
        get() = hasSelection && imagePath == null
}

interface BackgroundRepository {
    val state: StateFlow<BackgroundState>

    /** Publishes a private copy only after decoding and durable writes succeed. */
    suspend fun importImage(uri: Uri)

    suspend fun deleteImage()

    /** Ignores a failure reported by an image request for a superseded path. */
    suspend fun markUnavailable(imagePath: String)
}

class BackgroundImportException(cause: Throwable) : Exception("Unable to import background image", cause)
