package com.musicapp.player.feature.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.CircularRippleIconButton
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import com.musicapp.player.core.designsystem.component.TrackInfoViewer
import com.musicapp.player.core.designsystem.component.localizedArtistName
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.aero.AeroRuntimeSignals
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.timer.formatRemainingTime
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.feature.lyrics.LyricsDisplayMode
import com.musicapp.player.feature.lyrics.LyricsPaneRoute
import com.musicapp.player.feature.lyrics.LyricsViewModel
import com.musicapp.player.feature.aero.AeroBackground
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow

private val PlayerSheetEmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)

@Composable
fun PlayerSheetRoute(
    viewModel: PlayerViewModel,
    lyricsViewModel: LyricsViewModel,
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    contentInsets: WindowInsets,
    isExpanded: Boolean = false,
    onExpansionChanged: (Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.currentTrack) {
        if (state.currentTrack == null && state.loadState == PlayerLoadState.EMPTY) {
            onExpansionChanged(false)
        }
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
        initialExpanded = isExpanded,
        onTogglePlayback = viewModel::togglePlayback,
        onPrevious = viewModel::skipPrevious,
        onNext = viewModel::skipNext,
        onSeek = viewModel::seekToPosition,
        onRewind = viewModel::rewind,
        onFastForward = viewModel::fastForward,
        onCycleMode = viewModel::cyclePlaybackMode,
        onJumpToQueueItem = viewModel::jumpToQueueItem,
        onRemoveQueueItem = viewModel::removeFromQueue,
        onClearQueue = viewModel::clearQueue,
        onShowInfo = viewModel::showTrackInfo,
        onDismissInfo = viewModel::dismissTrackInfo,
        onShowSleepTimer = viewModel::showSleepTimer,
        onDismissSleepTimer = viewModel::dismissSleepTimer,
        onStartSleepTimer = viewModel::startSleepTimer,
        onStopSleepTimer = viewModel::stopSleepTimer,
        onPageChanged = viewModel::selectFullPlayerPage,
        onExpansionChanged = onExpansionChanged,
        expandRequests = viewModel.expandRequests,
    )
}

