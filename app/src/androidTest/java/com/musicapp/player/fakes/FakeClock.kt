package com.musicapp.player.fakes

import com.musicapp.player.core.common.time.Clock

class FakeClock(
    var timeMillis: Long = 0L,
) : Clock {
    override fun currentTimeMillis(): Long = timeMillis
}
