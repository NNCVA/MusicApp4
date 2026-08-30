package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme

/**
 * Displays a compact, text-only track summary for category-style lists.
 *
 * The caller owns the track data, availability rules, duration formatting, and click action;
 * this component only provides the shared layout and semantics.
 */
@Composable
fun TrackSummaryRow(
    title: String,
    artist: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusLabel: String? = null,
    outerHorizontalPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.trackListItemHeight)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = outerHorizontalPadding + dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
            )
            Text(
                text = artist,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (statusLabel != null) {
            Text(
                text = statusLabel,
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        Text(
            text = duration,
            style = MusicTheme.typography.labelMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackSummaryRowPreview() {
    MusicAppTheme {
        TrackSummaryRow(
            title = "Song title",
            artist = "Artist",
            duration = "3:45",
            onClick = {},
        )
    }
}
