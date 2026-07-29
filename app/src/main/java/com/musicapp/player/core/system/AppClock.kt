package com.musicapp.player.core.system

/** Supplies wall-clock and monotonic time without coupling business logic to Android APIs. */
interface AppClock {
  fun currentTimeMillis(): Long

  fun elapsedRealtimeMillis(): Long
}
