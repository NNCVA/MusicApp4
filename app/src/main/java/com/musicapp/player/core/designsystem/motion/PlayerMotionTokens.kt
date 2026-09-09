package com.musicapp.player.core.designsystem.motion

/**
 * Motion values shared by the player artwork transition and its background palette.
 *
 * Keeping these values outside the player feature prevents the UI and Aero layers from
 * drifting apart while preserving the existing design-system dependency direction.
 */
object PlayerMotionTokens {
    const val TRACK_CHANGE_DURATION_MS = 500
    const val TRACK_CHANGE_MAX_BLUR_DP = 20f
}