@Composable
fun PlayerSheet(
    state: PlayerUiState,
    lyricsViewModel: LyricsViewModel,
    aeroMode: AeroMode,
    aeroSignals: AeroRuntimeSignals,
    contentInsets: WindowInsets,
    initialExpanded: Boolean = false,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemoveQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onShowInfo: () -> Unit,
    onDismissInfo: () -> Unit,
    onShowSleepTimer: () -> Unit = {},
    onDismissSleepTimer: () -> Unit = {},
    onStartSleepTimer: (durationMinutes: Int, extendToEndOfTrack: Boolean) -> Unit = { _, _ -> },
    onStopSleepTimer: () -> Unit = {},
    onPageChanged: (FullPlayerPage) -> Unit,
    onExpansionChanged: (Boolean) -> Unit,
    expandRequests: SharedFlow<Unit>? = null,
    onClearQueue: () -> Unit = {},
) {
    var progress by rememberSaveable {
        mutableFloatStateOf(if (initialExpanded) 1f else 0f)
    }
    var isContentMounted by rememberSaveable {
        mutableStateOf(initialExpanded)
    }
    val track = state.currentTrack ?: return
    val dimensions = MusicTheme.dimensions
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var sheetAnimationJob by remember { mutableStateOf<Job?>(null) }
    val isExpanded by remember {
        derivedStateOf { progress > 0f }
    }
    LaunchedEffect(isExpanded) { onExpansionChanged(isExpanded) }

    val bottomInset = contentInsets.asPaddingValues().calculateBottomPadding()
    val totalCollapsedHeight = dimensions.miniPlayerHeight + bottomInset

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val travelPx = with(density) { (maxHeight - totalCollapsedHeight).toPx().coerceAtLeast(1f) }
        val animateSheetTo: (Float, Float) -> Unit = { targetProgress, velocityY ->
            sheetAnimationJob?.cancel()
            if (targetProgress > 0f || progress > 0f) {
                isContentMounted = true
            }
            val settleDurationMs = PlayerSheetState.calculateSettleDurationMs(
                currentProgress = progress,
                targetProgress = targetProgress,
                velocityYPxPerSecond = velocityY,
                travelPx = travelPx,
            )
            val animationSpec = if (settleDurationMs > 0) {
                tween<Float>(
                    durationMillis = settleDurationMs,
                    easing = PlayerSheetEmphasizedEasing,
                )
            } else {
                tween<Float>(durationMillis = 0)
            }
            sheetAnimationJob = coroutineScope.launch {
                animate(
                    initialValue = progress,
                    targetValue = targetProgress,
                    animationSpec = animationSpec,
                ) { value, _ ->
                    progress = value.coerceIn(0f, 1f)
                }
                if (progress == 0f) {
                    delay(64)
                    if (progress == 0f && sheetAnimationJob?.isActive != true) {
                        isContentMounted = false
                    }
                }
            }
        }
        LaunchedEffect(expandRequests) {
            expandRequests?.collect {
                animateSheetTo(1f, 0f)
            }
        }
        val dragSheet: (Float) -> Float = { deltaY ->
            sheetAnimationJob?.cancel()
            sheetAnimationJob = null
            isContentMounted = true
            val previous = progress
            val targetProgress = PlayerSheetState(previous).dragBy(deltaY, travelPx).expansionProgress
            progress = targetProgress
            (previous - targetProgress) * travelPx
        }
        val settleSheet: (Float) -> Unit = { velocityY ->
            val targetProgress = PlayerSheetState(progress).settle(velocityY).expansionProgress
            animateSheetTo(targetProgress, velocityY)
        }
        BackHandler(enabled = isExpanded) { animateSheetTo(0f, 0f) }
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
                .offsetPx { (1f - progress) * travelPx },
            shape = RectangleShape,
            color = MusicTheme.colors.surfaceContainer,
            tonalElevation = dimensions.playerSheetElevation,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MiniPlayer(
                    state = state,
                    track = track,
                    isVisible = PlayerLayerAlpha.mini(progress) > 0f,
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
                if (isContentMounted || progress > 0f) {
                    AeroBackground(
                        preferredMode = aeroMode,
                        signals = aeroSignals,
                        artwork = (state.artwork as? ArtworkResult.Embedded)?.image,
                        mixArtworkColors = true,
                        isPlaying = state.isPlaying,
                        isVisible = progress > 0f,
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
                            onClearQueue = onClearQueue,
                            onShowInfo = onShowInfo,
                            onShowSleepTimer = onShowSleepTimer,
                            initialPage = state.fullPlayerPage,
                            onPageChanged = onPageChanged,
                            onSheetDrag = dragSheet,
                            onSheetSettle = settleSheet,
                            sheetProgress = { progress },
                        )
                    }
                }
            }
        }
    }
    if (state.showTrackInfo) {
        TrackInfoViewer(track, state.metadata, state.metadataLoading, onDismissInfo)
    }
    if (state.showSleepTimer) {
        SleepTimerSheet(
            sleepTimerStatus = state.sleepTimer,
            initialDurationMinutes = state.savedSleepTimerDurationMinutes,
            initialExtendToEndOfTrack = state.savedSleepTimerExtendToEndOfTrack,
            onStart = onStartSleepTimer,
            onStop = onStopSleepTimer,
            onDismiss = onDismissSleepTimer,
        )
    }
}

