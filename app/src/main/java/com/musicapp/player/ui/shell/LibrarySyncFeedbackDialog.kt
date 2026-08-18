package com.musicapp.player.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musicapp.player.R
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.MediaLibraryScanSummary
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import com.musicapp.player.data.sync.scanResultTitle
import com.musicapp.player.theme.MusicTheme

@Composable
fun LibrarySyncFeedbackDialog(
    feedback: PendingLibrarySyncFeedback,
    onAcknowledge: (Long) -> Unit,
) {
    when (val event = feedback.event) {
        is LibrarySyncEvent.Completed -> {
            val summary = event.result.scanSummary ?: MediaLibraryScanSummary.EMPTY
            ScanResultDialog(summary = summary, onDismiss = { onAcknowledge(feedback.eventId) })
        }
        is LibrarySyncEvent.Failed ->
            AlertDialog(
                onDismissRequest = { onAcknowledge(feedback.eventId) },
                title = { Text(stringResource(R.string.scan_result_failed_title)) },
                text = { Text(stringResource(R.string.scan_error_description)) },
                confirmButton = {
                    TextButton(onClick = { onAcknowledge(feedback.eventId) }) {
                        Text(stringResource(R.string.dismiss))
                    }
                },
            )
    }
}

@Composable
private fun ScanResultDialog(
    summary: MediaLibraryScanSummary,
    onDismiss: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val successfulTitles =
        remember(summary) {
            summary.acceptedCandidates.map(MediaAudioCandidate::scanResultTitle)
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_result_title)) },
        text = {
            Column {
                Spacer(modifier = Modifier.height(dimensions.spaceSmall))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = dimensions.dialogListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    if (successfulTitles.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.scan_result_added),
                                style = MusicTheme.typography.titleSmall,
                            )
                        }
                        items(successfulTitles) { title ->
                            Text(title, style = MusicTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        },
    )
}
