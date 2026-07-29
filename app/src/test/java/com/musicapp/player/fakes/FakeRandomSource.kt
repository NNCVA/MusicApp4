package com.musicapp.player.fakes

import com.musicapp.player.core.common.random.RandomSource

class FakeRandomSource(
    var nextValue: Int = 0,
) : RandomSource {
    override fun nextInt(untilExclusive: Int): Int {
        require(untilExclusive > 0)
        return Math.floorMod(nextValue, untilExclusive)
    }
}