private fun Modifier.offsetPx(y: () -> Float): Modifier =
    this.offset { IntOffset(0, y().roundToInt()) }

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    track: Track,
    isVisible: Boolean,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val compact = dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.miniPlayerHeight)
            .clickable(onClick = onExpand)
            .padding(horizontal = dimensions.contentHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        PlayerArtwork(
            artwork = state.artwork,
            trackId = track.id,
            artworkTrackId = state.artworkTrackId,
            isVisible = isVisible,
            shape = RoundedCornerShape(dimensions.spaceExtraSmall),
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
                text = track.artistName.localizedArtistName(),
                style = if (compact) MusicTheme.typography.compactTrackArtist else MusicTheme.typography.expandedTrackArtist,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemoveQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onClearQueue: () -> Unit,
    onShowInfo: () -> Unit,
    onShowSleepTimer: () -> Unit = {},
    initialPage: FullPlayerPage,
    onPageChanged: (FullPlayerPage) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    sheetProgress: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialPage.ordinal, pageCount = { FullPlayerPage.entries.size })
    val lyricsUiState by lyricsViewModel.uiState.collectAsStateWithLifecycle()
    val isScrollableContentActive = when (FullPlayerPage.entries[pager.currentPage]) {
        FullPlayerPage.QUEUE -> true
        FullPlayerPage.LYRICS -> lyricsUiState.mode == LyricsDisplayMode.SYNCHRONIZED
        FullPlayerPage.ARTWORK -> false
    }
    val artworkContent = remember {
        movableContentOf { artworkModifier: Modifier, artworkTrack: Track, artworkIsPlaying: Boolean, artworkIsVisible: Boolean ->
            RotatingArtworkDisc(
                track = artworkTrack,
                isPlaying = artworkIsPlaying,
                isVisible = artworkIsVisible,
                modifier = artworkModifier,
            )
        }
    }
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

    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    val showFeedback: (String) -> Unit = { msg ->
        feedbackMessage = msg
        feedbackJob?.cancel()
        feedbackJob = coroutineScope.launch {
            kotlinx.coroutines.delay(2000L)
            feedbackMessage = null
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().windowInsetsPadding(contentInsets),
    ) {
        val isLandscape = PlayerResponsivePolicy.isLandscape(maxWidth, maxHeight)
        if (isLandscape) {
            PlayerLandscapeContent(
                state = state,
                lyricsViewModel = lyricsViewModel,
                track = track,
                pager = pager,
                artworkContent = artworkContent,
                isScrollableContentActive = isScrollableContentActive,
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                onCycleMode = onCycleMode,
                onJumpToQueueItem = onJumpToQueueItem,
                onRemoveQueueItem = onRemoveQueueItem,
                onClearQueue = onClearQueue,
                onShowInfo = onShowInfo,
                onShowSleepTimer = onShowSleepTimer,
                showFeedback = showFeedback,
                onSheetDrag = onSheetDrag,
                onSheetSettle = onSheetSettle,
                sheetProgress = sheetProgress,
            )
        } else {
            PortraitFullPlayer(
                state = state,
                lyricsViewModel = lyricsViewModel,
                track = track,
                pager = pager,
                artworkContent = artworkContent,
                isScrollableContentActive = isScrollableContentActive,
                backgroundDragState = backgroundDragState,
                pagerVerticalDragState = pagerVerticalDragState,
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                onCycleMode = onCycleMode,
                onJumpToQueueItem = onJumpToQueueItem,
                onRemoveQueueItem = onRemoveQueueItem,
                onClearQueue = onClearQueue,
                onShowInfo = onShowInfo,
                onShowSleepTimer = onShowSleepTimer,
                showFeedback = showFeedback,
                onSheetDrag = onSheetDrag,
                onSheetSettle = onSheetSettle,
                sheetProgress = sheetProgress,
            )
        }
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimensions.minimumTouchTarget + dimensions.spaceSmall),
        ) {
            feedbackMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(dimensions.spaceMedium),
                    color = MusicTheme.colors.inverseSurface.copy(alpha = 0.88f),
                    tonalElevation = dimensions.playerSheetElevation,
                ) {
                    Text(
                        text = msg,
                        style = MusicTheme.typography.bodySmall,
                        color = MusicTheme.colors.inverseOnSurface,
                        modifier = Modifier.padding(
                            horizontal = dimensions.spaceMedium,
                            vertical = dimensions.spaceSmallMedium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitFullPlayer(
    state: PlayerUiState,
    lyricsViewModel: LyricsViewModel,
    track: Track,
    pager: androidx.compose.foundation.pager.PagerState,
    artworkContent: @Composable (Modifier, Track, Boolean, Boolean) -> Unit,
    isScrollableContentActive: Boolean,
    backgroundDragState: androidx.compose.foundation.gestures.DraggableState,
    pagerVerticalDragState: androidx.compose.foundation.gestures.DraggableState,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemoveQueueItem: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onClearQueue: () -> Unit,
    onShowInfo: () -> Unit,
    onShowSleepTimer: () -> Unit,
    showFeedback: (String) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    sheetProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensions.spaceLarge,
                    end = dimensions.spaceLarge,
                    top = dimensions.spaceLarge,
                    bottom = dimensions.spaceSmall,
                )
                .draggable(
                    state = backgroundDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            PlayerTrackHeader(
                track = track,
                direction = state.slideDirection,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = dimensions.contentHorizontalPadding)
                .draggable(
                    state = pagerVerticalDragState,
                    orientation = Orientation.Vertical,
                    enabled = !isScrollableContentActive,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
        ) { page ->
            when (FullPlayerPage.entries[page]) {
                FullPlayerPage.ARTWORK -> ArtworkPage(
                    state = state,
                    track = track,
                    artworkContent = artworkContent,
                    isVisible = pager.currentPage == FullPlayerPage.ARTWORK.ordinal,
                )
                FullPlayerPage.LYRICS -> LyricsPaneRoute(
                    viewModel = lyricsViewModel,
                    missingText = stringResource(R.string.lyrics_not_found),
                    loadingText = stringResource(R.string.lyrics_loading),
                    onSheetDrag = onSheetDrag,
                    onSheetSettle = onSheetSettle,
                    sheetProgress = sheetProgress,
                )
                FullPlayerPage.QUEUE -> QueuePage(
                    rows = state.queue,
                    playbackMode = state.playbackMode,
                    onCycleMode = onCycleMode,
                    onJump = onJumpToQueueItem,
                    onRemove = onRemoveQueueItem,
                    onClearQueue = onClearQueue,
                    onSheetDrag = onSheetDrag,
                    onSheetSettle = onSheetSettle,
                    sheetProgress = sheetProgress,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
        ) {
            PlayerStatus(state.loadState, state.errorMessageRes)
        }
        InteractiveThinProgressBar(
            trackId = track.id,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = state.durationMs > 0,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
        )
        Spacer(Modifier.height(dimensions.spaceSmall))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.contentHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val previousDescription = stringResource(R.string.playback_previous)
            CircularRippleIconButton(
                onClick = onPrevious,
                enabled = state.canSkipPrevious,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playback_skip_previous),
                    contentDescription = previousDescription,
                    modifier = Modifier.size(36.dp),
                )
            }
            val playbackDescription =
                stringResource(if (state.isPlaying) R.string.playback_pause else R.string.playback_play)
            CircularRippleIconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    painter = painterResource(if (state.isPlaying) R.drawable.ic_playback_pause else R.drawable.ic_playback_play),
                    contentDescription = playbackDescription,
                    modifier = Modifier.size(48.dp),
                )
            }
            val nextDescription = stringResource(R.string.playback_next)
            CircularRippleIconButton(
                onClick = onNext,
                enabled = state.canSkipNext,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playback_skip_next),
                    contentDescription = nextDescription,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.height(dimensions.spaceSmall))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensions.contentHorizontalPadding,
                    vertical = dimensions.spaceSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
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
            BareIconButton(
                onClick = onShowSleepTimer,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                if (state.sleepTimer != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playback_sleep_timer),
                            contentDescription = stringResource(R.string.playback_sleep_timer),
                            tint = MusicTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = state.sleepTimer.formatRemainingTime(),
                            style = MusicTheme.typography.labelSmall,
                            color = MusicTheme.colors.primary,
                            maxLines = 1,
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_playback_sleep_timer),
                        contentDescription = stringResource(R.string.playback_sleep_timer),
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }
            val equalizerComingSoon = stringResource(R.string.playback_equalizer_coming_soon)
            BareIconButton(
                onClick = { showFeedback(equalizerComingSoon) },
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sidebar_equalizer),
                    contentDescription = stringResource(R.string.playback_equalizer),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
            BareIconButton(
                onClick = {
                    coroutineScope.launch {
                        pager.animateScrollToPage(FullPlayerPage.QUEUE.ordinal)
                    }
                },
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_view_list),
                    contentDescription = stringResource(R.string.playback_queue),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
            BareIconButton(
                onClick = onShowInfo,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_more_horizontal),
                    contentDescription = stringResource(R.string.playback_more_options),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
    }
}

