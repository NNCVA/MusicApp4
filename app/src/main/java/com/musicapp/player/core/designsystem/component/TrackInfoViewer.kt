package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier

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
        ModalBottomSheet(onDismissRequest = onDismiss) {
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
    val path = track.relativePath + track.displayName
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensions.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Text(
            text = stringResource(R.string.track_info_title),
            style = MusicTheme.typography.headlineMedium,
            color = MusicTheme.colors.onSurface,
        )
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        InfoRow(R.string.track_info_encoding, metadata?.encoding ?: track.mimeType.orEmpty())
        InfoRow(
            R.string.track_info_bitrate,
            metadata?.bitrateBps?.let { stringResource(R.string.track_info_bitrate_value, it / 1_000) }.orEmpty(),
        )
        InfoRow(
            R.string.track_info_sample_rate,
            metadata?.sampleRateHz?.let { stringResource(R.string.track_info_sample_rate_value, it) }.orEmpty(),
        )
        InfoRow(
            R.string.track_info_file_size,
            pluralStringResource(
                R.plurals.track_info_file_size_value,
                track.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                track.sizeBytes,
            ),
        )
        InfoRow(R.string.track_info_path, path)
        TextButton(onClick = { clipboard.setText(AnnotatedString(path)) }) {
            Text(stringResource(R.string.track_info_copy_path))
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column {
        Text(
            text = stringResource(label),
            style = MusicTheme.typography.labelMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { stringResource(R.string.track_info_unknown) },
            style = MusicTheme.typography.bodyLarge,
            color = MusicTheme.colors.onSurface,
        )
    }
}
