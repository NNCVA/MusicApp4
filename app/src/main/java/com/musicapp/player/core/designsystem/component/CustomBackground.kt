package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.ProvideAeroCardTransparency
import java.io.File
import kotlin.math.max

val LocalCustomBackgroundEnabled = staticCompositionLocalOf { false }

private data class BackgroundFrame(val painter: Painter, val viewport: Size, val backgroundColor: Color)
private val LocalBackgroundFrame = staticCompositionLocalOf<BackgroundFrame?> { null }

/** One decoded image and one window crop, shared by the page and the moving mini sheet. */
@Composable
fun CustomBackgroundHost(
    imagePath: String?,
    onImageError: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val context = LocalContext.current
    val request = remember(context, imagePath) {
        ImageRequest.Builder(context)
            .data(imagePath?.let(::File))
            // The repository has already bounded the image dimensions. Both surfaces use this decode.
            .size(CoilSize.ORIGINAL)
            .build()
    }
    val imageState = key(imagePath) {
        val painter = rememberAsyncImagePainter(request)
        val state by painter.state.collectAsStateWithLifecycle()
        state
    }
    var previousPainter by remember { mutableStateOf<Painter?>(null) }
    val readyPainter = (imageState as? AsyncImagePainter.State.Success)?.painter
    val displayedPainter = when {
        imagePath == null || imageState is AsyncImagePainter.State.Error -> null
        readyPainter != null -> readyPainter
        else -> previousPainter
    }
    SideEffect {
        if (imagePath == null) previousPainter = null
        else if (readyPainter != null) previousPainter = readyPainter
    }
    LaunchedEffect(imagePath, imageState) {
        if (imagePath != null && imageState is AsyncImagePainter.State.Error) onImageError(imagePath)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(MusicTheme.colors.background)) {
        val density = LocalDensity.current
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val active = enabled && displayedPainter != null
        val frame = if (active) BackgroundFrame(checkNotNull(displayedPainter), viewport, MusicTheme.colors.background) else null
        CompositionLocalProvider(
            LocalBackgroundFrame provides frame,
            LocalCustomBackgroundEnabled provides active,
        ) {
            ProvideAeroCardTransparency(enabled = active) {
                CustomBackgroundSlice(Modifier.matchParentSize())
                content()
            }
        }
    }
}

/** [offsetInViewport] is the local surface's origin in the original, full-window background. */
@Composable
fun CustomBackgroundSlice(
    modifier: Modifier = Modifier,
    offsetInViewport: () -> Offset = { Offset.Zero },
) {
    val frame = LocalBackgroundFrame.current ?: return
    Canvas(modifier) {
        // Transparent photos must composite once against the same base on both surfaces.
        // Drawing them over the already-rendered root photo would otherwise double their alpha.
        drawRect(frame.backgroundColor)
        val imageSize = frame.painter.intrinsicSize
        val crop = centerBackgroundCrop(
            imageSize.width, imageSize.height, frame.viewport.width, frame.viewport.height,
        ) ?: return@Canvas
        val origin = offsetInViewport()
        clipRect {
            translate(crop.left - origin.x, crop.top - origin.y) {
                with(frame.painter) { draw(Size(crop.width, crop.height)) }
            }
        }
    }
}

internal data class BackgroundCrop(
    val scale: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal fun centerBackgroundCrop(
    imageWidth: Float,
    imageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): BackgroundCrop? {
    if (listOf(imageWidth, imageHeight, viewportWidth, viewportHeight).any { !it.isFinite() || it <= 0f }) {
        return null
    }
    val scale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return BackgroundCrop(scale, (viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height)
}
