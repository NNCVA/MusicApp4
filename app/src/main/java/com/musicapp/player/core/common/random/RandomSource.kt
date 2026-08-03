package com.musicapp.player.core.common.random

import kotlin.random.Random

fun interface RandomSource {
    fun nextInt(untilExclusive: Int): Int
}

internal class DefaultRandomSource : RandomSource {
    override fun nextInt(untilExclusive: Int): Int = Random.Default.nextInt(untilExclusive)
}
