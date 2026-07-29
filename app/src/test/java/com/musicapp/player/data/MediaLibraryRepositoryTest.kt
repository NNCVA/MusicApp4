package com.musicapp.player.data

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaLibraryRepositoryTest {
    private lateinit var database: com.musicapp.player.data.local.MusicDatabase
    private lateinit var repository: RoomMediaLibraryRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository = RoomMediaLibraryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun compositeTrackIdentityAndDerivedQueriesArePreserved() = runTest {
        val primary = track(mediaStoreId = 42, title = "Primary")
        val removable = track(mediaStoreId = 43, title = "Remove")
        val card = track(volumeName = "card", mediaStoreId = 42, title = "Card", relativePath = "Podcasts/")
        repository.mergeTracks(listOf(primary, removable, card))

        assertEquals(3, repository.observeTracks(includeHidden = true).first().size)
        assertEquals(listOf(primary), repository.observeAlbumTracks(AlbumId("external_primary", 8)).first().filter { it.id == primary.id })
        assertEquals(3, repository.observeArtistTracks(ArtistId(7)).first().size)
        assertEquals(listOf(card), repository.observeFolderTracks("card", "Podcasts/").first())

        repository.replaceTracksForVolume("external_primary", listOf(primary.copy(title = "Updated")))
        assertEquals("Updated", repository.getTrack(primary.id)?.title)
        assertEquals(null, repository.getTrack(removable.id))
        assertEquals(card, repository.getTrack(card.id))
    }

    @Test
    fun emptyBulkInputsDoNotDestroyExistingStateAndHiddenStateFiltersOnlyVisibleQuery() = runTest {
        val existing = track(mediaStoreId = 1)
        repository.mergeTracks(listOf(existing))
        repository.mergeTracks(emptyList())
        repository.replaceTracksForVolume(existing.id.volumeName, emptyList())
        repository.setHidden(existing.id, hidden = true, changedAtMs = 10)

        assertTrue(repository.observeTracks().first().isEmpty())
        assertEquals(listOf(existing), repository.observeTracks(includeHidden = true).first())

        val rule = repository.addPathRule("external_primary", "Music/", PathRuleKind.INCLUDE)
        repository.replacePathRules(emptyList())
        assertEquals(listOf(rule), repository.observePathRules().first())
    }

    @Test
    fun roomAndFakeRejectBlankVolumesAndMissingTrackVisibilityChanges() = runTest {
        val existing = track(mediaStoreId = 1)
        repository.mergeTracks(listOf(existing))
        val repositories: List<MediaLibraryRepository> = listOf(
            repository,
            FakeMediaLibraryRepository(initialTracks = listOf(existing)),
        )
        val missing = TrackId("external_primary", 999)

        repositories.forEach { subject ->
            assertIllegalArgument { subject.replaceTracksForVolume(" ", emptyList()) }
            assertIllegalArgument { subject.setVolumeAvailability(" ", Availability.AVAILABLE) }
            assertIllegalArgument { subject.observeFolderTracks(" ", "Music/") }
            assertIllegalArgument { subject.addPathRule(" ", "Music/", PathRuleKind.INCLUDE) }
            assertIllegalArgument { subject.setHidden(missing, hidden = true, changedAtMs = 1) }
            assertIllegalArgument { subject.setHidden(missing, hidden = false, changedAtMs = 1) }
        }
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }
}
