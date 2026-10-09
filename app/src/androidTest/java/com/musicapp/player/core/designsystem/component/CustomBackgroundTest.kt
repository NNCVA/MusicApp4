package com.musicapp.player.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun transparentPhotoMiniSliceMatchesWindowPixelsWithoutDoubleCompositing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("background-render-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) {
            for (x in 0 until 100) bitmap.setPixel(x, y, AndroidColor.argb(128, y, x * 2, 255 - y))
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val loaded = AtomicBoolean(false)
        var showMini by mutableStateOf(false)
        try {
            compose.setContent {
                MaterialTheme {
                    CustomBackgroundHost(image.absolutePath, { error("Unexpected background failure") }, Modifier.size(180.dp, 300.dp).testTag("window")) {
                        val enabled = LocalCustomBackgroundEnabled.current
                        SideEffect { loaded.set(enabled) }
                        if (showMini) {
                            val origin = with(LocalDensity.current) { (maxHeight - 60.dp).toPx().roundToInt().toFloat() }
                            CustomBackgroundSlice(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.dp),
                                offsetInViewport = { Offset(0f, origin) },
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { loaded.get() }
            val baseline = compose.onNodeWithTag("window").captureToImage().toPixelMap()
            compose.runOnIdle { showMini = true }
            val sliced = compose.onNodeWithTag("window").captureToImage().toPixelMap()
            for (x in listOf(baseline.width / 4, baseline.width / 2, baseline.width * 3 / 4)) {
                for (y in listOf(baseline.height * 9 / 10, baseline.height - 4)) {
                    assertEquals(baseline[x, y], sliced[x, y])
                }
            }
        } finally {
            image.delete()
        }
    }

    @Test
    fun excludedDestinationDoesNotEnableImageOrTransparentCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("background-excluded-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.RED)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var enabled = true
        var defaultColor = androidx.compose.ui.graphics.Color.Unspecified
        try {
            compose.setContent {
                MaterialTheme {
                    defaultColor = com.musicapp.player.theme.MusicTheme.colors.background
                    CustomBackgroundHost(image.absolutePath, {}, Modifier.size(48.dp).testTag("excluded"), enabled = false) {
                        val current = LocalCustomBackgroundEnabled.current
                        SideEffect { enabled = current }
                    }
                }
            }
            compose.runOnIdle { assertFalse(enabled) }
            val pixels = compose.onNodeWithTag("excluded").captureToImage().toPixelMap()
            assertEquals(defaultColor, pixels[pixels.width / 2, pixels.height / 2])
        } finally {
            image.delete()
        }
    }
}
