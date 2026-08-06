package com.musicapp.player.core.media

import java.util.Locale

object AudioAdmissionPolicy {
    private val supportedExtensions = setOf(
        "mp3",
        "flac",
        "wav",
        "aac",
        "m4a",
        "ogg",
        "opus",
    )

    private val supportedMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/x-mp3",
        "audio/x-mpeg",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/vnd.wave",
        "audio/aac",
        "audio/aacp",
        "audio/x-aac",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/ogg",
        "audio/x-ogg",
        "application/ogg",
        "audio/opus",
        "audio/x-opus",
    )

    private val genericMimeTypes = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/unknown",
        "application/x-unknown",
        "audio/*",
        "unknown/unknown",
    )

    fun evaluate(
        candidate: MediaAudioCandidate,
        rejectShortAudio: Boolean = false,
    ): AdmissionResult {
        if (candidate.durationMs <= 0) {
            return AdmissionResult.REJECTED_NON_POSITIVE_DURATION
        }
        if (rejectShortAudio && candidate.durationMs < MIN_AUDIO_DURATION_MS) {
            return AdmissionResult.REJECTED_SHORT_AUDIO
        }
        if (candidate.isRingtone || candidate.isAlarm || candidate.isNotification) {
            return AdmissionResult.REJECTED_SYSTEM_AUDIO
        }

        val mimeType = candidate.mimeType.normalizedMimeType()
        val supported = when {
            mimeType == null || mimeType in genericMimeTypes -> candidate.hasSupportedExtension()
            else -> mimeType in supportedMimeTypes
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
        return extension in supportedExtensions
    }

    private const val MIN_AUDIO_DURATION_MS = 60_000L
}
