package com.musicapp.player.core.system

/** Supplies reproducible bounded random values to queue algorithms. */
fun interface RandomSource {
  fun nextInt(boundExclusive: Int): Int
}
