package com.musicapp.player.feature.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import coil3.compose.AsyncImage
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.feature.lyrics.LyricsPaneRoute
import com.musicapp.player.feature.lyrics.LyricsViewModel
import com.musicapp.player.feature.aero.AeroBackground
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.Job

@Composable
fun PlayerSheetRoute(
    viewModel: PlayerViewModel,
    lyricsViewModel: LyricsViewModel,
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    contentInsets: WindowInsets,
    onExpansionChanged: (Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.currentTrack) {
        if (state.currentTrack == null) onExpansionChanged(false)
        lyricsViewModel.load(state.currentTrack)
    }
    LaunchedEffect(state.positionMs) { lyricsViewModel.updatePlaybackPosition(state.positionMs) }
    LaunchedEffect(lyricsViewModel, viewModel) {
        lyricsViewModel.seekRequests.collect(viewModel::seekToPosition)
    }
    PlayerSheet(
        state = state,
        lyricsViewModel = lyricsViewModel,
        aeroMode = aeroMode,
        aeroSignals = aeroSignals,
        contentInsets = contentInsets,
        onTogglePlayback = viewModel::togglePlayback,
        onPrevious = viewModel::skipPrevious,
        onNext = viewModel::skipNext,
        onSeek = viewModel::seekToFraction,
        onRewind = viewModel::rewind,
        onFastForward = viewModel::fastForward,
        onCycleMode = viewModel::cyclePlaybackMode,
        onJumpToQueueItem = viewModel::jumpToQueueItem,
        onRemoveQueueItem = viewModel::removeFromQueue,
        onShowInfo = viewModel::showTrackInfo,
        onDismissInfo = viewModel::dismissTrackInfo,
        onPageChanged = viewModel::selectFullPlayerPage,
        onExpansionChanged = onExpansionChanged,
    )
}

@Composable
fun PlayerSheet(
    state: PlayerUiState,
    lyricsViewModel: LyricsViewModel,
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    contentInsets: WindowInsets,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemoveQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onShowInfo: () -> Unit,
    onDismissInfo: () -> Unit,
    onPageChanged: (FullPlayerPage) -> Unit,
    onExpansionChanged: (Boolean) -> Unit,
) {
    val track = state.currentTrack ?: return
    val dimensions = MusicTheme.dimensions
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var progress by rememberSaveable { mutableFloatStateOf(0f) }
    var sheetAnimationJob by remember { mutableStateOf<Job?>(null) }
    val springSpec = remember {
        spring<Float>(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        )
    }
    val isExpanded = progress > 0f
    LaunchedEffect(isExpanded) { onExpansionChanged(isExpanded) }

    val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
    val totalCollapsedHeight = dimensions.miniPlayerHeight + bottomInset

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val travelPx = with(density) { (maxHeight - totalCollapsedHeight).toPx().coerceAtLeast(1f) }
        val animateSheetTo: (Float, Float) -> Unit = { targetProgress, initialVelocity ->
            sheetAnimationJob?.cancel()
            sheetAnimationJob = coroutineScope.launch {
                animate(
                    initialValue = progress,
                    targetValue = targetProgress,
                    initialVelocity = initialVelocity,
                    animationSpec = springSpec,
                ) { value, _ ->
                    progress = value.coerceIn(0f, 1f)
                }
            }
        }
        val dragSheet: (Float) -> Float = { deltaY ->
            sheetAnimationJob?.cancel()
            sheetAnimationJob = null
            val previous = progress
            val targetProgress = PlayerSheetState(previous).dragBy(deltaY, travelPx).expansionProgress
            progress = targetProgress
            (previous - targetProgress) * travelPx
        }
        val settleSheet: (Float) -> Unit = { velocityY ->
            val targetProgress = PlayerSheetState(progress).settle(velocityY).expansionProgress
            val initialVelocity = if (travelPx > 0f) -velocityY / travelPx else 0f
            animateSheetTo(targetProgress, initialVelocity)
        }
        BackHandler(enabled = progress > 0f) { animateSheetTo(0f, 0f) }
        val miniDragState = rememberDraggableState { deltaY ->
            PlayerGestureRouter.routeSheetDrag(
                region = PlayerGestureRegion.SHEET_BACKGROUND,
                deltaX = 0f,
                deltaY = deltaY,
                dragSheet = dragSheet,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offsetPx((1f - progress) * travelPx),
            shape = RectangleShape,
            color = MusicTheme.colors.surfaceContainer,
            tonalElevation = dimensions.playerSheetElevation,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiniPlayer(
                    state = state,
                    track = track,
                    onExpand = {
                        animateSheetTo(1f, 0f)
                    },
                    onTogglePlayback = onTogglePlayback,
                    onOpenQueue = {
                        onPageChanged(FullPlayerPage.QUEUE)
                        animateSheetTo(1f, 0f)
                    },
                    modifier = Modifier
                        .graphicsLayer { alpha = PlayerLayerAlpha.mini(progress) }
                        .draggable(
                            state = miniDragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocityY -> settleSheet(velocityY) },
                        ),
                )
                if (progress > 0f) {
                    AeroBackground(
                        preferredMode = aeroMode,
                        signals = aeroSignals,
                        artwork = (state.artwork as? ArtworkResult.Embedded)?.image,
                        mixArtworkColors = true,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { alpha = PlayerLayerAlpha.full(progress) },
                    ) {
                        FullPlayer(
                            state = state,
                            lyricsViewModel = lyricsViewModel,
                            track = track,
                            contentInsets = contentInsets,
                            onCollapse = {
                                animateSheetTo(0f, 0f)
                            },
                            onTogglePlayback = onTogglePlayback,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onSeek = onSeek,
                            onRewind = onRewind,
                            onFastForward = onFastForward,
                            onCycleMode = onCycleMode,
                            onJumpToQueueItem = onJumpToQueueItem,
                            onRemoveQueueItem = onRemoveQueueItem,
                            onShowInfo = onShowInfo,
                            initialPage = state.fullPlayerPage,
                            onPageChanged = onPageChanged,
                            onSheetDrag = dragSheet,
                            onSheetSettle = settleSheet,
                        )
                    }
                }
            }
        }
    }
    if (state.showTrackInfo) {
        TrackInfoViewer(track, state.metadata, state.metadataLoading, onDismissInfo)
    }
}

