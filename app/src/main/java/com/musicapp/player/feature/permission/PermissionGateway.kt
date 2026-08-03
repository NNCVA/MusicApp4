package com.musicapp.player.feature.permission

enum class MediaPermission(val manifestName: String) {
    READ_MEDIA_AUDIO("android.permission.READ_MEDIA_AUDIO"),
    READ_EXTERNAL_STORAGE("android.permission.READ_EXTERNAL_STORAGE"),
}

interface PermissionGateway {
    val apiLevel: Int

    fun isGranted(permission: MediaPermission): Boolean

    fun wasRequested(permission: MediaPermission): Boolean

    fun shouldShowRationale(permission: MediaPermission): Boolean

    /**
     * Starts the platform permission request and durably records that it was requested.
     * The result must be returned to [MediaPermissionCoordinator.onPermissionResult].
     */
    fun requestPermission(permission: MediaPermission)

    fun openApplicationSettings()
}

fun requiredMediaPermission(apiLevel: Int): MediaPermission {
    require(apiLevel >= 26) { "MusicApp only supports API 26 and above" }
    return if (apiLevel >= 33) {
        MediaPermission.READ_MEDIA_AUDIO
    } else {
        MediaPermission.READ_EXTERNAL_STORAGE
    }
}
