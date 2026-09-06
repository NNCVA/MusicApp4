package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackInfoViewer(
    track: Track,
    metadata: AdvancedTrackMetadata?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val compact = MusicTheme.dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    if (compact) {
        ModalBottomSheet(
            modifier = Modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
            ),
            containerColor = MusicTheme.colors.surface,
            dragHandle = null,
        ) {
            TrackInfoContent(track, metadata, loading)
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = MusicTheme.dimensions.trackInfoDialogMaxWidth),
                shape = MusicTheme.shapes.extraLarge,
            ) {
                TrackInfoContent(track, metadata, loading)
            }
        }
    }
}

@Composable
fun TrackInfoContent(
    track: Track,
    metadata: AdvancedTrackMetadata?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val clipboard = LocalClipboardManager.current
    val path = trackPath(track)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensions.spaceLarge, vertical = dimensions.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        Text(
            text = stringResource(R.string.track_info_title),
            style = MusicTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MusicTheme.colors.onSurface,
        )
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        TrackInfoCard {
            InfoRow(R.string.track_info_track_title, track.title)
            InfoRow(R.string.track_info_artist, track.artistName)
            InfoRow(R.string.track_info_album, track.albumTitle)
        }

        TrackInfoCard {
            InfoRow(R.string.track_info_media_source, track.relativePath)
            InfoRow(
                R.string.track_info_duration,
                stringResource(
                    R.string.track_info_duration_value,
                    (track.durationMs / 60_000).toInt(),
                    ((track.durationMs / 1_000) % 60).toInt(),
                ),
            )
            InfoRow(
                R.string.track_info_bitrate,
                metadata?.bitrateBps?.let { stringResource(R.string.track_info_bitrate_value, it / 1_000) },
            )
            InfoRow(
                R.string.track_info_sample_rate,
                metadata?.sampleRateHz?.let { stringResource(R.string.track_info_sample_rate_value, it) },
            )
            InfoRow(
                R.string.track_info_file_size,
                trackInfoFileSizeValue(track.sizeBytes),
            )
            InfoRow(R.string.track_info_format, trackFormat(track))
            InfoRow(R.string.track_info_path, path)
            InfoRow(R.string.track_info_encoding, metadata?.encoding ?: track.mimeType)
            InfoRow(
                R.string.track_info_bit_depth,
                metadata?.bitDepth?.let { stringResource(R.string.track_info_bit_depth_value, it) },
            )
            InfoRow(R.string.track_info_added_at, formatTrackInfoDate(track.dateAddedMs))
            InfoRow(R.string.track_info_modified_at, formatTrackInfoDate(track.dateModifiedMs))
        }

        TextButton(onClick = { clipboard.setText(AnnotatedString(path)) }) {
            Text(stringResource(R.string.track_info_copy_path))
        }
    }
}

@Composable
private fun TrackInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.extraLarge,
        color = MusicTheme.colors.surfaceContainer,
        content = {
            Column(
                modifier = Modifier.padding(MusicTheme.dimensions.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(MusicTheme.dimensions.spaceMedium),
                content = content,
            )
        },
    )
}

@Composable
private fun InfoRow(label: Int, value: String?) {
    Column {
        Text(
            text = stringResource(label),
            style = MusicTheme.typography.labelMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value?.takeIf(String::isNotBlank) ?: stringResource(R.string.track_info_unknown),
            style = MusicTheme.typography.bodyLarge,
            color = MusicTheme.colors.onSurface,
        )
    }
}

internal fun trackFormat(track: Track): String? =
    track.displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
        ?.uppercase(Locale.getDefault())
        ?: track.mimeType
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.takeIf(String::isNotBlank)
            ?.uppercase(Locale.getDefault())

internal fun trackPath(track: Track): String = buildString {
    append(track.relativePath)
    if (track.relativePath.isNotEmpty() && !track.relativePath.endsWith('/')) append('/')
    append(track.displayName)
}

private fun formatTrackInfoDate(timestampMs: Long): String? =
    timestampMs.takeIf { it > 0L }?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
    }

@Composable
private fun trackInfoFileSizeValue(sizeBytes: Long): String =
    when {
        sizeBytes >= 1_000_000_000L ->
            stringResource(
                R.string.track_info_file_size_gb_value,
                formatTrackInfoDecimal(sizeBytes / 1_000_000_000.0),
            )
        sizeBytes >= 1_000_000L ->
            stringResource(
                R.string.track_info_file_size_mb_value,
                formatTrackInfoDecimal(sizeBytes / 1_000_000.0),
            )
        sizeBytes >= 1_000L ->
            stringResource(
                R.string.track_info_file_size_kb_value,
                formatTrackInfoDecimal(sizeBytes / 1_000.0),
            )
        else ->
            pluralStringResource(
                R.plurals.track_info_file_size_value,
                sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                sizeBytes,
            )
    }

private fun formatTrackInfoDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)
