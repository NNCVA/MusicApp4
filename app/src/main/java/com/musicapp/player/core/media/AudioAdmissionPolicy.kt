package com.musicapp.player.core.media

import java.util.Locale

object AudioAdmissionPolicy {
    fun evaluate(
        candidate: MediaAudioCandidate,
        rejectShortAudio: Boolean = false,
    ): AdmissionResult {
        if (candidate.durationMs <= 0) {
            return AdmissionResult.REJECTED_NON_POSITIVE_DURATION
        }
        if (rejectShortAudio && candidate.durationMs < AudioFormatRegistry.MIN_AUDIO_DURATION_MS) {
            return AdmissionResult.REJECTED_SHORT_AUDIO
        }
        if (candidate.isRingtone || candidate.isAlarm || candidate.isNotification) {
            return AdmissionResult.REJECTED_SYSTEM_AUDIO
        }

        val mimeType = candidate.mimeType.normalizedMimeType()
        val supported = when {
            AudioFormatRegistry.isGenericMimeType(mimeType) -> candidate.hasSupportedExtension()
            else -> AudioFormatRegistry.isSupportedMimeType(mimeType)
        }
        return if (supported) {
            AdmissionResult.ACCEPTED
        } else {
            AdmissionResult.REJECTED_UNSUPPORTED_FORMAT
        }
    }

    private fun String?.normalizedMimeType(): String? =
        this
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotEmpty)

    private fun MediaAudioCandidate.hasSupportedExtension(): Boolean {
        val extension = displayName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return AudioFormatRegistry.isSupportedExtension(extension)
    }
}
