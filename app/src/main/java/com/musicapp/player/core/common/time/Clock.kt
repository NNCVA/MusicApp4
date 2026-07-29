package com.musicapp.player.core.common.time

fun interface Clock {
    fun currentTimeMillis(): Long
}

internal class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
