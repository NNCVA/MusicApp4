package com.musicapp.player.feature.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class MediaPermissionState(
    open val permission: MediaPermission,
    val canQueryMediaStore: Boolean,
) {
    data class PurposeExplanation(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = false)

    data class Requesting(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = false)

    data class DeniedCanRetry(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = false)

    data class PermanentlyDenied(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = false)

    data class WaitingForSettingsReturn(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = false)

    data class Granted(
        override val permission: MediaPermission,
    ) : MediaPermissionState(permission, canQueryMediaStore = true)
}

class MediaPermissionCoordinator(
    private val gateway: PermissionGateway,
) {
    val requiredPermission: MediaPermission = requiredMediaPermission(gateway.apiLevel)

    private val mutableState = MutableStateFlow(resolveCurrentState())
    val state: StateFlow<MediaPermissionState> = mutableState.asStateFlow()

    val canQueryMediaStore: Boolean
        get() = state.value.canQueryMediaStore

    fun confirmPurposeExplanation() {
        check(state.value is MediaPermissionState.PurposeExplanation) {
            "Purpose explanation can only be confirmed before the first request"
        }
        launchPermissionRequest()
    }

    fun retryPermissionRequest() {
        check(state.value is MediaPermissionState.DeniedCanRetry) {
            "Permission can only be retried after a retryable denial"
        }
        launchPermissionRequest()
    }

    fun onPermissionResult(granted: Boolean) {
        mutableState.value =
            if (granted) {
                MediaPermissionState.Granted(requiredPermission)
            } else if (gateway.shouldShowRationale(requiredPermission)) {
                MediaPermissionState.DeniedCanRetry(requiredPermission)
            } else {
                MediaPermissionState.PermanentlyDenied(requiredPermission)
            }
    }

    fun openApplicationSettings() {
        check(state.value is MediaPermissionState.PermanentlyDenied) {
            "Application settings are only opened after a permanent denial"
        }
        mutableState.value = MediaPermissionState.WaitingForSettingsReturn(requiredPermission)
        gateway.openApplicationSettings()
    }

    fun onApplicationSettingsReturned() {
        check(state.value is MediaPermissionState.WaitingForSettingsReturn) {
            "Settings return received without opening application settings"
        }
        mutableState.value =
            if (gateway.isGranted(requiredPermission)) {
                MediaPermissionState.Granted(requiredPermission)
            } else {
                MediaPermissionState.PermanentlyDenied(requiredPermission)
            }
    }

    fun refreshPermission() {
        mutableState.value = resolveCurrentState()
    }

    private fun launchPermissionRequest() {
        mutableState.value = MediaPermissionState.Requesting(requiredPermission)
        gateway.requestPermission(requiredPermission)
    }

    private fun resolveCurrentState(): MediaPermissionState =
        when {
            gateway.isGranted(requiredPermission) -> MediaPermissionState.Granted(requiredPermission)
            !gateway.wasRequested(requiredPermission) ->
                MediaPermissionState.PurposeExplanation(requiredPermission)
            gateway.shouldShowRationale(requiredPermission) ->
                MediaPermissionState.DeniedCanRetry(requiredPermission)
            else -> MediaPermissionState.PermanentlyDenied(requiredPermission)
        }
}
