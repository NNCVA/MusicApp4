package com.musicapp.player.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.Options
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioArtworkKeyerTest {

    private lateinit var context: Context
    private lateinit var options: Options
    private lateinit var keyer: AudioArtworkKeyer
    private lateinit var trackKeyer: TrackArtworkKeyer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        options = Options(context = context)
        keyer = AudioArtworkKeyer()
        trackKeyer = TrackArtworkKeyer()
    }

    private fun createSampleTrack(
        volume: String = "external",
        id: Long = 1001L,
        title: String = "Test Title",
        artist: String = "Test Artist",
        modified: Long = 1_700_000_000L,
    ): Track = Track(
        id = TrackId(volume, id),
        title = title,
        artistName = artist,
        durationMs = 180_000L,
        dateAddedMs = 1_000_000L,
        dateModifiedMs = modified,
        relativePath = "Music/$title.mp3",
        displayName = "$title.mp3",
    )

    @Test
    fun key_forTrack_generatesDeterministicKeyWithVolumeIdAndTimestamp() {
        val track = createSampleTrack(volume = "external_primary", id = 1001L, modified = 1_700_000_000L)

        val key = keyer.key(track, options)

        assertEquals("artwork:track:external_primary:1001:1700000000", key)
    }

    @Test
    fun key_isStableAcrossRepeatedInvocationsWithSameData() {
        val track = createSampleTrack(id = 2002L, modified = 5000L)

        val key1 = keyer.key(track, options)
        val key2 = keyer.key(track, options)

        assertEquals(key1, key2)
        assertNotNull(key1)
    }

    @Test
    fun key_changesWhenDateModifiedMsChangesForSameTrackId() {
        val trackV1 = createSampleTrack(id = 3003L, modified = 1_000L)
        val trackV2 = createSampleTrack(id = 3003L, modified = 2_000L)

        val key1 = keyer.key(trackV1, options)
        val key2 = keyer.key(trackV2, options)

        assertNotEquals(key1, key2)
        assertTrue(key1.contains("1000"))
        assertTrue(key2.contains("2000"))
    }

    @Test
    fun key_differentiatesBetweenDifferentVolumeNamesAndIds() {
        val trackExt = createSampleTrack(volume = "external", id = 100L, modified = 1000L)
        val trackInt = createSampleTrack(volume = "internal", id = 100L, modified = 1000L)
        val trackOtherId = createSampleTrack(volume = "external", id = 101L, modified = 1000L)

        val keyExt = keyer.key(trackExt, options)
        val keyInt = keyer.key(trackInt, options)
        val keyOther = keyer.key(trackOtherId, options)

        assertNotEquals(keyExt, keyInt)
        assertNotEquals(keyExt, keyOther)
    }

    @Test
    fun key_forAudioArtworkRequest_trackRequest_generatesConsistentKey() {
        val request = AudioArtworkRequest.TrackArtworkRequest(
            trackId = TrackId("external", 5005L),
            dateModifiedMs = 9_999L,
        )

        val key = keyer.key(request, options)

        assertEquals("artwork:track:external:5005:9999", key)
    }

    @Test
    fun key_forAudioArtworkRequest_albumRequest_generatesKeyWithRepresentativeTrack() {
        val albumId = AlbumId("external", 301L)
        val repTrackId = TrackId("external", 1002L)
        val request = AudioArtworkRequest.AlbumArtworkRequest(
            albumId = albumId,
            representativeTrackId = repTrackId,
            dateModifiedMs = 8_888L,
        )

        val key = keyer.key(request, options)

        assertEquals("artwork:album:external:301:external:1002:8888", key)

        val requestNoRep = AudioArtworkRequest.AlbumArtworkRequest(
            albumId = albumId,
            representativeTrackId = null,
            dateModifiedMs = 8_888L,
        )
        val keyNoRep = keyer.key(requestNoRep, options)
        assertEquals("artwork:album:external:301:none:8888", keyNoRep)
    }

    @Test
    fun key_forAudioArtworkRequest_artistRequest_generatesKeyWithArtistName() {
        val repTrackId = TrackId("external", 1003L)
        val request = AudioArtworkRequest.ArtistArtworkRequest(
            artistName = "Radiohead",
            representativeTrackId = repTrackId,
            dateModifiedMs = 7_777L,
        )

        val key = keyer.key(request, options)

        assertEquals("artwork:artist:Radiohead:external:1003:7777", key)
    }

    @Test
    fun key_forAudioArtworkRequest_playlistRequest_generatesKeyWithPlaylistId() {
        val repTrackId = TrackId("external", 1004L)
        val request = AudioArtworkRequest.PlaylistArtworkRequest(
            playlistId = PlaylistId(42L),
            representativeTrackId = repTrackId,
            dateModifiedMs = 6_666L,
        )

        val key = keyer.key(request, options)

        assertEquals("artwork:playlist:42:external:1004:6666", key)
    }

    @Test
    fun key_forTrackArtworkKeyer_matchesAudioArtworkKeyerTrackKey() {
        val track = createSampleTrack(id = 8008L, modified = 12_345L)

        val keyFromTrackKeyer = trackKeyer.key(track, options)
        val keyFromAudioKeyer = keyer.key(track, options)

        assertEquals(keyFromTrackKeyer, keyFromAudioKeyer)
    }

    @Test
    fun keyFromAny_returnsNullForUnsupportedDataModel() {
        val unsupportedModel = "https://example.com/cover.jpg"

        val key = keyer.keyFromAny(unsupportedModel, options)

        assertNull(key)
    }

    @Test
    fun key_handlesZeroDateModifiedMsWithoutError() {
        val track = createSampleTrack(id = 6006L, modified = 0L)

        val key = keyer.key(track, options)

        assertEquals("artwork:track:external:6006:0", key)
    }
}
