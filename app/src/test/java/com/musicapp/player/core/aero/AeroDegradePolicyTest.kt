package com.musicapp.player.core.aero

import androidx.compose.ui.graphics.Color
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.feature.aero.resolveAeroPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AeroDegradePolicyTest {
    @Test
    fun `each runtime power signal degrades dynamic modes to solid without frame scheduling`() {
        val cases =
            listOf(
                AeroRuntimeSignals.Active.copy(isAppInForeground = false) to
                    AeroDegradeReason.APP_BACKGROUND,
                AeroRuntimeSignals.Active.copy(isScreenInteractive = false) to
                    AeroDegradeReason.SCREEN_OFF,
                AeroRuntimeSignals.Active.copy(isPowerSaveMode = true) to
                    AeroDegradeReason.POWER_SAVE,
                AeroRuntimeSignals.Active.copy(isBatteryLow = true) to
                    AeroDegradeReason.BATTERY_LOW,
            )

        cases.forEach { (signals, expectedReason) ->
            val state = AeroDegradePolicy.resolve(AeroMode.FLUID_MESH, signals)

            assertEquals(AeroMode.SOLID, state.effectiveMode)
            assertEquals(setOf(expectedReason), state.degradeReasons)
            assertFalse(state.schedulesCanvasFrames)
        }
    }

    @Test
    fun `animator duration scale zero degrades every dynamic mode`() {
        val signals = AeroRuntimeSignals.Active.copy(areSystemAnimationsEnabled = false)

        AeroMode.entries.filterNot { it == AeroMode.SOLID }.forEach { mode ->
            val state = AeroDegradePolicy.resolve(mode, signals)
            assertEquals(AeroMode.SOLID, state.effectiveMode)
            assertEquals(
                setOf(AeroDegradeReason.SYSTEM_ANIMATIONS_DISABLED),
                state.degradeReasons,
            )
            assertFalse(state.schedulesCanvasFrames)
        }
    }

    @Test
    fun `clearing all degrade conditions restores the preferred mode and frame scheduling`() {
        val degraded =
            AeroDegradePolicy.resolve(
                AeroMode.GLOW_AURA,
                AeroRuntimeSignals(
                    isAppInForeground = false,
                    isScreenInteractive = false,
                    isPowerSaveMode = true,
                    isBatteryLow = true,
                    areSystemAnimationsEnabled = false,
                ),
            )
        val restored = AeroDegradePolicy.resolve(degraded.preferredMode, AeroRuntimeSignals.Active)

        assertEquals(AeroMode.SOLID, degraded.effectiveMode)
        assertEquals(5, degraded.degradeReasons.size)
        assertEquals(AeroMode.GLOW_AURA, restored.effectiveMode)
        assertTrue(restored.degradeReasons.isEmpty())
        assertTrue(restored.schedulesCanvasFrames)
    }

    @Test
    fun `solid preference never schedules canvas frames`() {
        val state = AeroDegradePolicy.resolve(AeroMode.SOLID, AeroRuntimeSignals.Active)

        assertEquals(AeroMode.SOLID, state.effectiveMode)
        assertFalse(state.schedulesCanvasFrames)
    }

    @Test
    fun `artwork sampler returns at most three opaque dominant colors`() {
        val artwork =
            ArtworkImage(
                width = 4,
                height = 2,
                argbPixels =
                    intArrayOf(
                        0xFFFF0000.toInt(),
                        0xFFFF0000.toInt(),
                        0xFF00FF00.toInt(),
                        0xFF00FF00.toInt(),
                        0xFF0000FF.toInt(),
                        0xFF0000FF.toInt(),
                        0xFFFFFF00.toInt(),
                        0x00FFFFFF,
                    ),
            )

        val colors = ArtworkColorSampler.dominantArgb(artwork)

        assertEquals(3, colors.size)
        assertTrue(colors.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `full player palette mixes at most three artwork colors with theme colors`() {
        val firstThree = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val palette =
            resolveAeroPalette(
                base = Color.Black,
                primary = Color.White,
                secondary = Color.White,
                tertiary = Color.White,
                artworkArgb = firstThree + 0xFFFFFF00.toInt(),
            )
        val withoutFourth =
            resolveAeroPalette(
                base = Color.Black,
                primary = Color.White,
                secondary = Color.White,
                tertiary = Color.White,
                artworkArgb = firstThree,
            )

        assertEquals(withoutFourth, palette)
        assertFalse(palette.base == Color.Black)
        assertFalse(palette.primary == Color.White)
        assertFalse(palette.secondary == Color.White)
        assertFalse(palette.tertiary == Color.White)
    }
}