@Composable
internal fun InteractiveThinProgressBar(
    trackId: TrackId,
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var seekState by remember(trackId, durationMs) { mutableStateOf(PlayerSeekState()) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val isDragging = seekState.dragFraction != null
    val pending = seekState.pending?.takeIf {
        it.trackId == trackId && it.durationMs == durationMs
    }
    val pendingRequestId = seekState.pending?.requestId

    LaunchedEffect(trackId, durationMs, positionMs, pendingRequestId) {
        pendingRequestId?.let { requestId ->
            seekState = seekState.acknowledgePosition(requestId, trackId, durationMs, positionMs)
        }
    }
    LaunchedEffect(trackId, durationMs, pendingRequestId) {
        val requestId = pendingRequestId ?: return@LaunchedEffect
        delay(PlayerSeekPolicy.TIMEOUT_MS)
        seekState = seekState.timeout(requestId)
    }

    val currentPositionMs = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
    val currentDragFraction = seekState.dragFraction
    val displayedPositionMs = when {
        currentDragFraction != null && durationMs > 0 ->
            kotlin.math.round(currentDragFraction.toDouble() * durationMs).toLong().coerceIn(0, durationMs)
        pending != null -> pending.targetMs
        else -> currentPositionMs
    }
    val displayFraction = if (durationMs > 0) {
        (displayedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 5.dp else 2.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "trackHeight",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbRadius",
    )

    val activeColor = MusicTheme.colors.onSurface
    val inactiveColor = MusicTheme.colors.onSurface.copy(alpha = 0.2f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
                }
                .pointerInput(enabled, durationMs, trackId) {
                    if (!enabled || durationMs <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width
                        if (width <= 0) return@awaitEachGesture
                        val downFraction = (down.position.x / width).coerceIn(0f, 1f)
                        seekState = seekState.beginDrag(downFraction)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        var lastHapticFraction = downFraction
                        val pointer = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                                if (change.pressed) {
                                    val newFraction = (change.position.x / width).coerceIn(0f, 1f)
                                    if (kotlin.math.abs(newFraction - lastHapticFraction) >= 0.015f) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticFraction = newFraction
                                    }
                                    seekState = seekState.updateDrag(newFraction)
                                    change.consume()
                                } else {
                                    change.consume()
                                    seekState.dragFraction?.let {
                                        val commit = seekState.commit(
                                            trackId = trackId,
                                            durationMs = durationMs,
                                            baselineMs = latestPositionMs,
                                        )
                                        seekState = commit.state
                                        latestOnSeek(commit.targetMs)
                                    }
                                    break
                                }
                            }
                        } finally {
                            seekState = seekState.cancelDrag()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val centerY = size.height / 2f
                val strokeWidthPx = trackHeight.toPx()
                val thumbRadiusPx = thumbRadius.toPx()
                val startX = 0f
                val endX = size.width
                val progressX = (endX * displayFraction).coerceIn(0f, endX)

                drawLine(
                    color = inactiveColor,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )

                if (progressX > 0f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(startX, centerY),
                        end = Offset(progressX, centerY),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round,
                    )
                }

                if (thumbRadiusPx > 0f) {
                    drawCircle(
                        color = activeColor,
                        radius = thumbRadiusPx,
                        center = Offset(progressX, centerY),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displayedPositionMs),
                style = MusicTheme.typography.labelMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MusicTheme.typography.labelMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtworkPage(
    state: PlayerUiState,
    track: Track,
    artworkContent: @Composable (Modifier, Track, Boolean, Boolean) -> Unit,
    isVisible: Boolean = true,
) {
    val dimensions = MusicTheme.dimensions
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        artworkContent(
            Modifier.size(dimensions.fullPlayerArtworkSize),
            track,
            state.isPlaying,
            isVisible,
        )
    }
}

@Composable
internal fun QueuePage(
    rows: List<PlayerQueueRow>,
    playbackMode: PlaybackMode,
    onCycleMode: () -> Unit,
    onJump: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onRemove: (com.musicapp.player.core.domain.model.QueueItemId) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    sheetProgress: () -> Float = { 1f },
    onClearQueue: () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect =
        rememberBounceOverscrollEffect(
            state = listState,
            allowStartEdge = true,
        )
    val nestedScrollConnection = rememberPlayerSheetNestedScrollConnection(
        canScrollBackward = { listState.canScrollBackward },
        sheetProgress = sheetProgress,
        onSheetDrag = onSheetDrag,
        onSheetSettle = onSheetSettle,
        isScrollInProgress = { listState.isScrollInProgress },
        isOverscrollInProgress = { overscrollEffect.isInProgress },
    )
    val headerDragState = rememberDraggableState { deltaY ->
        PlayerGestureRouter.routeSheetDrag(
            region = PlayerGestureRegion.SHEET_BACKGROUND,
            deltaX = 0f,
            deltaY = deltaY,
            dragSheet = onSheetDrag,
        )
    }

    val currentIndex = remember(rows) { rows.indexOfFirst { it.isCurrent } }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    val counterText = remember(rows.size, currentIndex) {
        if (rows.isEmpty()) {
            "- / 0"
        } else if (currentIndex >= 0) {
            "${currentIndex + 1} / ${rows.size}"
        } else {
            "- / ${rows.size}"
        }
    }
    var showClearQueueDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensions.minimumTouchTarget)
                .draggable(
                    state = headerDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = counterText,
                    style = MusicTheme.typography.bodyMedium,
                    color = MusicTheme.colors.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.player_queue_title),
                style = MusicTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MusicTheme.colors.onSurface,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (rows.isNotEmpty()) {
                    BareIconButton(
                        onClick = { showClearQueueDialog = true },
                        modifier = Modifier.size(dimensions.minimumTouchTarget),
                    ) {
                        Text(
                            text = stringResource(R.string.player_queue_clear),
                            style = MusicTheme.typography.labelSmall,
                            color = MusicTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(dimensions.spaceExtraSmall))

        LazyColumn(
            state = listState,
            overscrollEffect = overscrollEffect,
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            contentPadding = PaddingValues(vertical = dimensions.spaceExtraSmall),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .bounceOverscroll(overscrollEffect)
                .nestedScroll(nestedScrollConnection),
        ) {
            if (rows.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.player_queue_empty),
                            color = MusicTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(rows, key = { it.queueItemId.value }) { row ->
                    QueueItemRow(
                        row = row,
                        onJump = { onJump(row.queueItemId) },
                        onRemove = { onRemove(row.queueItemId) },
                    )
                }
            }
        }
    }
    if (showClearQueueDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.player_queue_clear_confirm_title),
            text = stringResource(R.string.player_queue_clear_confirm_description),
            confirmLabel = stringResource(R.string.player_queue_clear_confirm_action),
            cancelLabel = stringResource(R.string.player_queue_clear_cancel),
            onConfirm = {
                showClearQueueDialog = false
                onClearQueue()
            },
            onDismiss = { showClearQueueDialog = false },
            isDestructive = true,
        )
    }
}

