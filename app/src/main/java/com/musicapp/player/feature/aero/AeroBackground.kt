package com.musicapp.player.feature.aero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.cos
import kotlin.math.sin

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
    content: @Composable BoxScope.() -> Unit = {},
) {
    val runtimeState = remember(preferredMode, signals) {
        AeroDegradePolicy.resolve(preferredMode, signals)
    }
    val colors = MusicTheme.colors
    val artworkArgb = remember(artwork, mixArtworkColors) {
        if (mixArtworkColors && artwork != null) ArtworkColorSampler.dominantArgb(artwork) else emptyList()
    }
    val palette =
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
    Box(modifier = modifier) {
        when {
            !runtimeState.schedulesCanvasFrames -> SolidAeroCanvas(palette)
            runtimeState.effectiveMode == AeroMode.FLUID_MESH -> FluidMeshAeroCanvas(palette)
            runtimeState.effectiveMode == AeroMode.GLOW_AURA -> GlowAuraAeroCanvas(palette)
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
private fun BoxScope.FluidMeshAeroCanvas(palette: AeroPalette) {
    val transition = rememberInfiniteTransition(label = "aero-fluid-mesh")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(FLUID_MESH_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "aero-fluid-mesh-phase",
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.base)
        val minimumDimension = size.minDimension
        val colors = listOf(palette.primary, palette.secondary, palette.tertiary)
        colors.forEachIndexed { index, color ->
            val angle = phase * TWO_PI + index * TWO_PI / colors.size
            val center =
                Offset(
                    x = size.width * (0.5f + cos(angle).toFloat() * MESH_TRAVEL_FRACTION),
                    y = size.height * (0.5f + sin(angle * MESH_VERTICAL_RATE).toFloat() * MESH_TRAVEL_FRACTION),
                )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = MESH_CENTER_ALPHA), Color.Transparent),
                        center = center,
                        radius = minimumDimension * MESH_RADIUS_FRACTION,
                    ),
                radius = minimumDimension * MESH_RADIUS_FRACTION,
                center = center,
            )
        }
    }
}

@Composable
private fun BoxScope.GlowAuraAeroCanvas(palette: AeroPalette) {
    val transition = rememberInfiniteTransition(label = "aero-glow-aura")
    val pulse by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(GLOW_AURA_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "aero-glow-aura-pulse",
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(palette.base)
        val radius = size.minDimension * (GLOW_MINIMUM_RADIUS + pulse * GLOW_RADIUS_RANGE)
        val center = Offset(size.width * GLOW_CENTER_X, size.height * GLOW_CENTER_Y)
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            palette.primary.copy(alpha = GLOW_PRIMARY_ALPHA),
                            palette.secondary.copy(alpha = GLOW_SECONDARY_ALPHA),
                            palette.tertiary.copy(alpha = GLOW_TERTIARY_ALPHA),
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

private const val MAXIMUM_ARTWORK_COLORS = 3
private const val BACKGROUND_ARTWORK_BLEND = 0.18f
private const val ACCENT_ARTWORK_BLEND = 0.52f
private const val FLUID_MESH_CYCLE_MS = 18_000
private const val GLOW_AURA_CYCLE_MS = 4_800
private const val MESH_TRAVEL_FRACTION = 0.28f
private const val MESH_VERTICAL_RATE = 1.35f
private const val MESH_RADIUS_FRACTION = 0.72f
private const val MESH_CENTER_ALPHA = 0.44f
private const val GLOW_MINIMUM_RADIUS = 0.54f
private const val GLOW_RADIUS_RANGE = 0.16f
private const val GLOW_CENTER_X = 0.68f
private const val GLOW_CENTER_Y = 0.28f
private const val GLOW_PRIMARY_ALPHA = 0.5f
private const val GLOW_SECONDARY_ALPHA = 0.32f
private const val GLOW_TERTIARY_ALPHA = 0.2f
private const val TWO_PI = (PI * 2).toFloat()
