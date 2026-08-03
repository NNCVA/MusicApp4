package com.musicapp.player.feature.permission

class FakePermissionGateway(
    override val apiLevel: Int,
) : PermissionGateway {
    val grantedPermissions = mutableSetOf<MediaPermission>()
    val requestedPermissions = mutableSetOf<MediaPermission>()
    val rationalePermissions = mutableSetOf<MediaPermission>()
    val requestHistory = mutableListOf<MediaPermission>()
    var applicationSettingsOpenCount: Int = 0
        private set

    override fun isGranted(permission: MediaPermission): Boolean = permission in grantedPermissions

    override fun wasRequested(permission: MediaPermission): Boolean = permission in requestedPermissions

    override fun shouldShowRationale(permission: MediaPermission): Boolean =
        permission in rationalePermissions

    override fun requestPermission(permission: MediaPermission) {
        requestedPermissions += permission
        requestHistory += permission
    }

    override fun openApplicationSettings() {
        applicationSettingsOpenCount += 1
    }
}
