package com.musicapp.player.feature.tracks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.ErrorState
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.MediaLibraryScanSkipReason
import com.musicapp.player.data.sync.MediaLibraryScanSummary
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import java.util.Locale

@Composable
fun TracksScreenRoute(
    viewModel: TracksViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    BackHandler(enabled = state.isSelectionMode) { viewModel.onBack() }
    TracksScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onManualSync = viewModel::requestManualSync,
        onRetry = viewModel::retrySync,
        onSortSelected = viewModel::selectSort,
        onTrackClick = { track ->
            if (state.isSelectionMode) {
                viewModel.toggleSelection(track.id)
            } else {
                viewModel.playTrack(track.id)
            }
        },
        onTrackLongClick = { track ->
            if (!state.isSelectionMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            viewModel.toggleSelection(track.id)
        },
        onSelectAll = viewModel::selectAllCurrentResults,
        onClearSelection = viewModel::clearSelection,
        onHideSelected = viewModel::hideSelected,
        onAcknowledgeFeedback = viewModel::acknowledgeFeedback,
    )
}

@Composable
fun TracksScreen(
    state: TracksUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onManualSync: () -> Unit,
    onRetry: () -> Unit,
    onSortSelected: (TrackSortField) -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onHideSelected: () -> Unit,
    onAcknowledgeFeedback: (Long) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets)
                .padding(horizontal = dimensions.contentHorizontalPadding),
    ) {
        TracksTopBar(
            state = state,
            policy = policy,
            openDrawer = openDrawer,
            onManualSync = onManualSync,
            onSortSelected = onSortSelected,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onHideSelected = onHideSelected,
        )
        when {
            state.isInitialLoading -> ScanRadar(modifier = Modifier.weight(1f))
            state.fullScreenFailure ->
                ErrorState(
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                    description = stringResource(R.string.scan_error_description),
                )
            else -> {
                if (state.isRefreshing) {
                    val refreshingDescription = stringResource(R.string.scan_refreshing)
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth().semantics {
                                contentDescription = refreshingDescription
                            },
                    )
                }
                if (state.cachedFailure) {
                    CachedScanError(onRetry = onRetry)
                }
                if (state.tracks.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.tracks_empty_title),
                        description = stringResource(R.string.tracks_empty_description),
                    )
                } else {
                    TrackList(
                        tracks = state.tracks,
                        selectedIds = state.selectedTrackIds,
                        selectionMode = state.isSelectionMode,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    state.pendingFeedback?.let { feedback ->
        ScanFeedbackDialog(feedback, onAcknowledgeFeedback)
    }
}

@Composable
private fun TracksTopBar(
    state: TracksUiState,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onManualSync: () -> Unit,
    onSortSelected: (TrackSortField) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onHideSelected: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        if (state.isSelectionMode) {
            TextButton(onClick = onClearSelection, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.selection_close))
            }
            Text(
                text = stringResource(R.string.selection_count, state.selectedTrackIds.size),
                style = MusicTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.selection_select_all))
            }
            TextButton(onClick = onHideSelected, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.selection_hide))
            }
        } else {
            if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
                TextButton(onClick = openDrawer, shape = MusicTheme.shapes.small) {
                    Text(stringResource(R.string.tracks_menu))
                }
            }
            Text(
                text = stringResource(R.string.navigation_tracks),
                style = MusicTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(
                    onClick = { sortMenuExpanded = true },
                    shape = MusicTheme.shapes.small,
                ) {
                    Text(stringResource(state.sort.field.labelResId()))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    TrackSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = {
                                val suffix =
                                    if (field == state.sort.field) {
                                        stringResource(state.sort.direction.labelResId())
                                    } else {
                                        ""
                                    }
                                Text(stringResource(field.labelResId()) + suffix)
                            },
                            onClick = {
                                onSortSelected(field)
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onManualSync, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.scan_now))
            }
        }
    }
}

