package com.musicapp.player.feature.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.motion.PlayerMotionTokens
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.isActive

/**
 * The single progress value shared by the full-player disc and Aero background.
 *
 * [progress] is zero at the start of a track-change transition and one when the new artwork is
 * fully settled. [transitionId] changes for every accepted transition, including an immediate
 * (reduced-motion or invisible) settle.
 */
@Immutable
data class ArtworkDiscTransitionState(
    val progress: Float = 1f,
    val isRunning: Boolean = false,
    val transitionId: Long = 0L,
)

/**
 * 播放详情页旋转专辑封面组件。
 *
 * - 播放时顺时针匀速旋转（20 秒/圈）；
 * - 暂停时原位保持当前角度，恢复播放时无缝继续旋转；
 * - 切歌时在共享的 500ms 进度内逆时针回正，同时完成新旧封面交叉淡入淡出与虚化；
 * - 新封面资源未就绪前保持旧封面，避免封面和背景调色板错拍；
 * - 不可见或系统关闭动效时直接更新，不补播错过的动画。
 */
@Composable
fun RotatingArtworkDisc(
    track: Track,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    artwork: ArtworkResult = ArtworkResult.Placeholder,
    artworkTrackId: TrackId? = null,
    transition: ArtworkDiscTransitionState = ArtworkDiscTransitionState(),
) {
    val animationScope = rememberCoroutineScope()
    val animationsEnabled = animationScope.coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    val artworkReady = artworkTrackId == null || artworkTrackId == track.id

    var displayedTrackId by remember { mutableStateOf(track.id) }
    var displayedArtwork by remember {
        mutableStateOf(if (artworkReady) artwork else ArtworkResult.Placeholder)
    }
    var pendingTrackId by remember { mutableStateOf<TrackId?>(null) }
    var pendingArtwork by remember { mutableStateOf<ArtworkResult?>(null) }

    LaunchedEffect(
        track.id,
        artwork,
        artworkTrackId,
        isVisible,
        animationsEnabled,
        transition.isRunning,
        transition.transitionId,
    ) {
        val ready = artworkTrackId == null || artworkTrackId == track.id

        fun commitCurrentArtwork() {
            displayedTrackId = track.id
            displayedArtwork = if (ready) artwork else ArtworkResult.Placeholder
            pendingTrackId = null
            pendingArtwork = null
        }

        if (displayedTrackId == track.id) {
            if (ready) displayedArtwork = artwork
            if (!transition.isRunning) {
                pendingTrackId = null
                pendingArtwork = null
            }
            return@LaunchedEffect
        }

        if (!isVisible || !animationsEnabled || !transition.isRunning) {
            if (ready || !isVisible) {
                commitCurrentArtwork()
            } else {
                pendingTrackId = track.id
                pendingArtwork = null
            }
            return@LaunchedEffect
        }

        // Promote the current pending target before retargeting so a rapid skip never flashes
        // back to an older base image while the new transition restarts from progress zero.
        if (pendingTrackId != null && pendingTrackId != track.id && pendingArtwork != null) {
            displayedTrackId = pendingTrackId!!
            displayedArtwork = pendingArtwork!!
        }
        pendingTrackId = track.id
        if (ready) pendingArtwork = artwork
    }

    val rotationAngle = remember { Animatable(0f) }
    var rewindStartAngle by remember { mutableFloatStateOf(0f) }
    var settledRotationTransitionId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(transition.transitionId, transition.isRunning) {
        if (transition.isRunning) {
            rotationAngle.stop()
            rewindStartAngle = ArtworkDiscMotion.normalizeAngle(rotationAngle.value)
            rotationAngle.snapTo(rewindStartAngle)
        } else if (transition.transitionId != 0L) {
            rotationAngle.stop()
            rotationAngle.snapTo(0f)
            settledRotationTransitionId = transition.transitionId
        }
    }

    // 正常播放时的顺时针匀速旋转循环（20 秒/圈）。
    LaunchedEffect(isPlaying, transition.isRunning, isVisible) {
        if (isPlaying && !transition.isRunning && isVisible && animationsEnabled) {
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

    val progress = transition.progress.coerceIn(0f, 1f)
    // The parent clears isRunning immediately after reaching progress 1, while this composable
    // commits displayedArtwork in the following LaunchedEffect. Keep the target as the rendered
    // base for that terminal composition so the old base layer cannot flash back for one frame.
    val terminalPendingArtwork = pendingArtwork.takeIf {
        !transition.isRunning &&
            progress >= 1f &&
            pendingTrackId == track.id
    }
    val incomingArtwork = pendingArtwork.takeIf {
        transition.isRunning && pendingTrackId == track.id
    }
    val renderedBaseArtwork = terminalPendingArtwork ?: displayedArtwork
    // The transition coroutine publishes isRunning before its LaunchedEffect can capture the
    // current angle. Use the live angle at progress zero so the first composed frame cannot use
    // the previous song's rewindStartAngle and visibly jump before settling back.
    val rewindStart = if (transition.isRunning && progress <= 0f) {
        ArtworkDiscMotion.normalizeAngle(rotationAngle.value)
    } else {
        rewindStartAngle
    }
    val renderedRotation = when {
        transition.isRunning -> ArtworkDiscMotion.rewindAngle(rewindStart, progress)
        transition.transitionId != 0L && transition.transitionId != settledRotationTransitionId -> 0f
        else -> rotationAngle.value
    }
    val artworkDescription = stringResource(R.string.player_artwork_description)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .semantics { contentDescription = artworkDescription },
    ) {
        DiscArtworkImage(
            artwork = renderedBaseArtwork,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (incomingArtwork != null) {
                        Modifier.blur(ArtworkDiscMotion.outgoingBlurRadiusDp(progress).dp)
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    rotationZ = renderedRotation
                    alpha = if (incomingArtwork != null) {
                        ArtworkDiscMotion.outgoingAlpha(progress)
                    } else {
                        1f
                    }
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        )

        if (incomingArtwork != null) {
            DiscArtworkImage(
                artwork = incomingArtwork,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(ArtworkDiscMotion.incomingBlurRadiusDp(progress).dp)
                    .graphicsLayer {
                        alpha = ArtworkDiscMotion.incomingAlpha(progress)
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            )
        }
    }
}

@Composable
private fun DiscArtworkImage(
    artwork: ArtworkResult,
    modifier: Modifier = Modifier,
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
            modifier = modifier
                .clip(CircleShape)
                .background(MusicTheme.colors.secondaryContainer),
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = placeholder,
            contentDescription = null,
            modifier = modifier
                .clip(CircleShape)
                .background(MusicTheme.colors.secondaryContainer),
            contentScale = ContentScale.Crop,
        )
    }
}

/** 唱片封面旋转与切歌动效的运动策略与辅助计算。 */
object ArtworkDiscMotion {
    /** 顺时针旋转一整圈周期（毫秒）：20 秒/圈。 */
    const val ROTATION_CYCLE_MS = 20_000

    /** 切歌时逆时针回正与封面交叉过渡共用的时长（毫秒）。 */
    const val REWIND_DURATION_MS = PlayerMotionTokens.TRACK_CHANGE_DURATION_MS

    /** 切歌时的最大封面虚化半径。 */
    const val MAX_BLUR_DP = PlayerMotionTokens.TRACK_CHANGE_MAX_BLUR_DP

    fun incomingAlpha(progress: Float): Float = progress.coerceIn(0f, 1f)

    fun outgoingAlpha(progress: Float): Float = 1f - incomingAlpha(progress)

    fun outgoingBlurRadiusDp(progress: Float): Float = MAX_BLUR_DP * incomingAlpha(progress)

    fun incomingBlurRadiusDp(progress: Float): Float = MAX_BLUR_DP * (1f - incomingAlpha(progress))

    /** 将角度规范化到 [0, 360) 区间。 */
    fun normalizeAngle(degrees: Float): Float {
        val normalized = degrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    /** 按统一过渡进度将当前角度逆时针回正到 0 度。 */
    fun rewindAngle(startAngle: Float, progress: Float): Float =
        normalizeAngle(startAngle) * (1f - progress.coerceIn(0f, 1f))
}
