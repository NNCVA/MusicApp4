package com.musicapp.player.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CircularRippleIconButtonTest {

    @Test
    fun disabledAlphaMatchesSpecification() {
        assertEquals(0.38f, CIRCULAR_RIPPLE_ICON_DISABLED_ALPHA, 0.001f)
    }
}
