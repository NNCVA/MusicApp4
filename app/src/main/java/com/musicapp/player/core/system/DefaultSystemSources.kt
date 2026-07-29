package com.musicapp.player.core.system

import android.os.SystemClock
import javax.inject.Inject
import kotlin.random.Random

class DefaultAppClock @Inject constructor() : AppClock {
  override fun currentTimeMillis(): Long = System.currentTimeMillis()

  override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

class DefaultRandomSource @Inject constructor() : RandomSource {
  override fun nextInt(boundExclusive: Int): Int = Random.Default.nextInt(boundExclusive)
}
