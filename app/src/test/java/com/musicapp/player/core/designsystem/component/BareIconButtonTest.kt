package com.musicapp.player.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class BareIconButtonTest {

    @Test
    fun constantsMatchSpecification() {
        assertEquals(0.60f, BARE_ICON_PRESSED_ALPHA, 0.001f)
        assertEquals(60, BARE_ICON_PRESS_DURATION_MS)
        assertEquals(120, BARE_ICON_RELEASE_DURATION_MS)
    }
}
