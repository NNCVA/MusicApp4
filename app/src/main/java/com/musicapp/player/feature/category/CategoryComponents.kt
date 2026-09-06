package com.musicapp.player.feature.category

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.designsystem.component.TrackSummaryRow
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    BareIconButton(onClick = onClick, modifier = Modifier.size(dimensions.minimumTouchTarget)) {
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
    titleStyle: TextStyle = MusicTheme.typography.titleLarge,
    trailingContent: @Composable () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.playerHeaderHeight)
            .padding(horizontal = dimensions.topBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        when {
            onBack != null ->
                CategoryNavigationIconButton(
                    action = CategoryNavigationAction.BACK,
                    onClick = onBack,
                )
            navigationAction == CategoryNavigationAction.BACK ->
                CategoryNavigationIconButton(
                    action = CategoryNavigationAction.BACK,
                    onClick = onNavigationClick,
                )
            policy == WindowLayoutPolicy.COMPACT_DRAWER && navigationAction == CategoryNavigationAction.DRAWER ->
                CategoryNavigationIconButton(
                    action = CategoryNavigationAction.DRAWER,
                    onClick = onNavigationClick,
                )
        }
        Text(
            text = title,
            style = titleStyle,
            color = MusicTheme.colors.onSurface,
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
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    LazyColumn(
        state = listState,
        overscrollEffect = overscrollEffect,
        modifier = modifier.fillMaxWidth().bounceOverscroll(overscrollEffect),
        contentPadding =
            PaddingValues(
                top = dimensions.spaceSmall,
                bottom = dimensions.spaceSmall + bottomPadding,
            ),
    ) {
        items(tracks, key = { "${it.id.volumeName}:${it.id.mediaStoreId}" }) { track ->
            TrackSummaryRow(
                title = track.title,
                artist = track.artistName,
                duration = formatDuration(track.durationMs),
                enabled = track.availability == Availability.AVAILABLE,
                statusLabel =
                    if (track.availability == Availability.TEMPORARILY_UNAVAILABLE) {
                        stringResource(R.string.track_temporarily_unavailable)
                    } else {
                        null
                    },
                outerHorizontalPadding = dimensions.contentHorizontalPadding,
                onClick = { onTrackClick(track) },
            )
        }
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
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fields.forEach { field ->
                AppDropdownMenuItem(
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
