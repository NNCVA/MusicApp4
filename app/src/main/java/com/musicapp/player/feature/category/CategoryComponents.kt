package com.musicapp.player.feature.category

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import java.util.Locale

enum class CategoryNavigationAction {
    DRAWER,
    BACK,
}

@Composable
fun CategoryNavigationIconButton(
    action: CategoryNavigationAction,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val iconResId =
        when (action) {
            CategoryNavigationAction.DRAWER -> R.drawable.ic_navigation_menu
            CategoryNavigationAction.BACK -> R.drawable.ic_navigation_back
        }
    val descriptionResId =
        when (action) {
            CategoryNavigationAction.DRAWER -> R.string.open_navigation
            CategoryNavigationAction.BACK -> R.string.category_back
        }
    IconButton(onClick = onClick, modifier = Modifier.size(dimensions.minimumTouchTarget)) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = stringResource(descriptionResId),
            tint = MusicTheme.colors.onSurface,
            modifier = Modifier.size(dimensions.spaceLarge),
        )
    }
}

@Composable
fun CategoryHeader(
    title: String,
    policy: WindowLayoutPolicy? = null,
    onBack: (() -> Unit)? = null,
    navigationAction: CategoryNavigationAction? = null,
    onNavigationClick: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.playerHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        when {
            onBack != null -> TextButton(onClick = onBack) { Text(stringResource(R.string.category_back)) }
            policy == WindowLayoutPolicy.COMPACT_DRAWER && navigationAction != null ->
                CategoryNavigationIconButton(
                    action = navigationAction,
                    onClick = onNavigationClick,
                )
        }
        Text(
            text = title,
            style = MusicTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailingContent()
    }
}

@Composable
fun CategoryTrackList(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
    ) {
        items(tracks, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { track ->
            CategoryTrackRow(track, onTrackClick)
        }
    }
}

@Composable
fun CategoryTrackRow(track: Track, onTrackClick: (Track) -> Unit) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(dimensions.trackListItemHeight)
            .clickable(enabled = track.availability == Availability.AVAILABLE) { onTrackClick(track) }
            .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MusicTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = track.artistName,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (track.availability == Availability.TEMPORARILY_UNAVAILABLE) {
            Text(
                text = stringResource(R.string.track_temporarily_unavailable),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        Text(formatDuration(track.durationMs), style = MusicTheme.typography.labelMedium)
    }
}

@Composable
fun CategoryTrackSortMenu(
    sort: CategoryTrackSort,
    fields: List<CategoryTrackSortField>,
    onSelected: (CategoryTrackSortField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(sort.field.labelRes()) + stringResource(sort.direction.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fields.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    onClick = { onSelected(field); expanded = false },
                )
            }
        }
    }
}

@StringRes
fun CategoryTrackSortField.labelRes(): Int =
    when (this) {
        CategoryTrackSortField.TITLE -> R.string.sort_title
        CategoryTrackSortField.ARTIST -> R.string.sort_artist
        CategoryTrackSortField.ALBUM -> R.string.sort_album
        CategoryTrackSortField.DATE_ADDED -> R.string.sort_date_added
        CategoryTrackSortField.DURATION -> R.string.sort_duration
    }

@StringRes
fun CategorySortDirection.labelRes(): Int =
    when (this) {
        CategorySortDirection.ASCENDING -> R.string.sort_direction_ascending
        CategorySortDirection.DESCENDING -> R.string.sort_direction_descending
    }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
