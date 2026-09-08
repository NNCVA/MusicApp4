package com.musicapp.player.feature.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.feature.player.rememberPlayerSheetNestedScrollConnection
import com.musicapp.player.theme.MusicTheme
import kotlin.math.abs

@Composable
fun LyricsPaneRoute(
    viewModel: LyricsViewModel,
    missingText: String,
    loadingText: String,
    modifier: Modifier = Modifier,
    onSheetDrag: (Float) -> Float = { 0f },
    onSheetSettle: (Float) -> Unit = {},
    sheetProgress: () -> Float = { 1f },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LyricsPane(
        state = state,
        missingText = missingText,
        loadingText = loadingText,
        onManualScroll = viewModel::onManualScroll,
        onLineClick = viewModel::onLineClick,
        onOpenSettings = viewModel::showSettings,
        onDismissSettings = viewModel::dismissSettings,
        onFontSizeChange = viewModel::setFontSizeSp,
        onTextCenteredChange = viewModel::setTextCentered,
        onFontWeightChange = viewModel::setFontWeight,
        onSheetDrag = onSheetDrag,
        onSheetSettle = onSheetSettle,
        sheetProgress = sheetProgress,
        modifier = modifier,
    )
}

@Composable
fun LyricsPane(
    state: LyricsUiState,
    missingText: String,
    loadingText: String,
    onManualScroll: () -> Unit,
    onLineClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onDismissSettings: () -> Unit = {},
    onFontSizeChange: (Int) -> Unit = {},
    onTextCenteredChange: (Boolean) -> Unit = {},
    onFontWeightChange: (Int) -> Unit = {},
    onSheetDrag: (Float) -> Float = { 0f },
    onSheetSettle: (Float) -> Unit = {},
    sheetProgress: () -> Float = { 1f },
) {
    val dimensions = MusicTheme.dimensions
    Box(modifier = modifier.fillMaxSize()) {
        when (state.mode) {
            LyricsDisplayMode.LOADING -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        loadingText,
                        style = MusicTheme.typography.bodyMedium,
                        color = MusicTheme.colors.onSurfaceVariant,
                    )
                }
            }

            LyricsDisplayMode.STATIC,
            LyricsDisplayMode.MISSING,
            -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimensions.contentHorizontalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.staticText ?: missingText,
                        style = MusicTheme.typography.bodyLarge.copy(
                            fontSize = state.fontSizeSp.sp,
                            fontWeight = FontWeight(state.fontWeight),
                        ),
                        textAlign = if (state.isTextCentered) TextAlign.Center else TextAlign.Start,
                        color = MusicTheme.colors.onSurfaceVariant,
                    )
                }
            }

            LyricsDisplayMode.SYNCHRONIZED -> {
                val listState = rememberLazyListState()
                val sheetNestedScrollConnection = rememberPlayerSheetNestedScrollConnection(
                    canScrollBackward = { listState.canScrollBackward },
                    sheetProgress = sheetProgress,
                    onSheetDrag = onSheetDrag,
                    onSheetSettle = onSheetSettle,
                    onPreUserScroll = onManualScroll,
                )
                LaunchedEffect(state.activeLineIndex, state.autoCenterEnabled, state.autoCenterRequest) {
                    val index = state.activeLineIndex ?: return@LaunchedEffect
                    if (!state.autoCenterEnabled) return@LaunchedEffect
                    if (listState.isScrollInProgress) return@LaunchedEffect
                    val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (visibleItem != null) {
                        val viewportCenter = (
                            listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
                        ) / 2f
                        val offsetFromCenter = (visibleItem.offset + visibleItem.size / 2f) - viewportCenter
                        if (abs(offsetFromCenter) > 0.5f) {
                            listState.animateScrollBy(
                                value = offsetFromCenter,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            )
                        }
                    } else {
                        listState.scrollToItem(index = index, scrollOffset = 0)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerableEdgePadding = ((maxHeight - dimensions.minimumTouchTarget) / 2)
                        .coerceAtLeast(dimensions.spaceLarge)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                val bottomBarHeightPx = dimensions.minimumTouchTarget.toPx()
                                val fadeHeightPx = 48.dp.toPx()
                                val totalHeight = size.height
                                if (totalHeight > 0f) {
                                    val bottomFadeEndStop = ((totalHeight - bottomBarHeightPx) / totalHeight).coerceIn(0f, 1f)
                                    val bottomFadeStartStop = ((totalHeight - bottomBarHeightPx - fadeHeightPx) / totalHeight).coerceIn(0f, bottomFadeEndStop)
                                    val topFadeStop = 0.12f.coerceAtMost(bottomFadeStartStop)
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            topFadeStop to Color.Black,
                                            bottomFadeStartStop to Color.Black,
                                            bottomFadeEndStop to Color.Transparent,
                                            1.0f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                            .nestedScroll(sheetNestedScrollConnection),
                        contentPadding = PaddingValues(vertical = centerableEdgePadding),
                        //verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
                    ) {
                        itemsIndexed(
                            items = state.lines,
                            key = { index, line -> "${index}_${line.timestampMs}" },
                        ) { index, line ->
                            val isActive = index == state.activeLineIndex
                            val baseFontSize = state.fontSizeSp.toFloat()
                            val targetScale = if (isActive && baseFontSize > 0f) {
                                (baseFontSize + 2f) / baseFontSize
                            } else {
                                1f
                            }
                            val animatedScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                label = "lyricScale_$index",
                            )
                            val animatedColor by animateColorAsState(
                                targetValue = if (isActive) {
                                    MusicTheme.colors.onSurface
                                } else {
                                    MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                label = "lyricColor_$index",
                            )
                            val transformOrigin = remember(state.isTextCentered) {
                                TransformOrigin(
                                    pivotFractionX = if (state.isTextCentered) 0.5f else 0f,
                                    pivotFractionY = 0.5f,
                                )
                            }
                            Text(
                                text = line.text,
                                style = MusicTheme.typography.bodyLarge.copy(
                                    fontSize = state.fontSizeSp.sp,
                                    fontWeight = FontWeight(state.fontWeight),
                                    lineHeight = (state.fontSizeSp * 1.45f).sp,
                                ),
                                color = animatedColor,
                                textAlign = if (state.isTextCentered) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = dimensions.minimumTouchTarget)
                                    .clickable { onLineClick(index) }
                                    .padding(
                                        horizontal = dimensions.contentHorizontalPadding,
                                        vertical = dimensions.spaceSmall,
                                    )
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                        this.transformOrigin = transformOrigin
                                    },
                            )
                        }
                    }
                }
            }
        }

        if (state.mode != LyricsDisplayMode.LOADING) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(dimensions.minimumTouchTarget)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsSourceBadgeButton(
                    source = state.source,
                    onClick = onOpenSettings,
                )
            }
        }

        if (state.isSettingsSheetVisible) {
            LyricsSettingsSheet(
                fontSizeSp = state.fontSizeSp,
                isTextCentered = state.isTextCentered,
                fontWeight = state.fontWeight,
                onFontSizeChange = onFontSizeChange,
                onTextCenteredChange = onTextCenteredChange,
                onFontWeightChange = onFontWeightChange,
                onDismiss = onDismissSettings,
            )
        }
    }
}

@Composable
internal fun LyricsSourceBadgeButton(
    source: LyricsSource?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceLabel = when (source) {
        LyricsSource.EMBEDDED_SYLT, LyricsSource.EMBEDDED_USLT -> stringResource(R.string.lyrics_source_embedded)
        LyricsSource.EXTERNAL_LRC -> stringResource(R.string.lyrics_source_lrc)
        null -> stringResource(R.string.lyrics_source_default)
    }
    val contentDesc = stringResource(R.string.lyrics_settings_cd)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        contentColor = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_icon_text),
                    style = MusicTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    ),
                    color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            Text(
                text = sourceLabel,
                style = MusicTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                ),
                color = MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
