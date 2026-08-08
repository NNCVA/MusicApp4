package com.musicapp.player.data

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
        val card = track(volumeName = "card", mediaStoreId = 42, title = "Card", relativePath = "Podcasts")
        repository.mergeTracks(listOf(primary, removable, card))

        assertEquals(3, repository.observeTracks(includeHidden = true).first().size)
        assertEquals(listOf(primary), repository.observeAlbumTracks(AlbumId("external_primary", 8)).first().filter { it.id == primary.id })
        assertEquals(3, repository.observeArtistTracks(ArtistId(7)).first().size)
        assertEquals(listOf(card), repository.observeFolderTracks("card", "Podcasts").first())

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

    @Test
    fun roomAndFakeDerivedQueriesExcludeHiddenAndRespectExactRecursiveFolders() = runTest {
        val root = track(mediaStoreId = 1, title = "Root", relativePath = "")
        val direct = track(mediaStoreId = 2, title = "Direct", relativePath = "Music")
        val descendant = track(mediaStoreId = 3, title = "Descendant", relativePath = "Music/Live")
        val prefixedSibling = track(mediaStoreId = 4, title = "Sibling", relativePath = "Music Videos")
        val card = track(volumeName = "card", mediaStoreId = 5, title = "Card", relativePath = "Music")
        val tracks = listOf(root, direct, descendant, prefixedSibling, card)
        repository.mergeTracks(tracks)
        val repositories: List<MediaLibraryRepository> = listOf(
            repository,
            FakeMediaLibraryRepository(initialTracks = tracks),
        )

        repositories.forEach { subject ->
            assertEquals(
                setOf(direct.id, descendant.id),
                subject.observeFolderTracks("external_primary", "Music").first().map { it.id }.toSet(),
            )

            subject.setHidden(descendant.id, hidden = true, changedAtMs = 10)

            assertEquals(
                setOf(direct.id),
                subject.observeFolderTracks("external_primary", "Music/").first().map { it.id }.toSet(),
            )
            assertEquals(
                setOf(root.id, direct.id, prefixedSibling.id),
                subject.observeFolderTracks("external_primary", "").first().map { it.id }.toSet(),
            )
            assertEquals(
                setOf(root.id, direct.id, prefixedSibling.id),
                subject.observeAlbumTracks(AlbumId("external_primary", 8)).first().map { it.id }.toSet(),
            )
            assertEquals(
                setOf(root.id, direct.id, prefixedSibling.id, card.id),
                subject.observeArtistTracks(ArtistId(7)).first().map { it.id }.toSet(),
            )
        }
    }

    @Test
    fun roomAndFakeNormalizePathRulesBeforeUniquenessChecks() = runTest {
        val repositories: List<MediaLibraryRepository> = listOf(
            repository,
            FakeMediaLibraryRepository(),
        )

        repositories.forEach { subject ->
            val rule = subject.addPathRule("external_primary", "./Music/Live/..", PathRuleKind.INCLUDE)
            val duplicate = runCatching {
                subject.addPathRule("external_primary", "Music/", PathRuleKind.INCLUDE)
            }.exceptionOrNull()

            assertEquals("Music", rule.directory)
            assertTrue(duplicate != null)
        }
    }

    @Test
    fun batchVisibilityChangesAreAtomicAndNeverDeleteTracks() = runTest {
        val first = track(mediaStoreId = 101, title = "First")
        val second = track(mediaStoreId = 102, title = "Second")
        repository.mergeTracks(listOf(first, second))
        val repositories: List<MediaLibraryRepository> = listOf(
            repository,
            FakeMediaLibraryRepository(initialTracks = listOf(first, second)),
        )

        repositories.forEach { subject ->
            subject.setHidden(listOf(first.id, second.id, first.id), hidden = true, changedAtMs = 10)
            assertEquals(emptyList<TrackId>(), subject.observeTracks().first().map { it.id })
            assertEquals(
                setOf(first.id, second.id),
                subject.observeTracks(includeHidden = true).first().mapTo(mutableSetOf()) { it.id },
            )

            subject.setHidden(listOf(second.id, first.id), hidden = false, changedAtMs = 11)
            assertEquals(
                setOf(first.id, second.id),
                subject.observeTracks().first().mapTo(mutableSetOf()) { it.id },
            )
        }
    }

    @Test
    fun mixedValidAndMissingBatchRollsBackEarlierVisibilityChanges() = runTest {
        val existing = track(mediaStoreId = 201, title = "Existing")
        repository.mergeTracks(listOf(existing))
        val repositories: List<MediaLibraryRepository> = listOf(
            repository,
            FakeMediaLibraryRepository(initialTracks = listOf(existing)),
        )
        val missing = TrackId(existing.id.volumeName, 999)

        repositories.forEach { subject ->
            val failure = runCatching {
                subject.setHidden(listOf(existing.id, missing), hidden = true, changedAtMs = 20)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertEquals(listOf(existing.id), subject.observeTracks().first().map { it.id })
            assertEquals(existing, subject.getTrack(existing.id))
        }
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }
}
