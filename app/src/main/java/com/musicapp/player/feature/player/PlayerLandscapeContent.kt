package com.musicapp.player.feature.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.CircularRippleIconButton
import com.musicapp.player.core.designsystem.component.localizedArtistName
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.lyrics.LyricsPaneRoute
import com.musicapp.player.feature.lyrics.LyricsViewModel
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.launch

/**
 * 播放详情页响应式双栏布局（横屏 / 宽屏模式）。
 *
 * 遵循 ADR-0019 架构决策：
 * - 左侧：大圆形黑胶旋转唱片（[RotatingArtworkDisc]），居中自适应高度动态缩放；
 * - 右侧：3 页 [HorizontalPager]（Page 0 播控、Page 1 同步歌词、Page 2 播放队列），
 *   支持横向手势平滑切换，不添加额外“返回播控”按钮；
 * - 左右两侧均支持垂直下拉手势收起播放详情页。
 */
@Composable
internal fun PlayerLandscapeContent(
    state: PlayerUiState,
    lyricsViewModel: LyricsViewModel,
    track: Track,
    pager: PagerState,
    artworkContent: @Composable (Modifier, Track, Boolean, Boolean) -> Unit = { artworkModifier, artworkTrack, artworkIsPlaying, artworkIsVisible ->
        RotatingArtworkDisc(
            track = artworkTrack,
            isPlaying = artworkIsPlaying,
            isVisible = artworkIsVisible,
            modifier = artworkModifier,
        )
    },
    isScrollableContentActive: Boolean,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onJumpToQueueItem: (QueueItemId) -> Unit,
    onRemoveQueueItem: (QueueItemId) -> Unit,
    onShowInfo: () -> Unit,
    showFeedback: (String) -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetSettle: (Float) -> Unit,
    sheetProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val dimensions = MusicTheme.dimensions

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

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左栏：大黑胶唱片旋转封面，垂直下拉手势支持收起
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .draggable(
                    state = backgroundDragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val discDiameter = PlayerResponsivePolicy.calculateArtworkDiscSize(
                    width = maxWidth,
                    height = maxHeight,
                    maxSize = dimensions.fullPlayerArtworkSize,
                )

                artworkContent(Modifier.size(discDiameter), track, state.isPlaying, true)
            }
        }

        // 右栏：多视图 Pager（Page 0 播控、Page 1 同步歌词、Page 2 队列）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = pagerVerticalDragState,
                        orientation = Orientation.Vertical,
                        enabled = !isScrollableContentActive,
                        onDragStopped = { velocityY -> onSheetSettle(velocityY) },
                    ),
            ) { page ->
                when (FullPlayerPage.entries[page]) {
                    FullPlayerPage.ARTWORK -> {
                        LandscapeControlsPage(
                            state = state,
                            track = track,
                            onTogglePlayback = onTogglePlayback,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onSeek = onSeek,
                            onCycleMode = onCycleMode,
                            onOpenQueue = {
                                coroutineScope.launch {
                                    pager.animateScrollToPage(FullPlayerPage.QUEUE.ordinal)
                                }
                            },
                            onShowInfo = onShowInfo,
                            showFeedback = showFeedback,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    FullPlayerPage.LYRICS -> {
                        LyricsPaneRoute(
                            viewModel = lyricsViewModel,
                            missingText = stringResource(R.string.lyrics_not_found),
                            loadingText = stringResource(R.string.lyrics_loading),
                            returnToCurrentText = stringResource(R.string.lyrics_return_to_current),
                            onSheetDrag = onSheetDrag,
                            onSheetSettle = onSheetSettle,
                            sheetProgress = sheetProgress,
                            modifier = Modifier.padding(horizontal = dimensions.spaceMedium),
                        )
                    }

                    FullPlayerPage.QUEUE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = dimensions.spaceMedium),
                        ) {
                            QueuePage(
                                rows = state.queue,
                                playbackMode = state.playbackMode,
                                onCycleMode = onCycleMode,
                                onJump = onJumpToQueueItem,
                                onRemove = onRemoveQueueItem,
                                onSheetDrag = onSheetDrag,
                                onSheetSettle = onSheetSettle,
                                sheetProgress = sheetProgress,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 响应式双栏右侧 Page 0：播控主视图。
 *
 * 垂直居中展示：
 * 1. 歌曲标题（单行超长跑马灯）与艺术家名
 * 2. 状态信息指示（准备中 / 错误）
 * 3. 细长交互进度条（[InteractiveThinProgressBar]）与时间标签
 * 4. 三键核心主控行（上一首、播放/暂停、下一首）
 * 5. 五项辅助工具栏（循环模式、睡眠定时、均衡器、队列、更多）
 */
@Composable
internal fun LandscapeControlsPage(
    state: PlayerUiState,
    track: Track,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onShowInfo: () -> Unit,
    showFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = dimensions.spaceMedium,
                end = dimensions.spaceLarge,
                top = dimensions.spaceSmall,
                bottom = dimensions.spaceSmall,
            ),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.Start,
    ) {
        // 1. 歌曲标题与艺术家名称
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = track.title,
                style = MusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(dimensions.spaceExtraSmall))
            Text(
                text = track.artistName.localizedArtistName(),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 2. 状态指示（准备中 / 错误等）
        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerStatus(state.loadState, state.errorMessageRes)
        }

        // 3. 细长交互进度条
        InteractiveThinProgressBar(
            trackId = track.id,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = state.durationMs > 0,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        // 4. 三键核心主控区（上一首、播放/暂停、下一首）
        val previousDescription = stringResource(R.string.playback_previous)
        val playbackDescription =
            stringResource(if (state.isPlaying) R.string.playback_pause else R.string.playback_play)
        val nextDescription = stringResource(R.string.playback_next)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                dimensions.spaceSmall,
                Alignment.CenterHorizontally,
            ),
        ) {
            item {
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
            }
            item {
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
            }
            item {
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
        }

        // 5. 底部五项辅助工具栏（循环模式、睡眠定时、均衡器、播放队列、更多信息）
        val sleepTimerComingSoon = stringResource(R.string.playback_sleep_timer_coming_soon)
        val equalizerComingSoon = stringResource(R.string.playback_equalizer_coming_soon)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                dimensions.spaceSmall,
                Alignment.CenterHorizontally,
            ),
        ) {
            item {
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
            }
            item {
                BareIconButton(
                    onClick = { showFeedback(sleepTimerComingSoon) },
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playback_sleep_timer),
                        contentDescription = stringResource(R.string.playback_sleep_timer),
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }
            item {
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
            }
            item {
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
            item {
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
}

/**
 * 播放详情页响应式布局判定与尺寸策略。
 */
internal object PlayerResponsivePolicy {
    /**
     * 判断视口是否满足双栏响应式横屏排版条件（宽度大于高度）。
     */
    fun isLandscape(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Boolean =
        width > height

    /**
     * 计算左栏黑胶唱片自适应直径，不超过 [maxSize]。
     */
    fun calculateArtworkDiscSize(
        width: androidx.compose.ui.unit.Dp,
        height: androidx.compose.ui.unit.Dp,
        maxSize: androidx.compose.ui.unit.Dp,
    ): androidx.compose.ui.unit.Dp {
        val target = min(width * 0.85f, height * 0.72f)
        return target.coerceAtMost(maxSize)
    }
}
