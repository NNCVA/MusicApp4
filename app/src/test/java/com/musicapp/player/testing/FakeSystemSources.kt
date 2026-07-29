package com.musicapp.player.testing

import com.musicapp.player.core.system.AppClock
import com.musicapp.player.core.system.RandomSource

class FakeAppClock(
  var wallTimeMillis: Long = 0L,
  var elapsedTimeMillis: Long = 0L,
) : AppClock {
  override fun currentTimeMillis(): Long = wallTimeMillis

  override fun elapsedRealtimeMillis(): Long = elapsedTimeMillis
}

class FakeRandomSource(values: Iterable<Int> = emptyList()) : RandomSource {
  private val values = ArrayDeque(values.toList())

  override fun nextInt(boundExclusive: Int): Int {
    require(boundExclusive > 0)
    val value = values.removeFirstOrNull() ?: 0
    require(value in 0 until boundExclusive)
    return value
  }
}
