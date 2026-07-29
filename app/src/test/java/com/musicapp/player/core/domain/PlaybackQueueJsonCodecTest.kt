package com.musicapp.player.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueJsonCodecTest {
  @Test
  fun bothQueuesRoundTripWithoutLosingOrderOrEscapedVolumeNames() {
    val original = listOf(TrackId("external\"primary", 1), TrackId("sd\\card", 2))
    val random = original.reversed()
    val decoded = PlaybackQueueJsonCodec.decodeBoth(
      PlaybackQueueJsonCodec.encode(original),
      PlaybackQueueJsonCodec.encode(random),
    )
    assertEquals(QueueCodecResult.Success(PlaybackQueues(original, random)), decoded)
  }

  @Test
  fun emptyQueuesRoundTrip() {
    val encoded = PlaybackQueueJsonCodec.encode(emptyList())
    assertEquals(QueueCodecResult.Success(PlaybackQueues(emptyList(), emptyList())), PlaybackQueueJsonCodec.decodeBoth(encoded, encoded))
  }

  @Test
  fun oneDamagedOrUnknownVersionQueueRejectsWholePair() {
    val valid = PlaybackQueueJsonCodec.encode(listOf(TrackId("external", 1)))
    assertTrue(PlaybackQueueJsonCodec.decodeBoth(valid, "broken") is QueueCodecResult.Corrupt)
    assertTrue(
      PlaybackQueueJsonCodec.decodeBoth(valid.replace("\"formatVersion\":1", "\"formatVersion\":2"), valid) is
        QueueCodecResult.Corrupt,
    )
  }

  @Test
  fun malformedItemSeparatorsRejectWholePair() {
    val valid = PlaybackQueueJsonCodec.encode(listOf(TrackId("external", 1), TrackId("external", 2)))
    assertTrue(PlaybackQueueJsonCodec.decodeBoth(valid, valid.replace("},{", "}{")) is QueueCodecResult.Corrupt)
    assertTrue(PlaybackQueueJsonCodec.decodeBoth(valid, valid.replace("},{", "},,{")) is QueueCodecResult.Corrupt)
    assertTrue(PlaybackQueueJsonCodec.decodeBoth(valid, valid.replace("]}", ",]}")) is QueueCodecResult.Corrupt)
  }
}
