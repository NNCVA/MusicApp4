package com.musicapp.player.core.domain

enum class PlaybackMode {
  LIST_LOOP,
  REPEAT_ONE,
  SHUFFLE,
  ;

  companion object {
    val Default: PlaybackMode = LIST_LOOP
  }
}

data class PlaybackQueues(
  val originalQueue: List<TrackId>,
  val stableRandomQueue: List<TrackId>,
)

data class PlaybackSnapshot(
  val queues: PlaybackQueues,
  val currentTrackId: TrackId?,
  val currentQueueIndex: Int?,
  val mode: PlaybackMode = PlaybackMode.Default,
  val positionMs: Long,
  val playInstance: PlayInstanceProgress?,
) {
  init {
    require(positionMs >= 0L) { "positionMs must be non-negative" }
    require((currentTrackId == null) == (currentQueueIndex == null)) {
      "currentTrackId and currentQueueIndex must both be present or absent"
    }
    if (currentTrackId == null) {
      require(positionMs == 0L) { "positionMs must be zero without a current track" }
      require(playInstance == null) { "playInstance must be absent without a current track" }
    }
    if (currentTrackId != null && currentQueueIndex != null) {
      val activeQueue = if (mode == PlaybackMode.SHUFFLE) queues.stableRandomQueue else queues.originalQueue
      require(currentQueueIndex in activeQueue.indices) { "currentQueueIndex is outside the active queue" }
      require(activeQueue[currentQueueIndex] == currentTrackId) { "currentTrackId does not match the active queue" }
    }
    if (mode == PlaybackMode.SHUFFLE) {
      require(queues.originalQueue.groupingBy { it }.eachCount() == queues.stableRandomQueue.groupingBy { it }.eachCount()) {
        "stableRandomQueue must contain the same tracks as originalQueue"
      }
    }
  }
}

sealed interface QueueCodecResult {
  data class Success(val queues: PlaybackQueues) : QueueCodecResult

  data class Corrupt(val reason: String) : QueueCodecResult
}

object PlaybackQueueJsonCodec {
  const val FORMAT_VERSION = 1

  fun encode(queue: List<TrackId>): String = buildString {
    append("{\"formatVersion\":")
    append(FORMAT_VERSION)
    append(",\"items\":[")
    queue.forEachIndexed { index, id ->
      if (index > 0) append(',')
      append("{\"volumeName\":\"")
      append(escape(id.volumeName))
      append("\",\"mediaStoreId\":")
      append(id.mediaStoreId)
      append('}')
    }
    append("]}")
  }

  fun decodeBoth(originalQueueJson: String, stableRandomQueueJson: String): QueueCodecResult {
    val original = decode(originalQueueJson) ?: return QueueCodecResult.Corrupt("originalQueue")
    val random = decode(stableRandomQueueJson) ?: return QueueCodecResult.Corrupt("stableRandomQueue")
    return QueueCodecResult.Success(PlaybackQueues(original, random))
  }

  private fun decode(json: String): List<TrackId>? {
    val match = DOCUMENT.matchEntire(json) ?: return null
    if (match.groupValues[1].toIntOrNull() != FORMAT_VERSION) return null
    val itemsText = match.groupValues[2]
    if (itemsText.isBlank()) return emptyList()
    val results = mutableListOf<TrackId>()
    var cursor = 0
    ITEM.findAll(itemsText).forEach { item ->
      val delimiter = itemsText.substring(cursor, item.range.first).trim()
      if ((results.isEmpty() && delimiter.isNotEmpty()) || (results.isNotEmpty() && delimiter != ",")) return null
      val volume = unescape(item.groupValues[1]) ?: return null
      val mediaStoreId = item.groupValues[2].toLongOrNull() ?: return null
      val id = runCatching { TrackId(volume, mediaStoreId) }.getOrNull() ?: return null
      results += id
      cursor = item.range.last + 1
    }
    if (results.isEmpty() || itemsText.substring(cursor).any { !it.isWhitespace() }) return null
    return results
  }

  private fun escape(value: String): String = buildString {
    value.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\b' -> append("\\b")
        '\u000C' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
      }
    }
  }

  private fun unescape(value: String): String? = buildString {
    var index = 0
    while (index < value.length) {
      val char = value[index++]
      if (char != '\\') {
        append(char)
        continue
      }
      if (index >= value.length) return null
      when (val escaped = value[index++]) {
        '\\', '"', '/' -> append(escaped)
        'b' -> append('\b')
        'f' -> append('\u000C')
        'n' -> append('\n')
        'r' -> append('\r')
        't' -> append('\t')
        'u' -> {
          if (index + 4 > value.length) return null
          val code = value.substring(index, index + 4).toIntOrNull(16) ?: return null
          append(code.toChar())
          index += 4
        }
        else -> return null
      }
    }
  }

  private val DOCUMENT = Regex(
    """\s*\{\s*"formatVersion"\s*:\s*(-?\d+)\s*,\s*"items"\s*:\s*\[(.*)]\s*}\s*""",
    setOf(RegexOption.DOT_MATCHES_ALL),
  )
  private val ITEM = Regex(
    """\{\s*"volumeName"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"mediaStoreId"\s*:\s*(-?\d+)\s*}""",
  )
}
