package com.musicapp.player.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.isActive

/**
 * 播放详情页旋转专辑封面组件。
 *
 * - 播放时顺时针匀速旋转（20 秒/圈，平缓优雅）；
 * - 暂停时原位保持当前角度，恢复播放时无缝继续旋转；
 * - 切歌时当前封面以减速曲线逆时针平滑回正到 0 度（约 350ms）；
 * - 回正后新封面以正中心为原点圆形径向波纹扩散展开（约 350ms），扩散完成后若处于播放中则启动顺时针旋转；
 * - 支持快速连续切歌时即时中断并衔接最新曲目；
 * - 当组件处于不可见状态时自动暂停动画循环以节约电量。
 */
@Composable
fun RotatingArtworkDisc(
    track: Track,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    var displayedTrack by remember { mutableStateOf(track) }
    var previousTrack by remember { mutableStateOf<Track?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }
    var isInitialLaunch by remember { mutableStateOf(true) }

    val rotationAngle = remember { Animatable(0f) }
    val revealProgress = remember { Animatable(1f) }

    // 切歌过渡监听
    LaunchedEffect(track.id) {
        if (isInitialLaunch) {
            isInitialLaunch = false
            displayedTrack = track
            return@LaunchedEffect
        }

        if (displayedTrack.id != track.id) {
            if (!isVisible) {
                // 不可见状态下直接更新，不耗费动画性能
                displayedTrack = track
                previousTrack = null
                isTransitioning = false
                rotationAngle.snapTo(0f)
                revealProgress.snapTo(1f)
                return@LaunchedEffect
            }

            isTransitioning = true
            previousTrack = displayedTrack

            // 阶段一：逆时针回正至 0 度
            val currentAngle = ArtworkDiscMotion.normalizeAngle(rotationAngle.value)
            rotationAngle.snapTo(currentAngle)

            if (currentAngle > 1f) {
                rotationAngle.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = ArtworkDiscMotion.REWIND_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            rotationAngle.snapTo(0f)

            // 阶段二：圆形径向展开扩散呈现新封面
            displayedTrack = track
            revealProgress.snapTo(0f)

            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ArtworkDiscMotion.REVEAL_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )

            // 阶段三：过渡完成，重置状态
            previousTrack = null
            isTransitioning = false
        }
    }

    // 正常播放时的顺时针匀速旋转循环（20 秒/圈）
    LaunchedEffect(isPlaying, isTransitioning, isVisible) {
        if (isPlaying && !isTransitioning && isVisible) {
            while (isActive) {
                val current = ArtworkDiscMotion.normalizeAngle(rotationAngle.value)
                rotationAngle.snapTo(current)
                rotationAngle.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(
                        durationMillis = ArtworkDiscMotion.ROTATION_CYCLE_MS,
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 底层：切歌过渡期间的旧封面（正在回正并作为扩散底图）
        if (isTransitioning && previousTrack != null) {
            DiscArtworkImage(
                track = previousTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationAngle.value
                    },
            )
        }

        // 顶层：当前封面（播放旋转 或 径向扩散展开）
        DiscArtworkImage(
            track = displayedTrack,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTransitioning) {
                        Modifier.clip(CircularRevealShape(revealProgress.value))
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    rotationZ = if (isTransitioning) 0f else rotationAngle.value
                },
        )

        // 径向扩散时的水波纹光环微光特效
        if (isTransitioning && revealProgress.value in 0.01f..0.99f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentRadius = (size.minDimension / 2f) * revealProgress.value
                val alpha = ((1f - revealProgress.value) * 0.4f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = 2.5f.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun DiscArtworkImage(
    track: Track?,
    modifier: Modifier = Modifier,
) {
    val artworkDescription = stringResource(R.string.player_artwork_description)
    AsyncImage(
        model = track,
        contentDescription = artworkDescription,
        modifier = modifier
            .clip(CircleShape)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}

/**
 * 圆形径向扩散裁切形状。
 * [progress] 范围 0f..1f，从中心向外扩展。
 */
private class CircularRevealShape(private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) {
            return Outline.Generic(Path())
        }
        val radius = (size.minDimension / 2f) * clamped
        val path = Path().apply {
            addOval(
                Rect(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius,
                )
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * 唱片封面旋转与切歌动效的运动策略与辅助计算。
 */
object ArtworkDiscMotion {
    /** 顺时针旋转一整圈周期（毫秒）：20 秒/圈 */
    const val ROTATION_CYCLE_MS = 20_000

    /** 切歌时逆时针快速回正动画时长（毫秒） */
    const val REWIND_DURATION_MS = 350

    /** 切歌时新封面中心径向扩散展开动画时长（毫秒） */
    const val REVEAL_DURATION_MS = 350

    /**
     * 将角度规范化到 [0, 360) 区间。
     */
    fun normalizeAngle(degrees: Float): Float {
        val normalized = degrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    /**
     * 根据扩散进度计算径向扩散半径。
     */
    fun calculateRevealRadius(minDimension: Float, progress: Float): Float {
        return (minDimension / 2f) * progress.coerceIn(0f, 1f)
    }
}
