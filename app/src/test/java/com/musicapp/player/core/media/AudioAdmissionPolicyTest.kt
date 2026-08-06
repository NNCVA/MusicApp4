package com.musicapp.player.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioAdmissionPolicyTest {
    @Test
    fun allSevenSupportedExtensionsAreAcceptedWhenMimeTypeIsMissing() {
        listOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus").forEachIndexed { index, extension ->
            val candidate = candidate(
                mediaStoreId = index + 1L,
                displayName = "track.$extension",
                mimeType = null,
            )

            assertEquals(extension, AdmissionResult.ACCEPTED, AudioAdmissionPolicy.evaluate(candidate))
        }
    }

    @Test
    fun allSevenSupportedConcreteMimeTypesAreAcceptedWithoutExtensionFallback() {
        listOf(
            "audio/mpeg",
            "audio/flac",
            "audio/wav",
            "audio/aac",
            "audio/mp4",
            "audio/ogg",
            "audio/opus",
        ).forEachIndexed { index, mimeType ->
            val candidate = candidate(
                mediaStoreId = index + 1L,
                displayName = "track.bin",
                mimeType = mimeType,
            )

            assertEquals(mimeType, AdmissionResult.ACCEPTED, AudioAdmissionPolicy.evaluate(candidate))
        }
    }

    @Test
    fun extensionFallbackIsCaseInsensitiveForMissingAndGenericMimeTypes() {
        listOf(null, "", "application/octet-stream", "audio/*").forEachIndexed { index, mimeType ->
            val candidate = candidate(
                mediaStoreId = index + 1L,
                displayName = "TRACK.FLAC",
                mimeType = mimeType,
            )

            assertEquals(mimeType, AdmissionResult.ACCEPTED, AudioAdmissionPolicy.evaluate(candidate))
        }
    }

    @Test
    fun concreteMimeTypeWinsWhenItConflictsWithExtension() {
        assertEquals(
            AdmissionResult.ACCEPTED,
            AudioAdmissionPolicy.evaluate(candidate(displayName = "track.txt", mimeType = "audio/mpeg")),
        )
        assertEquals(
            AdmissionResult.REJECTED_UNSUPPORTED_FORMAT,
            AudioAdmissionPolicy.evaluate(candidate(displayName = "track.mp3", mimeType = "text/plain")),
        )
    }

    @Test
    fun supportedConcreteMimeTypesAreCaseInsensitiveAndIgnoreParameters() {
        assertEquals(
            AdmissionResult.ACCEPTED,
            AudioAdmissionPolicy.evaluate(candidate(displayName = "track.bin", mimeType = " Audio/MP4; codecs=mp4a.40.2 ")),
        )
    }

    @Test
    fun unsupportedExtensionAndMimeTypeAreRejected() {
        assertEquals(
            AdmissionResult.REJECTED_UNSUPPORTED_FORMAT,
            AudioAdmissionPolicy.evaluate(candidate(displayName = "track.wma", mimeType = null)),
        )
        assertEquals(
            AdmissionResult.REJECTED_UNSUPPORTED_FORMAT,
            AudioAdmissionPolicy.evaluate(candidate(displayName = "track.bin", mimeType = "audio/x-ms-wma")),
        )
    }

    @Test
    fun nonPositiveDurationIsRejected() {
        listOf(0L, -1L).forEach { durationMs ->
            assertEquals(
                AdmissionResult.REJECTED_NON_POSITIVE_DURATION,
                AudioAdmissionPolicy.evaluate(candidate(durationMs = durationMs)),
            )
        }
    }

    @Test
    fun shortAudioIsRejectedOnlyWhenTheScanOptionIsEnabled() {
        val shortAudio = candidate(durationMs = 59_999)

        assertEquals(
            AdmissionResult.ACCEPTED,
            AudioAdmissionPolicy.evaluate(shortAudio),
        )
        assertEquals(
            AdmissionResult.REJECTED_SHORT_AUDIO,
            AudioAdmissionPolicy.evaluate(shortAudio, rejectShortAudio = true),
        )
        assertEquals(
            AdmissionResult.ACCEPTED,
            AudioAdmissionPolicy.evaluate(candidate(durationMs = 60_000), rejectShortAudio = true),
        )
    }

    @Test
    fun ringtoneAlarmAndNotificationAudioAreRejected() {
        listOf(
            candidate(isRingtone = true),
            candidate(isAlarm = true),
            candidate(isNotification = true),
        ).forEach { candidate ->
            assertEquals(AdmissionResult.REJECTED_SYSTEM_AUDIO, AudioAdmissionPolicy.evaluate(candidate))
        }
    }

    @Test
    fun recordingPodcastAndAudiobookAudioRemainEligible() {
        listOf(
            candidate(isRecording = true),
            candidate(isPodcast = true),
            candidate(isAudiobook = true),
        ).forEach { candidate ->
            assertEquals(AdmissionResult.ACCEPTED, AudioAdmissionPolicy.evaluate(candidate))
        }
    }

    @Test
    fun identityCombinesVolumeNameAndMediaStoreIdAcrossVolumes() {
        val primary = candidate(volumeName = "external_primary", mediaStoreId = 42L)
        val sdCard = candidate(volumeName = "0123-4567", mediaStoreId = 42L)

        assertEquals(primary.id, candidate(volumeName = "external_primary", mediaStoreId = 42L).id)
        assertNotEquals(primary.id, sdCard.id)
        assertEquals(42L, sdCard.id.mediaStoreId)
        assertEquals("0123-4567", sdCard.id.volumeName)
    }

    private fun candidate(
        volumeName: String = "external_primary",
        mediaStoreId: Long = 1L,
        displayName: String = "track.mp3",
        mimeType: String? = "audio/mpeg",
        durationMs: Long = 1L,
        isRingtone: Boolean = false,
        isAlarm: Boolean = false,
        isNotification: Boolean = false,
        isRecording: Boolean = false,
        isPodcast: Boolean = false,
        isAudiobook: Boolean = false,
    ) = MediaAudioCandidate(
        volumeName = volumeName,
        mediaStoreId = mediaStoreId,
        displayName = displayName,
        mimeType = mimeType,
        durationMs = durationMs,
        isRingtone = isRingtone,
        isAlarm = isAlarm,
        isNotification = isNotification,
        isRecording = isRecording,
        isPodcast = isPodcast,
        isAudiobook = isAudiobook,
    )
}