@Composable
private fun QueueItemRow(
    row: PlayerQueueRow,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val backgroundColor = if (row.isCurrent) {
        MusicTheme.colors.onSurface.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val itemShape = RoundedCornerShape(dimensions.spaceSmallMedium)

    val artist = row.track?.artistName?.localizedArtistName() ?: stringResource(R.string.unknown_artist)
    val album = row.track?.albumTitle
    val subtitle = if (!album.isNullOrBlank()) {
        "$artist - $album"
    } else {
        artist
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(backgroundColor)
            .clickable(onClick = onJump)
            .padding(
                start = dimensions.spaceMedium,
                end = dimensions.spaceSmall,
                top = dimensions.spaceSmall,
                bottom = dimensions.spaceSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.track?.title ?: stringResource(R.string.player_unknown_track),
                style = MusicTheme.typography.bodyLarge,
                fontWeight = if (row.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BareIconButton(
            onClick = onRemove,
            modifier = Modifier.size(dimensions.minimumTouchTarget),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_remove),
                contentDescription = stringResource(R.string.player_queue_remove),
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
    }
}

@Composable
private fun PlayerArtwork(
    artwork: ArtworkResult,
    trackId: TrackId,
    artworkTrackId: TrackId?,
    isVisible: Boolean,
    shape: Shape,
    modifier: Modifier,
) {
    val artworkDescription = stringResource(R.string.player_artwork_description)
    val animationScope = rememberCoroutineScope()
    val animationsEnabled = animationScope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    val transitionProgress = remember { Animatable(1f) }

    var observedTrackId by remember { mutableStateOf<TrackId?>(null) }
    var observedArtwork by remember { mutableStateOf<ArtworkResult?>(null) }
    var observedArtworkTrackId by remember { mutableStateOf<TrackId?>(null) }
    var displayedTrackId by remember { mutableStateOf(trackId) }
    var displayedArtwork by remember {
        mutableStateOf(
            if (artworkTrackId == trackId) artwork else ArtworkResult.Placeholder,
        )
    }
    var pendingTrackId by remember { mutableStateOf<TrackId?>(null) }
    var pendingArtwork by remember { mutableStateOf<ArtworkResult?>(null) }

    LaunchedEffect(trackId, artwork, artworkTrackId, isVisible, animationsEnabled) {
        val previousTrackId = observedTrackId
        val previousArtworkTrackId = observedArtworkTrackId
        val artworkChanged = observedArtwork !== artwork || previousArtworkTrackId != artworkTrackId
        val artworkReady = artworkTrackId == trackId
        val isInitialComposition = previousTrackId == null
        val isTrackChange = previousTrackId != null && previousTrackId != trackId

        observedTrackId = trackId
        observedArtwork = artwork
        observedArtworkTrackId = artworkTrackId

        suspend fun commitPending() {
            val nextArtwork = pendingArtwork ?: return
            val nextTrackId = pendingTrackId ?: return
            displayedTrackId = nextTrackId
            displayedArtwork = nextArtwork
            pendingTrackId = null
            pendingArtwork = null
            transitionProgress.snapTo(1f)
        }

        suspend fun animatePending() {
            val nextArtwork = pendingArtwork ?: return
            val nextTrackId = pendingTrackId ?: return
            if (!MiniArtworkMotion.shouldAnimate(
                    previousTrackId = displayedTrackId,
                    targetTrackId = nextTrackId,
                    artworkReady = true,
                    isVisible = isVisible,
                    animationsEnabled = animationsEnabled,
                )
            ) {
                commitPending()
                return
            }

            transitionProgress.stop()
            transitionProgress.snapTo(0f)
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MiniArtworkMotion.DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            // LaunchedEffect cancellation prevents a stale target from being committed
            // after a newer song or visibility state arrives.
            if (pendingTrackId == nextTrackId && pendingArtwork === nextArtwork) {
                commitPending()
            }
        }

        if (isInitialComposition) {
            displayedTrackId = trackId
            displayedArtwork = if (artworkReady) artwork else ArtworkResult.Placeholder
            pendingTrackId = null
            pendingArtwork = null
            transitionProgress.snapTo(1f)
            return@LaunchedEffect
        }

        if (isTrackChange) {
            // Promote the previous target before retargeting so a rapid skip never
            // flashes back to an older base image.
            if (pendingTrackId != null && pendingArtwork != null) {
                commitPending()
            }
            pendingTrackId = trackId
            pendingArtwork = artwork.takeIf { artworkReady }
            transitionProgress.stop()
            if (pendingArtwork != null) {
                animatePending()
            }
            return@LaunchedEffect
        }

        if (pendingTrackId == trackId) {
            if (artworkReady) {
                pendingArtwork = artwork
            }
            if (pendingArtwork != null) {
                if (!isVisible || !animationsEnabled) {
                    commitPending()
                } else {
                    animatePending()
                }
            }
            return@LaunchedEffect
        }

        // The first artwork load for the currently displayed song is not a song
        // change, so make it available without animating the mini player.
        if (displayedTrackId == trackId && artworkReady && artworkChanged) {
            displayedArtwork = artwork
        }
    }

    val incomingArtwork = pendingArtwork
    val progress = transitionProgress.value
    Box(
        modifier = modifier
            .clip(shape)
            .background(MusicTheme.colors.secondaryContainer)
            .semantics { contentDescription = artworkDescription },
    ) {
        MiniArtworkImage(
            artwork = displayedArtwork,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (incomingArtwork == null) 1f else MiniArtworkMotion.outgoingAlpha(progress)
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        )
        if (incomingArtwork != null) {
            MiniArtworkImage(
                artwork = incomingArtwork,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (MiniArtworkMotion.blurRadiusDp(progress) > 0f) {
                            Modifier.blur(MiniArtworkMotion.blurRadiusDp(progress).dp)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        alpha = MiniArtworkMotion.incomingAlpha(progress)
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            )
        }
    }
}

@Composable
private fun MiniArtworkImage(
    artwork: ArtworkResult,
    modifier: Modifier,
) {
    val placeholder = painterResource(R.drawable.ic_playlist_album)
    val embeddedImage = (artwork as? ArtworkResult.Embedded)?.image
    val imageBitmap = remember(embeddedImage) {
        embeddedImage?.let { image ->
            Bitmap.createBitmap(
                image.argbPixels,
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888,
            ).asImageBitmap()
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = placeholder,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

internal object MiniArtworkMotion {
    const val DURATION_MS = 500
    const val MAX_BLUR_DP = 20f

    fun shouldAnimate(
        previousTrackId: TrackId?,
        targetTrackId: TrackId,
        artworkReady: Boolean,
        isVisible: Boolean,
        animationsEnabled: Boolean,
    ): Boolean =
        previousTrackId != null &&
            previousTrackId != targetTrackId &&
            artworkReady &&
            isVisible &&
            animationsEnabled

    fun incomingAlpha(progress: Float): Float = progress.coerceIn(0f, 1f)

    fun outgoingAlpha(progress: Float): Float = 1f - incomingAlpha(progress)

    fun blurRadiusDp(progress: Float): Float = MAX_BLUR_DP * (1f - incomingAlpha(progress))
}

@Composable
internal fun PlayerStatus(status: PlayerLoadState, @androidx.annotation.StringRes errorMessageRes: Int?) {
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
        PlayerLoadState.ERROR -> Text(
            stringResource(errorMessageRes ?: R.string.player_error_unknown),
            color = MusicTheme.colors.error,
        )
        PlayerLoadState.BUFFERING, PlayerLoadState.EMPTY, PlayerLoadState.READY ->
            Spacer(Modifier.height(MusicTheme.dimensions.spaceSmall))
    }
}

internal fun PlaybackMode.labelRes() = when (this) {
    PlaybackMode.LIST_REPEAT -> R.string.playback_mode_list_repeat
    PlaybackMode.SINGLE_REPEAT -> R.string.playback_mode_single_repeat
    PlaybackMode.SHUFFLE -> R.string.playback_mode_shuffle
}

internal fun PlaybackMode.iconRes() = when (this) {
    PlaybackMode.LIST_REPEAT -> R.drawable.ic_playback_repeat
    PlaybackMode.SINGLE_REPEAT -> R.drawable.ic_playback_repeat_one
    PlaybackMode.SHUFFLE -> R.drawable.ic_playback_shuffle
}

internal fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
internal fun rememberPlayerSheetNestedScrollConnection(
    canScrollBackward: () -> Boolean,
    sheetProgress: () -> Float,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    onPreUserScroll: (() -> Unit)? = null,
    isScrollInProgress: () -> Boolean = { false },
    isOverscrollInProgress: () -> Boolean = { false },
): NestedScrollConnection {
    val coordinator = remember { ScrollableContentDebounceState() }
    val currentIsScrollInProgress by rememberUpdatedState(isScrollInProgress)
    val currentIsOverscrollInProgress by rememberUpdatedState(isOverscrollInProgress)

    val isMoving = currentIsScrollInProgress() || currentIsOverscrollInProgress()
    LaunchedEffect(isMoving) {
        coordinator.onMovementChanged(isMoving)
    }

    return remember(canScrollBackward, sheetProgress, onSheetDrag, onSheetSettle, onPreUserScroll, coordinator) {
        object : NestedScrollConnection {
            private var isSheetDragging = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                onPreUserScroll?.invoke()

                coordinator.onScrollDelta(available.y)

                val progress = sheetProgress()
                val isExpanded = progress >= 1f
                val canBackward = canScrollBackward()

                val canDragSheetFromContent = coordinator.canDragSheet(
                    isSheetExpanded = isExpanded,
                    canScrollBackward = canBackward,
                    isSheetDragging = isSheetDragging,
                )

                val decision = PlayerGesturePolicy.scrollableContentDecision(
                    deltaX = available.x,
                    deltaY = available.y,
                    canScrollBackward = canBackward,
                    isSheetExpanded = isExpanded,
                    isSheetDragging = isSheetDragging,
                    canDragSheetFromContent = canDragSheetFromContent,
                )

                return when (decision.behavior) {
                    QueueEdgeBehavior.SCROLL_CONTENT -> {
                        isSheetDragging = false
                        Offset.Zero
                    }
                    QueueEdgeBehavior.DRAG_SHEET -> {
                        isSheetDragging = true
                        val consumedY = onSheetDrag(available.y)
                        if (consumedY == 0f) Offset.Zero else Offset(0f, consumedY)
                    }
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasSheetDragging = isSheetDragging
                isSheetDragging = false
                coordinator.onGestureEnd()

                val progress = sheetProgress()
                val isExpanded = progress >= 1f
                val canBackward = canScrollBackward()

                val decision = PlayerGesturePolicy.scrollableContentFlingDecision(
                    velocityY = available.y,
                    canScrollBackward = canBackward,
                    isSheetExpanded = isExpanded,
                    isSheetDragging = wasSheetDragging,
                )

                return if (decision == QueueEdgeBehavior.DRAG_SHEET) {
                    onSheetSettle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isSheetDragging = false
                coordinator.onGestureEnd()
                return super.onPostFling(consumed, available)
            }
        }
    }
}