@Composable
private fun ScanRadar(modifier: Modifier = Modifier) {
    val dimensions = MusicTheme.dimensions
    val primary = MusicTheme.colors.primary
    val radarDescription = stringResource(R.string.scan_radar_accessibility)
    val transition = rememberInfiniteTransition(label = "scan-radar")
    val sweep by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1_200), RepeatMode.Restart),
            label = "scan-radar-sweep",
        )
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium, Alignment.CenterVertically),
    ) {
        Canvas(
            modifier =
                Modifier.size(dimensions.radarSize).semantics {
                    contentDescription = radarDescription
                },
        ) {
            val radius = size.minDimension / 2f
            repeat(3) { index ->
                drawCircle(
                    color = primary.copy(alpha = 0.22f + index * 0.12f),
                    radius = radius * (index + 1) / 3f,
                    style = Stroke(width = dimensions.radarStrokeWidth.toPx()),
                )
            }
            val angle = sweep * (Math.PI * 2.0)
            drawLine(
                color = primary,
                start = center,
                end = Offset(
                    x = center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                    y = center.y + (kotlin.math.sin(angle) * radius).toFloat(),
                ),
                strokeWidth = dimensions.radarStrokeWidth.toPx(),
            )
        }
        Text(
            text = stringResource(R.string.scan_first_library),
            style = MusicTheme.typography.titleLarge,
            color = MusicTheme.colors.onSurface,
        )
        Text(
            text = stringResource(R.string.scan_first_library_description),
            style = MusicTheme.typography.bodyMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CachedScanError(onRetry: () -> Unit) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MusicTheme.colors.errorContainer,
        shape = MusicTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(dimensions.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Text(
                text = stringResource(R.string.scan_cached_error),
                color = MusicTheme.colors.onErrorContainer,
                style = MusicTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, shape = MusicTheme.shapes.small) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    selectedIds: Set<com.musicapp.player.core.domain.model.TrackId>,
    selectionMode: Boolean,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
    ) {
        items(tracks, key = { track -> "${track.id.volumeName}:${track.id.mediaStoreId}" }) { track ->
            TrackRow(
                track = track,
                selected = track.id in selectedIds,
                selectionMode = selectionMode,
                onClick = { onTrackClick(track) },
                onLongClick = { onTrackLongClick(track) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val compact = dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(dimensions.trackListItemHeight)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = if (compact) MusicTheme.typography.compactTrackTitle else MusicTheme.typography.expandedTrackTitle,
                color = MusicTheme.colors.onSurface,
                maxLines = 2,
            )
            Text(
                text = track.artistName.localizedArtistName(),
                style = if (compact) MusicTheme.typography.compactTrackArtist else MusicTheme.typography.expandedTrackArtist,
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
        Text(
            text = formatDuration(track.durationMs),
            style = MusicTheme.typography.labelMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun String.localizedArtistName(): String =
    if (this == UNKNOWN_ARTIST_SENTINEL) stringResource(R.string.unknown_artist) else this

@Composable
private fun ScanFeedbackDialog(
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
    val skippedGroups = remember(summary) { summary.skippedItems.groupBy { it.reason } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_result_title)) },
        text = {
            Column {
                Text(
                    text =
                        stringResource(
                            R.string.scan_result_stats,
                            summary.queriedCandidateCount,
                            summary.acceptedCandidates.size,
                            summary.skippedItems.size,
                        ),
                    style = MusicTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(dimensions.spaceSmall))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = dimensions.dialogListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    if (successfulTitles.isNotEmpty()) {
                        item { Text(stringResource(R.string.scan_result_added), style = MusicTheme.typography.titleSmall) }
                        items(successfulTitles) { title -> Text(title, style = MusicTheme.typography.bodyMedium) }
                    }
                    skippedGroups.entries.sortedBy { it.key.ordinal }.forEach { (reason, skipped) ->
                        item {
                            Text(
                                text = stringResource(R.string.scan_result_skip_group, stringResource(reason.labelResId()), skipped.size),
                                style = MusicTheme.typography.titleSmall,
                            )
                        }
                        items(skipped) { item ->
                            Text(
                                text = item.displayName?.trim()?.takeIf(String::isNotEmpty)
                                    ?: stringResource(R.string.scan_unknown_item),
                                style = MusicTheme.typography.bodyMedium,
                            )
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

internal fun MediaAudioCandidate.scanResultTitle(): String {
    title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val normalizedDisplayName = displayName.trim()
    if (normalizedDisplayName.isNotEmpty()) {
        return normalizedDisplayName.substringBeforeLast('.').ifBlank { normalizedDisplayName }
    }
    return mediaStoreId.toString()
}

private fun TrackSortField.labelResId(): Int =
    when (this) {
        TrackSortField.TITLE -> R.string.sort_title
        TrackSortField.ARTIST -> R.string.sort_artist
        TrackSortField.ALBUM -> R.string.sort_album
        TrackSortField.DATE_ADDED -> R.string.sort_date_added
        TrackSortField.DURATION -> R.string.sort_duration
    }

private fun TrackSortDirection.labelResId(): Int =
    when (this) {
        TrackSortDirection.ASCENDING -> R.string.sort_direction_ascending
        TrackSortDirection.DESCENDING -> R.string.sort_direction_descending
    }

private fun MediaLibraryScanSkipReason.labelResId(): Int =
    when (this) {
        MediaLibraryScanSkipReason.UNSUPPORTED_FORMAT -> R.string.scan_skip_unsupported_format
        MediaLibraryScanSkipReason.NON_POSITIVE_DURATION -> R.string.scan_skip_zero_duration
        MediaLibraryScanSkipReason.SYSTEM_AUDIO -> R.string.scan_skip_system_audio
        MediaLibraryScanSkipReason.EXCLUDED_PATH -> R.string.scan_skip_excluded_path
        MediaLibraryScanSkipReason.OUTSIDE_INCLUDED_PATHS -> R.string.scan_skip_outside_included_paths
        MediaLibraryScanSkipReason.DUPLICATE_IDENTITY -> R.string.scan_skip_duplicate
        MediaLibraryScanSkipReason.UNREADABLE_ITEM -> R.string.scan_skip_unreadable
    }

private const val UNKNOWN_ARTIST_SENTINEL = "<unknown>"
