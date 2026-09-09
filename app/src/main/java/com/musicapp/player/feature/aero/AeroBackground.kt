package com.musicapp.player.feature.aero

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.musicapp.player.core.aero.AeroDegradePolicy
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.aero.ArtworkColorSampler
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.ProvideAeroCardTransparency
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive

@Immutable
data class AeroPalette(
    val base: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

@Composable
fun AeroBackground(
    preferredMode: AeroMode,
    signals: AeroRuntimeSignals,
    modifier: Modifier = Modifier,
    artwork: ArtworkImage? = null,
    mixArtworkColors: Boolean = false,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val runtimeState = remember(preferredMode, signals) {
        AeroDegradePolicy.resolve(preferredMode, signals)
    }
    val colors = MusicTheme.colors
    val artworkArgb = remember(artwork, mixArtworkColors) {
        if (mixArtworkColors && artwork != null) ArtworkColorSampler.dominantArgb(artwork) else emptyList()
    }
    val targetPalette =
        remember(
            colors.background,
            colors.primary,
            colors.secondary,
            colors.tertiary,
            artworkArgb,
        ) {
            resolveAeroPalette(
                base = colors.background,
                primary = colors.primary,
                secondary = colors.secondary,
                tertiary = colors.tertiary,
                artworkArgb = artworkArgb,
            )
        }

    val animatedBase by animateColorAsState(
        targetValue = targetPalette.base,
        animationSpec = tween(AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS),
        label = "aero-palette-base",
    )
    val animatedPrimary by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS),
        label = "aero-palette-primary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS),
        label = "aero-palette-secondary",
    )
    val animatedTertiary by animateColorAsState(
        targetValue = targetPalette.tertiary,
        animationSpec = tween(AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS),
        label = "aero-palette-tertiary",
    )
    val palette = remember(animatedBase, animatedPrimary, animatedSecondary, animatedTertiary) {
        AeroPalette(
            base = animatedBase,
            primary = animatedPrimary,
            secondary = animatedSecondary,
            tertiary = animatedTertiary,
        )
    }

    Box(modifier = modifier) {
        when {
            !runtimeState.schedulesCanvasFrames -> SolidAeroCanvas(palette)
            runtimeState.effectiveMode == AeroMode.FLUID_MESH ->
                FluidMeshAeroCanvas(palette = palette, isPlaying = isPlaying, isVisible = isVisible)
            runtimeState.effectiveMode == AeroMode.GLOW_AURA ->
                GlowAuraAeroCanvas(palette = palette, isPlaying = isPlaying, isVisible = isVisible)
            else -> SolidAeroCanvas(palette)
        }
        ProvideAeroCardTransparency(enabled = runtimeState.schedulesCanvasFrames) {
            content()
        }
    }
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

@Composable
private fun BoxScope.SolidAeroCanvas(palette: AeroPalette) {
    Canvas(modifier = Modifier.fillMaxSize()) { drawRect(palette.base) }
}

@Composable
private fun BoxScope.FluidMeshAeroCanvas(
    palette: AeroPalette,
    isPlaying: Boolean,
    isVisible: Boolean,
) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, isVisible) {
        if (isPlaying && isVisible) {
            while (isActive) {
                val normalized = phase.value % 1f
                val current = if (normalized < 0f) normalized + 1f else normalized
                phase.snapTo(current)
                val remainingFraction = 1f - current
                val duration = (AeroFluidMeshMotion.FLUID_MESH_CYCLE_MS * remainingFraction)
                    .roundToInt()
                    .coerceAtLeast(1)
                phase.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.base)
        val minimumDimension = size.minDimension
        val colors = listOf(palette.primary, palette.secondary, palette.tertiary)
        val currentPhase = phase.value
        colors.forEachIndexed { index, color ->
            val center =
                AeroFluidMeshMotion.calculateCenter(
                    phase = currentPhase,
                    colorIndex = index,
                    colorCount = colors.size,
                    width = size.width,
                    height = size.height,
                )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = AeroFluidMeshMotion.MESH_CENTER_ALPHA),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = minimumDimension * AeroFluidMeshMotion.MESH_RADIUS_FRACTION,
                    ),
                radius = minimumDimension * AeroFluidMeshMotion.MESH_RADIUS_FRACTION,
                center = center,
            )
        }
    }
}

