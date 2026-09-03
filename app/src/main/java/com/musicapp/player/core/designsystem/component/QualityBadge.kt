package com.musicapp.player.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.theme.MusicTheme
import java.util.Locale

/**
 * Represents the audio fidelity tier of a track.
 */
enum class TrackQuality(
    @param:StringRes val labelResId: Int,
) {
    HI_RES(R.string.track_quality_hi_res),
    HIGH(R.string.track_quality_high),
    STANDARD(R.string.track_quality_standard),
}

/**
 * Color palette container for the badge Surface and Text.
 */
data class QualityBadgeColors(
    val containerColor: Color,
    val contentColor: Color,
)

/**
 * Resolves the gold (HR), silver (HQ), or bronze (SQ) badge colors for the given quality tier.
 */
@Composable
fun qualityBadgeColors(quality: TrackQuality): QualityBadgeColors {
    val colors = MusicTheme.colors
    return when (quality) {
        TrackQuality.HI_RES ->
            QualityBadgeColors(
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
            )
        TrackQuality.HIGH ->
            QualityBadgeColors(
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurfaceVariant,
            )
        TrackQuality.STANDARD ->
            QualityBadgeColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            )
    }
}

/**
 * Resolves the quality badge tier from the track's MIME type, display name, and estimated bitrate.
 * Returns null for unrecognized formats or missing metadata ("Other").
 */
fun Track.resolveQuality(): TrackQuality? {
    val mime = mimeType?.lowercase(Locale.ROOT)
    val lowerDisplayName = displayName.lowercase(Locale.ROOT)

    // 1. Direct DSD recognition -> HI_RES
    if (mime == "audio/dsd" || mime == "audio/x-dsd" ||
        lowerDisplayName.endsWith(".dsf") || lowerDisplayName.endsWith(".dff")
    ) {
        return TrackQuality.HI_RES
    }

    val estimatedBitrateBps =
        if (durationMs > 0 && sizeBytes > 0) {
            (sizeBytes * 8000L) / durationMs
        } else {
            null
        }

    // 2. Lossless formats (FLAC, WAV)
    if (mime == "audio/flac" || mime == "audio/x-flac" ||
        mime == "audio/wav" || mime == "audio/x-wav" ||
        lowerDisplayName.endsWith(".flac") || lowerDisplayName.endsWith(".wav")
    ) {
        return if (estimatedBitrateBps != null && estimatedBitrateBps >= 1_500_000L) {
            TrackQuality.HI_RES
        } else {
            TrackQuality.HIGH
        }
    }

    // 3. Lossy formats (MP3, AAC, MP4, OGG, OPUS)
    if (mime == "audio/mpeg" || mime == "audio/mp3" ||
        mime == "audio/aac" || mime == "audio/mp4" ||
        mime == "audio/ogg" || mime == "audio/opus" ||
        lowerDisplayName.endsWith(".mp3") || lowerDisplayName.endsWith(".aac") ||
        lowerDisplayName.endsWith(".m4a") || lowerDisplayName.endsWith(".ogg") ||
        lowerDisplayName.endsWith(".opus")
    ) {
        return if (estimatedBitrateBps != null && estimatedBitrateBps >= 256_000L) {
            TrackQuality.HIGH
        } else {
            TrackQuality.STANDARD
        }
    }

    // 4. Other unrecognized format or missing metadata
    return null
}

/**
 * Renders a compact quality badge pill with gold (HR), silver (HQ), or bronze (SQ) styling.
 */
@Composable
fun QualityBadge(
    quality: TrackQuality,
    modifier: Modifier = Modifier,
) {
    val badgeColors = qualityBadgeColors(quality)
    Surface(
        color = badgeColors.containerColor,
        shape = MusicTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(quality.labelResId),
            style = MusicTheme.typography.labelSmall,
            color = badgeColors.contentColor,
            modifier = Modifier.padding(horizontal = MusicTheme.dimensions.spaceExtraSmall),
        )
    }
}
