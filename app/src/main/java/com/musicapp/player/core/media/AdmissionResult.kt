package com.musicapp.player.core.media

enum class AdmissionResult {
    ACCEPTED,
    REJECTED_NON_POSITIVE_DURATION,
    REJECTED_SYSTEM_AUDIO,
    REJECTED_UNSUPPORTED_FORMAT,
}
