package com.musicapp.player.feature.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class AndroidPermissionGateway(
    private val activity: Activity,
    private val launchPermissionRequest: (String) -> Unit,
) : PermissionGateway {
    private val requestHistory =
        activity.getSharedPreferences(REQUEST_HISTORY_FILE, Context.MODE_PRIVATE)

    override val apiLevel: Int
        get() = Build.VERSION.SDK_INT

    override fun isGranted(permission: MediaPermission): Boolean =
        ContextCompat.checkSelfPermission(activity, permission.manifestName) ==
            PackageManager.PERMISSION_GRANTED

    override fun wasRequested(permission: MediaPermission): Boolean =
        requestHistory.getBoolean(permission.manifestName, false)

    override fun shouldShowRationale(permission: MediaPermission): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission.manifestName)

    override fun requestPermission(permission: MediaPermission) {
        check(requestHistory.edit().putBoolean(permission.manifestName, true).commit()) {
            "Unable to persist permission request history"
        }
        launchPermissionRequest(permission.manifestName)
    }

    override fun openApplicationSettings() {
        activity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null),
            ),
        )
    }

    private companion object {
        const val REQUEST_HISTORY_FILE = "media_permission_request_history"
    }
}