@Composable
private fun BoxScope.GlowAuraAeroCanvas(
    palette: AeroPalette,
    isPlaying: Boolean,
    isVisible: Boolean,
) {
    val pulse = remember { Animatable(0f) }
    var targetPulse by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isPlaying, isVisible) {
        if (isPlaying && isVisible) {
            while (isActive) {
                val remainingFraction = abs(targetPulse - pulse.value)
                val duration = (AeroFluidMeshMotion.GLOW_AURA_CYCLE_MS * remainingFraction)
                    .roundToInt()
                    .coerceAtLeast(1)
                pulse.animateTo(
                    targetValue = targetPulse,
                    animationSpec = tween(duration, easing = LinearEasing),
                )
                targetPulse = if (targetPulse == 1f) 0f else 1f
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.base)
        val radius =
            size.minDimension *
                (AeroFluidMeshMotion.GLOW_MINIMUM_RADIUS + pulse.value * AeroFluidMeshMotion.GLOW_RADIUS_RANGE)
        val center =
            Offset(
                size.width * AeroFluidMeshMotion.GLOW_CENTER_X,
                size.height * AeroFluidMeshMotion.GLOW_CENTER_Y,
            )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            palette.primary.copy(alpha = AeroFluidMeshMotion.GLOW_PRIMARY_ALPHA),
                            palette.secondary.copy(alpha = AeroFluidMeshMotion.GLOW_SECONDARY_ALPHA),
                            palette.tertiary.copy(alpha = AeroFluidMeshMotion.GLOW_TERTIARY_ALPHA),
                            Color.Transparent,
                        ),
                    center = center,
                    radius = radius,
                ),
            radius = radius,
            center = center,
        )
    }
}

internal object AeroFluidMeshMotion {
    const val FLUID_MESH_CYCLE_MS = 36_000
    const val GLOW_AURA_CYCLE_MS = 4_800
    const val COLOR_CROSSFADE_DURATION_MS = 500

    const val MESH_TRAVEL_FRACTION = 0.28f
    const val MESH_RADIUS_FRACTION = 0.72f
    const val MESH_CENTER_ALPHA = 0.44f

    const val GLOW_MINIMUM_RADIUS = 0.54f
    const val GLOW_RADIUS_RANGE = 0.16f
    const val GLOW_CENTER_X = 0.68f
    const val GLOW_CENTER_Y = 0.28f
    const val GLOW_PRIMARY_ALPHA = 0.5f
    const val GLOW_SECONDARY_ALPHA = 0.32f
    const val GLOW_TERTIARY_ALPHA = 0.2f

    private const val TWO_PI = (PI * 2).toFloat()
    private const val SECONDARY_HARMONIC_FACTOR = 0.2f

    /**
     * Calculates the center offset for a fluid blob color point.
     *
     * The motion follows a closed, harmonic curve where both position and velocity (derivatives)
     * are strictly continuous across the cycle boundary [phase 0f -> 1f].
     */
    fun calculateCenter(
        phase: Float,
        colorIndex: Int,
        colorCount: Int,
        width: Float,
        height: Float,
    ): Offset {
        val angle = phase * TWO_PI + colorIndex * TWO_PI / colorCount.coerceAtLeast(1)
        val xNorm = 0.5f + cos(angle) * MESH_TRAVEL_FRACTION
        val yNorm =
            0.5f +
                (sin(angle) + SECONDARY_HARMONIC_FACTOR * sin(angle * 2)) *
                    MESH_TRAVEL_FRACTION
        return Offset(
            x = width * xNorm,
            y = height * yNorm,
        )
    }
}

private const val MAXIMUM_ARTWORK_COLORS = 3
private const val BACKGROUND_ARTWORK_BLEND = 0.18f
private const val ACCENT_ARTWORK_BLEND = 0.52f
