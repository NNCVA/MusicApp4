package com.musicapp.player.data.mediastore

import android.provider.MediaStore

/** Platform query details kept inside the MediaStore adapter boundary. */
data class MediaStoreQuerySpec(
    val projection: List<String>,
    val pathColumn: String,
    val selection: String? = null,
    val selectionArgs: Array<String>? = null,
    val sortOrder: String? = null,
) {
    companion object {
        fun forApiLevel(apiLevel: Int): MediaStoreQuerySpec {
            require(apiLevel >= 26) { "MusicApp only supports API 26 and above" }

            val pathColumn =
                if (apiLevel >= 29) {
                    MediaStore.MediaColumns.RELATIVE_PATH
                } else {
                    MediaStore.MediaColumns.DATA
                }
            return MediaStoreQuerySpec(
                projection = COMMON_PROJECTION + pathColumn,
                pathColumn = pathColumn,
            )
        }

        private val COMMON_PROJECTION =
            listOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
            )
    }
}
