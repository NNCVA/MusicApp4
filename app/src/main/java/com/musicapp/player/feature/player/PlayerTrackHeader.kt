package com.musicapp.player.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.musicapp.player.core.designsystem.component.localizedArtistName
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.theme.MusicTheme

private const val TITLE_SLIDE_DURATION_MS = 300

/**
 * 播放详情页头部曲目信息区域（包含歌名跑马灯与歌手名）。
 * 切歌时根据 [direction] 驱动水平滑入/滑出及透明度过渡动效：
 * - [TrackSlideDirection.FORWARD]（下一首）：旧曲向左滑出，新曲从右侧向左滑入；
 * - [TrackSlideDirection.BACKWARD]（上一首）：旧曲向右滑出，新曲从左侧向右滑入；
 * - [TrackSlideDirection.NONE]（首次加载/同曲重播）：瞬时刷新或仅淡入，无水平位移。
 */
@Composable
fun PlayerTrackHeader(
    track: Track,
    direction: TrackSlideDirection,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions

    AnimatedContent(
        targetState = track,
        transitionSpec = {
            when (direction) {
                TrackSlideDirection.FORWARD -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS),
                    )).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS),
                        ),
                    )
                }

                TrackSlideDirection.BACKWARD -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS),
                    )).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = TITLE_SLIDE_DURATION_MS),
                        ),
                    )
                }

                TrackSlideDirection.NONE -> {
                    fadeIn(animationSpec = tween(0)).togetherWith(fadeOut(animationSpec = tween(0)))
                }
            }
        },
        contentKey = { it.id },
        modifier = modifier.clipToBounds(),
        label = "PlayerTrackHeaderTransition",
    ) { targetTrack ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = targetTrack.title,
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
                text = targetTrack.artistName.localizedArtistName(),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
