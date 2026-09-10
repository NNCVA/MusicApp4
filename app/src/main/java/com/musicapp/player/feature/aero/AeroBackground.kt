package com.musicapp.player.feature.aero

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.musicapp.player.core.aero.AeroDegradePolicy
import com.musicapp.player.core.aero.AeroRuntimeState
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.aero.ArtworkColorSampler
import com.musicapp.player.core.designsystem.motion.PlayerMotionTokens
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.ProvideAeroCardTransparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Immutable
data class AeroPalette(
    val base: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

internal data class FlowingLightPresentation(
    val renderMode: AeroMode,
    val schedulesFrames: Boolean,
    val initialElapsedRealtimeMs: Long?,
)

internal fun resolveFlowingLightPresentation(
    runtimeState: AeroRuntimeState,
    hasArtwork: Boolean,
): FlowingLightPresentation? {
    if (!hasArtwork || runtimeState.degradeReasons.isNotEmpty()) return null
    return when (runtimeState.effectiveMode) {
        AeroMode.FLUID_MESH -> FlowingLightPresentation(AeroMode.FLUID_MESH, true, null)
        AeroMode.GLOW_AURA -> FlowingLightPresentation(AeroMode.GLOW_AURA, false, null)
        AeroMode.SOLID -> FlowingLightPresentation(AeroMode.FLUID_MESH, false, 0L)
    }
}

@Composable
fun AeroBackground(
    preferredMode: AeroMode,
    signals: AeroRuntimeSignals,
    modifier: Modifier = Modifier,
    artwork: ArtworkImage? = null,
    mixArtworkColors: Boolean = false,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    artworkTransitionProgress: Float? = null,
    artworkTransitionRunning: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val runtimeState = remember(preferredMode, signals) {
        AeroDegradePolicy.resolve(preferredMode, signals)
    }
    val colors = MusicTheme.colors
    val artworkArgb = remember(artwork, mixArtworkColors) {
        if (mixArtworkColors && artwork != null) ArtworkColorSampler.dominantArgb(artwork) else emptyList()
    }
    val targetPalette = remember(colors.background, colors.primary, colors.secondary, colors.tertiary, artworkArgb) {
        resolveAeroPalette(
            base = colors.background,
            primary = colors.primary,
            secondary = colors.secondary,
            tertiary = colors.tertiary,
            artworkArgb = artworkArgb,
        )
    }

    var previousTargetPalette by remember { mutableStateOf(targetPalette) }
    var observedTargetPalette by remember { mutableStateOf(targetPalette) }
    LaunchedEffect(targetPalette) {
        if (targetPalette != observedTargetPalette) {
            previousTargetPalette = observedTargetPalette
            observedTargetPalette = targetPalette
        }
    }
    val paletteAnimationDuration = if (artworkTransitionProgress == null) {
        AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS
    } else {
        0
    }
    val animatedBase by animateColorAsState(targetPalette.base, tween(paletteAnimationDuration), label = "aero-palette-base")
    val animatedPrimary by animateColorAsState(targetPalette.primary, tween(paletteAnimationDuration), label = "aero-palette-primary")
    val animatedSecondary by animateColorAsState(targetPalette.secondary, tween(paletteAnimationDuration), label = "aero-palette-secondary")
    val animatedTertiary by animateColorAsState(targetPalette.tertiary, tween(paletteAnimationDuration), label = "aero-palette-tertiary")
    val animatedPalette = remember(animatedBase, animatedPrimary, animatedSecondary, animatedTertiary) {
        AeroPalette(animatedBase, animatedPrimary, animatedSecondary, animatedTertiary)
    }
    val palette = artworkTransitionProgress?.let { progress ->
        if (artworkTransitionRunning) lerpAeroPalette(previousTargetPalette, targetPalette, progress) else targetPalette
    } ?: animatedPalette

    val flowingLightPresentation = resolveFlowingLightPresentation(runtimeState, artwork != null)
    val drawsFlowingLight = flowingLightPresentation != null
    Box(modifier = modifier) {
        if (flowingLightPresentation != null && artwork != null) {
            FlowingLightCanvas(
                artwork = artwork,
                presentation = flowingLightPresentation,
                isPlaying = isPlaying,
                isVisible = isVisible,
                runtimeAllowsFrames = runtimeState.degradeReasons.isEmpty(),
                darkTheme = colors.background.luminance() < .5f,
                transitionProgress = artworkTransitionProgress ?: 1f,
                transitionRunning = artworkTransitionRunning,
                fallbackColor = palette.base,
            )
        } else {
            SolidAeroCanvas(palette)
        }
        ProvideAeroCardTransparency(enabled = drawsFlowingLight) { content() }
    }
}

private data class FlowingLightFrame(
    val artwork: ArtworkImage,
    val image: ImageBitmap,
    val screenSize: IntSize,
    val viewport: FlowingLightViewportSpec,
    val presentation: FlowingLightPresentation,
    val generation: Long,
)

@Composable
private fun BoxScope.FlowingLightCanvas(
    artwork: ArtworkImage,
    presentation: FlowingLightPresentation,
    isPlaying: Boolean,
    isVisible: Boolean,
    runtimeAllowsFrames: Boolean,
    darkTheme: Boolean,
    transitionProgress: Float,
    transitionRunning: Boolean,
    fallbackColor: Color,
) {
    val renderer = remember { FlowingLightBitmapRenderer() }
    val densityDpi = LocalConfiguration.current.densityDpi
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var currentFrame by remember { mutableStateOf<FlowingLightFrame?>(null) }
    var previousFrame by remember { mutableStateOf<FlowingLightFrame?>(null) }
    var previousFrameUsesArtworkTransition by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0L) }
    val presentationTransitionProgress = remember { Animatable(1f) }

    DisposableEffect(renderer) { onDispose(renderer::close) }

    LaunchedEffect(artwork, canvasSize, densityDpi, darkTheme, presentation) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        val oldFrame = currentFrame
        if (oldFrame != null && oldFrame.screenSize != canvasSize) {
            currentFrame = null
            previousFrame = null
            previousFrameUsesArtworkTransition = false
        }
        val isPresentationTransition = oldFrame != null &&
            oldFrame.screenSize == canvasSize &&
            oldFrame.artwork === artwork &&
            oldFrame.presentation != presentation
        val bitmap = withContext(Dispatchers.Default) {
            renderer.render(
                artwork = artwork,
                screenWidth = canvasSize.width,
                screenHeight = canvasSize.height,
                densityDpi = densityDpi,
                darkTheme = darkTheme,
                mode = presentation.renderMode,
                elapsedRealtimeMs = presentation.initialElapsedRealtimeMs ?: SystemClock.elapsedRealtime(),
            )
        }
        generation += 1
        if (isPresentationTransition) {
            previousFrame = oldFrame
            previousFrameUsesArtworkTransition = false
        } else if (oldFrame?.artwork !== artwork || oldFrame.screenSize != canvasSize) {
            previousFrame = oldFrame?.takeIf { it.screenSize == canvasSize }
            previousFrameUsesArtworkTransition = previousFrame != null
        }
        currentFrame = FlowingLightFrame(
            artwork = artwork,
            image = bitmap.asImageBitmap(),
            screenSize = canvasSize,
            viewport = FlowingLightRenderPolicy.viewportSpec(
                canvasSize.width,
                canvasSize.height,
                densityDpi,
            ),
            presentation = presentation,
            generation = generation,
        )
        if (isPresentationTransition) {
            presentationTransitionProgress.snapTo(0f)
            presentationTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS,
                    easing = LinearEasing,
                ),
            )
            previousFrame = null
        } else {
            presentationTransitionProgress.snapTo(1f)
        }
    }

    LaunchedEffect(
        transitionRunning,
        transitionProgress,
        previousFrame,
        previousFrameUsesArtworkTransition,
        currentFrame,
    ) {
        if (
            previousFrameUsesArtworkTransition &&
            previousFrame != null &&
            !transitionRunning &&
            transitionProgress >= 1f
        ) {
            previousFrame = null
            previousFrameUsesArtworkTransition = false
        }
    }

    val shouldSchedule = FlowingLightRenderPolicy.shouldScheduleFrames(
        dynamic = presentation.schedulesFrames,
        isPlaying = isPlaying,
        isVisible = isVisible,
        runtimeAllowsFrames = runtimeAllowsFrames,
        transitionRunning = transitionRunning || previousFrame != null,
    )
    LaunchedEffect(artwork, canvasSize, densityDpi, darkTheme, presentation, shouldSchedule, currentFrame?.artwork) {
        if (!shouldSchedule || currentFrame?.artwork !== artwork || canvasSize == IntSize.Zero) return@LaunchedEffect
        while (isActive) {
            delay(FlowingLightRenderPolicy.FRAME_DELAY_MS)
            val bitmap = withContext(Dispatchers.Default) {
                renderer.render(
                    artwork = artwork,
                    screenWidth = canvasSize.width,
                    screenHeight = canvasSize.height,
                    densityDpi = densityDpi,
                    darkTheme = darkTheme,
                    mode = presentation.renderMode,
                    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                )
            }
            generation += 1
            currentFrame = FlowingLightFrame(
                artwork = artwork,
                image = bitmap.asImageBitmap(),
                screenSize = canvasSize,
                viewport = FlowingLightRenderPolicy.viewportSpec(
                    canvasSize.width,
                    canvasSize.height,
                    densityDpi,
                ),
                presentation = presentation,
                generation = generation,
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        drawRect(fallbackColor)
        val current = currentFrame ?: return@Canvas
        val previous = previousFrame
        val crossfadeProgress = if (previousFrameUsesArtworkTransition) {
            transitionProgress
        } else {
            presentationTransitionProgress.value
        }
        val alpha = FlowingLightRenderPolicy.crossfade(crossfadeProgress, previous != null)
        previous?.let { drawFullscreenImage(it, alpha.previousAlpha) }
        drawFullscreenImage(current, alpha.currentAlpha)
    }
}

private fun DrawScope.drawFullscreenImage(frame: FlowingLightFrame, alpha: Float) {
    if (alpha <= 0f) return
    drawImage(
        image = frame.image,
        srcOffset = IntOffset(frame.viewport.offsetX, frame.viewport.offsetY),
        srcSize = IntSize(frame.viewport.width, frame.viewport.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

internal fun resolveAeroPalette(
    base: Color,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    artworkArgb: List<Int>,
): AeroPalette {
    val artworkColors = artworkArgb.take(MAXIMUM_ARTWORK_COLORS).map { Color(it) }
    return AeroPalette(
        base = artworkColors.firstOrNull()?.let { lerp(base, it, BACKGROUND_ARTWORK_BLEND) } ?: base,
        primary = artworkColors.getOrNull(0)?.let { lerp(primary, it, ACCENT_ARTWORK_BLEND) } ?: primary,
        secondary = artworkColors.getOrNull(1)?.let { lerp(secondary, it, ACCENT_ARTWORK_BLEND) } ?: secondary,
        tertiary = artworkColors.getOrNull(2)?.let { lerp(tertiary, it, ACCENT_ARTWORK_BLEND) } ?: tertiary,
    )
}

internal fun lerpAeroPalette(start: AeroPalette, end: AeroPalette, progress: Float): AeroPalette {
    val fraction = progress.coerceIn(0f, 1f)
    return AeroPalette(
        base = lerp(start.base, end.base, fraction),
        primary = lerp(start.primary, end.primary, fraction),
        secondary = lerp(start.secondary, end.secondary, fraction),
        tertiary = lerp(start.tertiary, end.tertiary, fraction),
    )
}

@Composable
private fun BoxScope.SolidAeroCanvas(palette: AeroPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) { drawRect(palette.base) }
}

internal object AeroFluidMeshMotion {
    const val COLOR_CROSSFADE_DURATION_MS = PlayerMotionTokens.TRACK_CHANGE_DURATION_MS
}

private const val MAXIMUM_ARTWORK_COLORS = 3
private const val BACKGROUND_ARTWORK_BLEND = 0.18f
private const val ACCENT_ARTWORK_BLEND = 0.52f
