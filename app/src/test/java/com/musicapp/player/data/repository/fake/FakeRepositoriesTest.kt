package com.musicapp.player.data.repository.fake

import com.musicapp.player.core.domain.PathRule
import com.musicapp.player.core.domain.PathRuleId
import com.musicapp.player.core.domain.PathRuleType
import com.musicapp.player.core.domain.PlaybackMode
import com.musicapp.player.core.domain.PlaybackQueues
import com.musicapp.player.core.domain.PlaybackSnapshot
import com.musicapp.player.core.domain.PlaylistId
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
import com.musicapp.player.data.repository.api.InvalidInputReason
import com.musicapp.player.data.repository.api.RepositoryError
import com.musicapp.player.data.repository.api.RepositoryResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
  @Test
  fun repositoryFlowsHaveImmediateEmptyInitialValues() {
    assertEquals(emptyList<Any>(), FakeMediaLibraryRepository().tracks.value)
    assertEquals(emptyList<Any>(), FakePlaylistRepository().playlists.value)
    assertEquals(emptyList<Any>(), FakeHistoryRepository().history.value)
    assertEquals(emptyList<Any>(), FakePathRuleRepository().rules.value)
    assertEquals(null, FakePlaybackSnapshotRepository().snapshot.value)
  }

  @Test
  fun playlistFakeNormalizesRejectsDuplicateAndReportsBatchCounts() = runTest {
    val repository = FakePlaylistRepository(newId = { "playlist-1" })
    val created = repository.create("  Cafe\u0301 ", 10) as RepositoryResult.Success
    assertEquals("Café", created.value.name)
    val duplicate = repository.create("CAFÉ", 11) as RepositoryResult.Failure
    assertTrue(duplicate.error is RepositoryError.AlreadyExists)

    val first = TrackId("external", 1)
    val second = TrackId("external", 2)
    val result = repository.addTracks(created.value.id, listOf(first, first, second)) as RepositoryResult.Success
    assertEquals(2, result.value.addedCount)
    assertEquals(1, result.value.skippedCount)
    assertEquals(listOf(first, second), repository.observeTrackIds(created.value.id).value)
  }

  @Test
  fun missingPlaylistReturnsTypedFailure() = runTest {
    val result = FakePlaylistRepository().delete(PlaylistId("missing")) as RepositoryResult.Failure
    assertTrue(result.error is RepositoryError.NotFound)
  }

  @Test
  fun historyUpsertKeepsOneRowAndIncrementsCount() = runTest {
    val repository = FakeHistoryRepository()
    val trackId = TrackId("external", 1)
    repository.recordQualifiedPlay(trackId, 10)
    repository.recordQualifiedPlay(trackId, 20)
    assertEquals(1, repository.history.value.size)
    assertEquals(2L, repository.history.value.single().playCount)
    assertEquals(20L, repository.history.value.single().lastPlayedAtMs)
  }

  @Test
  fun fullScanAtomicallyUpsertsAndMarksUnseenTracksUnavailable() = runTest {
    val old = track(1)
    val repository = FakeMediaLibraryRepository(listOf(old, track(2)))
    val updated = old.copy(title = "Updated")
    assertTrue(repository.commitFullScan(2, listOf(updated)) is RepositoryResult.Success)
    assertEquals("Updated", repository.tracks.value.first { it.id == old.id }.title)
    assertTrue(repository.tracks.value.first { it.id.mediaStoreId == 2L }.isAvailable.not())
  }

  @Test
  fun invalidFullScanGenerationReturnsTypedFailureWithoutChangingState() = runTest {
    val repository = FakeMediaLibraryRepository(listOf(track(1)))
    val result = repository.commitFullScan(-1, emptyList()) as RepositoryResult.Failure
    assertEquals(RepositoryError.InvalidInput(InvalidInputReason.INVALID_SYNC_GENERATION), result.error)
    assertTrue(repository.tracks.value.single().isAvailable)
  }

  @Test
  fun mediaLibraryMutationsUpdateObservedFlowsAndRejectMissingTracks() = runTest {
    val first = track(1)
    val second = track(2)
    val repository = FakeMediaLibraryRepository(listOf(first))
    val observed = repository.observeTrack(first.id)

    repository.upsertTracks(listOf(first.copy(title = "Updated"), second))
    assertEquals("Updated", observed.value?.title)
    assertEquals(second, repository.observeTrack(second.id).value)

    assertTrue(repository.setTracksAvailable(setOf(first.id), false) is RepositoryResult.Success)
    assertFalse(repository.observeTrack(first.id).value!!.isAvailable)
    val missing = TrackId("external", 99)
    assertTrue(repository.setTracksAvailable(setOf(missing), false) is RepositoryResult.Failure)

    assertTrue(repository.setTracksHidden(setOf(first.id, second.id), true) is RepositoryResult.Success)
    assertEquals(setOf(first.id, second.id), repository.hiddenTrackIds.value)
    repository.setTracksHidden(setOf(first.id), false)
    assertEquals(setOf(second.id), repository.hiddenTrackIds.value)
    assertTrue(repository.setTracksHidden(setOf(missing), true) is RepositoryResult.Failure)
  }

  @Test
  fun playlistLifecycleUsesTypedFailuresAndPreservesTrackOrder() = runTest {
    var nextId = 0
    val repository = FakePlaylistRepository(newId = { "playlist-${++nextId}" })
    assertInvalid(repository.create(" ", 0), InvalidInputReason.BLANK_PLAYLIST_NAME)
    assertInvalid(repository.create("x".repeat(51), 0), InvalidInputReason.PLAYLIST_NAME_TOO_LONG)
    assertInvalid(repository.create("Road", -1), InvalidInputReason.INVALID_TIME)

    val firstPlaylist = (repository.create("Road", 10) as RepositoryResult.Success).value
    val secondPlaylist = (repository.create("Quiet", 11) as RepositoryResult.Success).value
    val renamed = repository.rename(firstPlaylist.id, "Trip", 12) as RepositoryResult.Success
    assertEquals("Trip", renamed.value.name)
    assertTrue(repository.rename(firstPlaylist.id, "quiet", 13) is RepositoryResult.Failure)
    assertInvalid(repository.rename(firstPlaylist.id, "Late", 9), InvalidInputReason.INVALID_TIME)
    assertTrue(repository.rename(PlaylistId("missing"), "Name", 20) is RepositoryResult.Failure)

    val first = TrackId("external", 1)
    val second = TrackId("external", 2)
    repository.addTracks(firstPlaylist.id, listOf(first, second))
    assertEquals(1, (repository.removeTracks(firstPlaylist.id, setOf(first)) as RepositoryResult.Success).value)
    assertEquals(listOf(second), repository.observeTrackIds(firstPlaylist.id).value)
    assertTrue(repository.addTracks(PlaylistId("missing"), listOf(first)) is RepositoryResult.Failure)
    assertTrue(repository.removeTracks(PlaylistId("missing"), setOf(first)) is RepositoryResult.Failure)

    assertTrue(repository.delete(secondPlaylist.id) is RepositoryResult.Success)
    assertTrue(repository.delete(secondPlaylist.id) is RepositoryResult.Failure)
    repository.deleteAll()
    assertTrue(repository.playlists.value.isEmpty())
    assertTrue(repository.observeTrackIds(firstPlaylist.id).value.isEmpty())
  }

  @Test
  fun playlistCreationRejectsInvalidGeneratedId() = runTest {
    val result = FakePlaylistRepository(newId = { " " }).create("Road", 1)
    assertEquals(RepositoryError.PersistenceUnavailable, (result as RepositoryResult.Failure).error)
  }

  @Test
  fun historySnapshotAndPathRuleFakesCoverClearAndMissingSemantics() = runTest {
    val trackId = TrackId("external", 1)
    val history = FakeHistoryRepository()
    assertInvalid(history.recordQualifiedPlay(trackId, -1), InvalidInputReason.INVALID_TIME)
    history.recordQualifiedPlay(trackId, 1)
    history.clear()
    assertTrue(history.history.value.isEmpty())

    val snapshotRepository = FakePlaybackSnapshotRepository()
    val snapshot =
      PlaybackSnapshot(
        queues = PlaybackQueues(listOf(trackId), listOf(trackId)),
        currentTrackId = trackId,
        currentQueueIndex = 0,
        mode = PlaybackMode.LIST_LOOP,
        positionMs = 20,
        playInstance = null,
      )
    snapshotRepository.save(snapshot)
    assertEquals(snapshot, snapshotRepository.snapshot.value)
    snapshotRepository.clear()
    assertNull(snapshotRepository.snapshot.value)

    val rules = FakePathRuleRepository()
    val rule = PathRule(PathRuleId("include-music"), "external", "Music/", PathRuleType.INCLUDE)
    rules.upsert(rule)
    rules.upsert(rule.copy(relativeDirectory = "Audio/"))
    assertEquals(listOf("Audio/"), rules.rules.value.map(PathRule::relativeDirectory))
    assertTrue(rules.remove(PathRuleId("missing")) is RepositoryResult.Failure)
    assertTrue(rules.remove(rule.id) is RepositoryResult.Success)
    assertTrue(rules.rules.value.isEmpty())
  }

  private fun assertInvalid(result: RepositoryResult<*>, reason: InvalidInputReason) {
    assertEquals(RepositoryError.InvalidInput(reason), (result as RepositoryResult.Failure).error)
  }

  private fun track(id: Long) = Track(
    id = TrackId("external", id),
    contentUri = "content://media/external/audio/media/$id",
    title = "Track $id",
    displayName = "track$id.mp3",
    extension = "mp3",
    artistId = null,
    artistName = null,
    albumId = null,
    albumName = null,
    durationMs = 1_000,
    dateAddedMs = 10,
    modifiedAtMs = 20,
    relativePath = "Music/",
    mimeType = "audio/mpeg",
    sizeBytes = 100,
    isAvailable = true,
  )
}