private fun Modifier.offsetPx(y: Float): Modifier =
    this.then(Modifier.offset { IntOffset(0, y.roundToInt()) })

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    track: Track,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val compact = dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    Row(
        modifier = modifier.fillMaxWidth().height(dimensions.miniPlayerHeight)
            .clickable(onClick = onExpand).padding(horizontal = dimensions.contentHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        PlayerArtwork(
            track = track,
            shape = RoundedCornerShape(dimensions.miniArtworkCornerRadius),
            modifier = Modifier.size(dimensions.trackArtworkSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = if (compact) MusicTheme.typography.compactTrackTitle else MusicTheme.typography.expandedTrackTitle,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = if (compact) MusicTheme.typography.compactTrackArtist else MusicTheme.typography.expandedTrackArtist,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.loadState == PlayerLoadState.BUFFERING) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val playbackDescription = stringResource(if (state.isPlaying) R.string.playback_pause else R.string.playback_play)
        BareIconButton(
            onClick = onTogglePlayback,
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(if (state.isPlaying) R.drawable.ic_playback_pause else R.drawable.ic_playback_play),
                contentDescription = playbackDescription,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
        BareIconButton(
            onClick = onOpenQueue,
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_view_list),
                contentDescription = stringResource(R.string.playback_queue),
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerUiState,
    lyricsViewModel: LyricsViewModel,
    track: Track,
    contentInsets: WindowInsets,
    onCollapse: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemoveQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onShowInfo: () -> Unit,
    initialPage: FullPlayerPage,
    onPageChanged: (FullPlayerPage) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val pager = rememberPagerState(initialPage = initialPage.ordinal, pageCount = { FullPlayerPage.entries.size })
    val backgroundDragState = rememberDraggableState { deltaY ->
        PlayerGestureRouter.routeSheetDrag(
            region = PlayerGestureRegion.SHEET_BACKGROUND,
            deltaX = 0f,
            deltaY = deltaY,
            dragSheet = onSheetDrag,
        )
    }
    val pagerVerticalDragState = rememberDraggableState { deltaY ->
        PlayerGestureRouter.routeSheetDrag(
            region = PlayerGestureRegion.HORIZONTAL_PAGER,
            deltaX = 0f,
            deltaY = deltaY,
            dragSheet = onSheetDrag,
        )
    }
    LaunchedEffect(initialPage) {
        if (pager.currentPage != initialPage.ordinal) {
            pager.scrollToPage(initialPage.ordinal)
        }
    }
    LaunchedEffect(pager.currentPage) { onPageChanged(FullPlayerPage.entries[pager.currentPage]) }
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(dimensions.playerHeaderHeight)
                .padding(horizontal = dimensions.topBarHorizontalPadding)
                .draggable(
                    state = backgroundDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCollapse) { Text(stringResource(R.string.player_collapse)) }
            Text(track.title, style = MusicTheme.typography.titleLarge, color = MusicTheme.colors.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onClick = onShowInfo) { Text(stringResource(R.string.track_info_title)) }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = dimensions.contentHorizontalPadding)
                .draggable(
                    state = pagerVerticalDragState,
                    orientation = Orientation.Vertical,
                    enabled = pager.currentPage != FullPlayerPage.QUEUE.ordinal,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
        ) { page ->
            when (FullPlayerPage.entries[page]) {
                FullPlayerPage.ARTWORK -> ArtworkPage(state, track)
                FullPlayerPage.LYRICS -> LyricsPaneRoute(
                    viewModel = lyricsViewModel,
                    missingText = stringResource(R.string.lyrics_not_found),
                    loadingText = stringResource(R.string.lyrics_loading),
                    returnToCurrentText = stringResource(R.string.lyrics_return_to_current),
                )
                FullPlayerPage.QUEUE -> QueuePage(
                    rows = state.queue,
                    playbackMode = state.playbackMode,
                    onCycleMode = onCycleMode,
                    onJump = onJumpToQueueItem,
                    onRemove = onRemoveQueueItem,
                    onSheetDrag = onSheetDrag,
                    onSheetSettle = onSheetSettle,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
        ) {
            PlayerStatus(state.loadState, state.errorMessageRes)
        }
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChangeFinished = {},
            onValueChange = onSeek,
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(state.positionMs), style = MusicTheme.typography.labelMedium, color = MusicTheme.colors.onSurfaceVariant)
            Text(formatDuration(state.durationMs), style = MusicTheme.typography.labelMedium, color = MusicTheme.colors.onSurfaceVariant)
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BareIconButton(
                onClick = onCycleMode,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(state.playbackMode.iconRes()),
                    contentDescription = stringResource(state.playbackMode.labelRes()),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val previousDescription = stringResource(R.string.playback_previous)
                BareIconButton(
                    onClick = onPrevious,
                    enabled = state.canSkipPrevious,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playback_skip_previous),
                        contentDescription = previousDescription,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
                val rewindDescription = stringResource(R.string.playback_rewind_10_seconds)
                TextButton(
                    onClick = onRewind,
                    enabled = state.durationMs > 0,
                    modifier = Modifier.semantics { contentDescription = rewindDescription },
                ) {
                    Text(stringResource(R.string.playback_rewind_10_seconds_short))
                }
                val playbackDescription =
                    stringResource(if (state.isPlaying) R.string.playback_pause else R.string.playback_play)
                FilledIconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(if (state.isPlaying) R.drawable.ic_playback_pause else R.drawable.ic_playback_play),
                        contentDescription = playbackDescription,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
                val forwardDescription = stringResource(R.string.playback_forward_10_seconds)
                TextButton(
                    onClick = onFastForward,
                    enabled = state.durationMs > 0,
                    modifier = Modifier.semantics { contentDescription = forwardDescription },
                ) {
                    Text(stringResource(R.string.playback_forward_10_seconds_short))
                }
                val nextDescription = stringResource(R.string.playback_next)
                BareIconButton(
                    onClick = onNext,
                    enabled = state.canSkipNext,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playback_skip_next),
                        contentDescription = nextDescription,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkPage(state: PlayerUiState, track: Track) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceLarge, Alignment.CenterVertically),
    ) {
        PlayerArtwork(
            track = track,
            shape = CircleShape,
            modifier = Modifier.size(dimensions.fullPlayerArtworkSize),
        )
        Text(track.title, style = MusicTheme.typography.headlineMedium, color = MusicTheme.colors.onSurface, maxLines = 2)
        Text(track.artistName, style = MusicTheme.typography.titleMedium, color = MusicTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun QueuePage(
    rows: List<PlayerQueueRow>,
    playbackMode: PlaybackMode,
    onCycleMode: () -> Unit,
    onJump: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemove: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect =
        rememberBounceOverscrollEffect(
            state = listState,
            allowStartEdge = false,
        )
    val nestedScrollConnection = remember(listState, onSheetDrag, onSheetSettle) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val consumedY = PlayerGestureRouter.routeQueueDrag(
                    deltaX = available.x,
                    deltaY = available.y,
                    canScrollBackward = listState.canScrollBackward,
                    dragSheet = onSheetDrag,
                )
                return if (consumedY == 0f) Offset.Zero else Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (
                    PlayerGesturePolicy.queueFlingDecision(
                        velocityY = available.y,
                        canScrollBackward = listState.canScrollBackward,
                    ) == QueueEdgeBehavior.DRAG_SHEET
                ) {
                    onSheetSettle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && !listState.canScrollBackward) {
                    onSheetSettle(available.y)
                    return available
                }
                return super.onPostFling(consumed, available)
            }
        }
    }
    val headerDragState = rememberDraggableState { deltaY ->
        PlayerGestureRouter.routeSheetDrag(
            region = PlayerGestureRegion.SHEET_BACKGROUND,
            deltaX = 0f,
            deltaY = deltaY,
            dragSheet = onSheetDrag,
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(dimensions.minimumTouchTarget)
                .draggable(
                    state = headerDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_queue_title),
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            BareIconButton(
                onClick = onCycleMode,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(playbackMode.iconRes()),
                    contentDescription = stringResource(playbackMode.labelRes()),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
        LazyColumn(
            state = listState,
            overscrollEffect = overscrollEffect,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .nestedScroll(nestedScrollConnection),
        ) {
            if (rows.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.player_queue_empty),
                            color = MusicTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(rows, key = { it.queueItemId.value }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(dimensions.trackListItemHeight)
                            .clickable { onJump(row.queueItemId) }
                            .padding(horizontal = dimensions.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.track?.title ?: stringResource(R.string.player_unknown_track),
                                style = MusicTheme.typography.titleMedium,
                                color = MusicTheme.colors.onSurface,
                                maxLines = 1,
                            )
                            if (row.isCurrent) {
                                Text(
                                    stringResource(R.string.player_queue_current),
                                    style = MusicTheme.typography.labelSmall,
                                    color = MusicTheme.colors.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onRemove(row.queueItemId) }) { Text(stringResource(R.string.player_queue_remove)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PlayerArtwork(track: Track?, shape: Shape, modifier: Modifier) {
    val artworkDescription = stringResource(R.string.player_artwork_description)
    AsyncImage(
        model = track,
        contentDescription = artworkDescription,
        modifier = modifier
            .clip(shape)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}

@Composable
private fun PlayerStatus(status: PlayerLoadState, @androidx.annotation.StringRes errorMessageRes: Int?) {
    when (status) {
        PlayerLoadState.PREPARING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(MusicTheme.dimensions.statusIndicatorSize))
            Text(
                stringResource(R.string.player_preparing),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurface,
                modifier = Modifier.padding(start = MusicTheme.dimensions.spaceSmall),
            )
        }
        PlayerLoadState.BUFFERING -> Text(
            stringResource(R.string.player_buffering),
            style = MusicTheme.typography.bodyMedium,
            color = MusicTheme.colors.onSurface,
        )
        PlayerLoadState.ERROR -> Text(
            stringResource(errorMessageRes ?: R.string.player_error_unknown),
            color = MusicTheme.colors.error,
        )
        PlayerLoadState.EMPTY, PlayerLoadState.READY -> Spacer(Modifier.height(MusicTheme.dimensions.spaceSmall))
    }
}

private fun PlaybackMode.labelRes() = when (this) {
    PlaybackMode.LIST_REPEAT -> R.string.playback_mode_list_repeat
    PlaybackMode.SINGLE_REPEAT -> R.string.playback_mode_single_repeat
    PlaybackMode.SHUFFLE -> R.string.playback_mode_shuffle
}

private fun PlaybackMode.iconRes() = when (this) {
    PlaybackMode.LIST_REPEAT -> R.drawable.ic_playback_repeat
    PlaybackMode.SINGLE_REPEAT -> R.drawable.ic_playback_repeat_one
    PlaybackMode.SHUFFLE -> R.drawable.ic_playback_shuffle
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
