package com.musicapp.player.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainRulesTest {
  @Test
  fun identitiesUseRequiredMediaStoreComponents() {
    assertEquals(TrackId("external", 7), TrackId("external", 7))
    assertFalse(TrackId("external", 7) == TrackId("sdcard", 7))
    assertEquals(AlbumId("external", 9), AlbumId("external", 9))
    assertEquals(ArtistId(12), ArtistId(12))
    assertEquals("stable", PlaylistId("stable").value)
    assertEquals("instance", PlayInstanceId("instance").value)
  }

  @Test
  fun identitiesRejectBlankOrNegativeComponents() {
    assertThrows(IllegalArgumentException::class.java) { TrackId(" ", 1) }
    assertThrows(IllegalArgumentException::class.java) { TrackId("external", -1) }
    assertThrows(IllegalArgumentException::class.java) { AlbumId("external", -1) }
    assertThrows(IllegalArgumentException::class.java) { ArtistId(-1) }
    assertThrows(IllegalArgumentException::class.java) { PlaylistId(" ") }
    assertThrows(IllegalArgumentException::class.java) { PlayInstanceId("") }
  }

  @Test
  fun playlistNameTrimsNormalizesNfcAndCountsCodePoints() {
    val result = PlaylistNameRules.normalize("  Cafe\u0301  ") as PlaylistNameResult.Valid
    assertEquals("Café", result.displayName)
    assertEquals("café", result.comparisonKey)
    assertTrue(PlaylistNameRules.conflicts(result, listOf("CAFÉ")))
    assertTrue(PlaylistNameRules.normalize("  ") is PlaylistNameResult.Blank)
    assertTrue(PlaylistNameRules.normalize("😀".repeat(51)) is PlaylistNameResult.InvalidLength)
  }

  @Test
  fun batchAdditionPreservesSelectionOrderAndCountsDuplicates() {
    val first = TrackId("external", 1)
    val second = TrackId("external", 2)
    val third = TrackId("external", 3)
    val selection = PlaylistBatchRules.selectNewTracks(listOf(first), listOf(second, first, second, third))
    assertEquals(listOf(second, third), selection.tracksToAdd)
    assertEquals(BatchAddResult(2, 2), selection.result)
  }

  @Test
  fun historyThresholdIsHalfDurationCappedAtThirtySecondsAndOncePerInstance() {
    assertEquals(5_001, PlaybackHistoryRules.thresholdMs(10_001))
    assertEquals(30_000, PlaybackHistoryRules.thresholdMs(120_000))
    val pending = PlayInstanceProgress(PlayInstanceId("p1"), 30_000, historyRecorded = false)
    assertTrue(PlaybackHistoryRules.shouldRecord(120_000, pending))
    assertFalse(PlaybackHistoryRules.shouldRecord(120_000, pending.copy(historyRecorded = true)))
  }

  @Test
  fun playbackModeDefaultsToListLoop() {
    assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.Default)
  }

  @Test
  fun playbackSnapshotRejectsShuffleMismatchAndInvalidCurrentItem() {
    val first = TrackId("external", 1)
    val second = TrackId("external", 2)
    assertThrows(IllegalArgumentException::class.java) {
      PlaybackSnapshot(
        queues = PlaybackQueues(listOf(first), listOf(second)),
        currentTrackId = first,
        currentQueueIndex = 0,
        mode = PlaybackMode.SHUFFLE,
        positionMs = 0,
        playInstance = null,
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      PlaybackSnapshot(
        queues = PlaybackQueues(listOf(first), emptyList()),
        currentTrackId = second,
        currentQueueIndex = 0,
        positionMs = 0,
        playInstance = null,
      )
    }
  }

  @Test
  fun domainValuesRejectInvalidTimesAndEmptyPaths() {
    assertThrows(IllegalArgumentException::class.java) {
      Track(
        id = TrackId("external", 1),
        contentUri = " ",
        title = "Title",
        displayName = "track.mp3",
        extension = "mp3",
        artistId = null,
        artistName = null,
        albumId = null,
        albumName = null,
        durationMs = 1,
        dateAddedMs = 0,
        modifiedAtMs = 0,
        relativePath = "Music/",
        mimeType = null,
        sizeBytes = 0,
        isAvailable = true,
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      PathRule(PathRuleId("rule"), "external", " ", PathRuleType.INCLUDE)
    }
    assertThrows(IllegalArgumentException::class.java) { PlayHistory(TrackId("external", 1), -1, 1) }
    assertThrows(IllegalArgumentException::class.java) { PlayHistory(TrackId("external", 1), 0, 0) }
    assertThrows(IllegalArgumentException::class.java) { PlayInstanceProgress(PlayInstanceId("i"), -1, false) }
    assertThrows(IllegalArgumentException::class.java) { BatchAddResult(-1, 0) }
    assertThrows(IllegalArgumentException::class.java) { BatchAddResult(0, -1) }
    assertThrows(IllegalArgumentException::class.java) { PlaybackHistoryRules.thresholdMs(0) }
  }

  @Test
  fun playbackSnapshotAcceptsStableShuffleAndRejectsPartialOrOutOfRangeCurrentItem() {
    val first = TrackId("external", 1)
    val second = TrackId("external", 2)
    val valid =
      PlaybackSnapshot(
        queues = PlaybackQueues(listOf(first, second), listOf(second, first)),
        currentTrackId = second,
        currentQueueIndex = 0,
        mode = PlaybackMode.SHUFFLE,
        positionMs = 1,
        playInstance = PlayInstanceProgress(PlayInstanceId("i"), 1, false),
      )
    assertEquals(second, valid.currentTrackId)
    assertThrows(IllegalArgumentException::class.java) {
      valid.copy(currentTrackId = null, currentQueueIndex = 0)
    }
    assertThrows(IllegalArgumentException::class.java) {
      valid.copy(currentTrackId = first, currentQueueIndex = 3)
    }
    assertThrows(IllegalArgumentException::class.java) { valid.copy(positionMs = -1) }
  }
}
