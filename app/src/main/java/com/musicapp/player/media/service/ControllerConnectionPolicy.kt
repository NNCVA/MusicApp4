package com.musicapp.player.media.service

internal enum class ControllerAccess {
    APPLICATION,
    TRUSTED_SYSTEM,
    REJECTED,
}

internal data class ControllerIdentity(
    val packageName: String,
    val uid: Int,
    val isTrusted: Boolean,
)

internal class ControllerConnectionPolicy(
    private val applicationPackageName: String,
    private val applicationUid: Int,
) {
    init {
        require(applicationPackageName.isNotBlank()) { "applicationPackageName must not be blank" }
        require(applicationUid >= 0) { "applicationUid must not be negative" }
    }

    fun accessFor(controller: ControllerIdentity): ControllerAccess =
        when {
            controller.packageName == applicationPackageName && controller.uid == applicationUid ->
                ControllerAccess.APPLICATION
            controller.isTrusted -> ControllerAccess.TRUSTED_SYSTEM
            else -> ControllerAccess.REJECTED
        }
}
