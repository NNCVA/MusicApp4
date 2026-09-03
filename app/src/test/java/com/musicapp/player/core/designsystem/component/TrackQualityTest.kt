package com.musicapp.player.core.designsystem.component

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackQualityTest {

    private fun createTrack(
        displayName: String = "track.mp3",
        mimeType: String? = "audio/mpeg",
        durationMs: Long = 180_000L,
        sizeBytes: Long = 4_500_000L,
    ): Track =
        Track(
            id = TrackId("primary", 1L),
            title = "Test Track",
            artistName = "Artist",
            durationMs = durationMs,
            dateAddedMs = 1_000L,
            dateModifiedMs = 2_000L,
            relativePath = "Music/",
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            availability = Availability.AVAILABLE,
        )

    @Test
    fun dsdFormatsResolveToHiRes() {
        val dsdByMime = createTrack(displayName = "track.audio", mimeType = "audio/dsd")
        val dsdByExtensionDsf = createTrack(displayName = "symphony.dsf", mimeType = null)
        val dsdByExtensionDff = createTrack(displayName = "symphony.dff", mimeType = null)

        assertEquals(TrackQuality.HI_RES, dsdByMime.resolveQuality())
        assertEquals(TrackQuality.HI_RES, dsdByExtensionDsf.resolveQuality())
        assertEquals(TrackQuality.HI_RES, dsdByExtensionDff.resolveQuality())
    }

    @Test
    fun highBitrateLosslessResolvesToHiRes() {
        // 24bit/96kHz FLAC ~ 2500 kbps (180s * 2500kbps / 8000 = 56.25 MB)
        val hiResFlac = createTrack(
            displayName = "master.flac",
            mimeType = "audio/flac",
            durationMs = 180_000L,
            sizeBytes = 56_250_000L,
        )
        // 24bit/48kHz WAV ~ 2304 kbps (180s * 2304kbps / 8000 = 51.84 MB)
        val hiResWav = createTrack(
            displayName = "master.wav",
            mimeType = "audio/wav",
            durationMs = 180_000L,
            sizeBytes = 51_840_000L,
        )

        assertEquals(TrackQuality.HI_RES, hiResFlac.resolveQuality())
        assertEquals(TrackQuality.HI_RES, hiResWav.resolveQuality())
    }

    @Test
    fun standardLosslessResolvesToHigh() {
        // CD-quality 16bit/44.1kHz FLAC ~ 850 kbps (180s * 850kbps / 8000 = 19.125 MB)
        val cdFlac = createTrack(
            displayName = "album_track.flac",
            mimeType = "audio/flac",
            durationMs = 180_000L,
            sizeBytes = 19_125_000L,
        )

        assertEquals(TrackQuality.HIGH, cdFlac.resolveQuality())
    }

    @Test
    fun highBitrateLossyResolvesToHigh() {
        // 320 kbps MP3 (180s * 320kbps / 8000 = 7.2 MB)
        val highMp3 = createTrack(
            displayName = "song_320k.mp3",
            mimeType = "audio/mpeg",
            durationMs = 180_000L,
            sizeBytes = 7_200_000L,
        )

        assertEquals(TrackQuality.HIGH, highMp3.resolveQuality())
    }

    @Test
    fun standardBitrateLossyResolvesToStandard() {
        // 128 kbps MP3 (180s * 128kbps / 8000 = 2.88 MB)
        val standardMp3 = createTrack(
            displayName = "song_128k.mp3",
            mimeType = "audio/mpeg",
            durationMs = 180_000L,
            sizeBytes = 2_880_000L,
        )
        // 128 kbps AAC (180s * 128kbps / 8000 = 2.88 MB)
        val standardAac = createTrack(
            displayName = "song.m4a",
            mimeType = "audio/mp4",
            durationMs = 180_000L,
            sizeBytes = 2_880_000L,
        )

        assertEquals(TrackQuality.STANDARD, standardMp3.resolveQuality())
        assertEquals(TrackQuality.STANDARD, standardAac.resolveQuality())
    }

    @Test
    fun unknownOrMissingMetadataResolvesToNull() {
        val unknownMime = createTrack(
            displayName = "mystery.bin",
            mimeType = "application/octet-stream",
        )
        val nullMimeAndExtension = createTrack(
            displayName = "mystery_file",
            mimeType = null,
        )

        assertNull(unknownMime.resolveQuality())
        assertNull(nullMimeAndExtension.resolveQuality())
    }

    /**
     * QualityBadge 颜色现由 MaterialTheme.colorScheme 提供（@Composable），
     * 此处改为验证 resolveQuality() 返回正确的枚举值，
     * 徽章颜色的主题适配性已由 [QualityBadge] Composable 自身的快照测试覆盖。
     */
    @Test
    fun qualityResolvedToCorrectTier() {
        val hiRes = createTrack(
            displayName = "master.flac",
            mimeType = "audio/flac",
            durationMs = 180_000L,
            sizeBytes = 56_250_000L,
        )
        val high = createTrack(
            displayName = "album_track.flac",
            mimeType = "audio/flac",
            durationMs = 180_000L,
            sizeBytes = 19_125_000L,
        )
        val standard = createTrack(
            displayName = "song_128k.mp3",
            mimeType = "audio/mpeg",
            durationMs = 180_000L,
            sizeBytes = 2_880_000L,
        )

        assertEquals(TrackQuality.HI_RES,  hiRes.resolveQuality())
        assertEquals(TrackQuality.HIGH,    high.resolveQuality())
        assertEquals(TrackQuality.STANDARD, standard.resolveQuality())
    }
}
