package com.musicapp.player.ui.sidebar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.launch

@Composable
internal fun SidebarNavigation(
    policy: WindowLayoutPolicy,
    selectedRoute: TopLevelNavKey,
    themeMode: ThemeMode,
    onSelect: (TopLevelNavKey) -> Unit,
    onRequestExit: () -> Unit,
    onCycleTheme: () -> Unit,
    onEqualizer: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val outerPadding =
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            dimensions.spaceSmall
        } else {
            dimensions.sidebarOuterPadding
        }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = outerPadding,
                    top = outerPadding,
                    end = outerPadding,
                    bottom = dimensions.miniPlayerHeight + outerPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(dimensions.sidebarCardSpacing),
    ) {
        SidebarCard(contentPadding = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidebarQuickAction(
                    iconResId = R.drawable.ic_sidebar_exit,
                    label = stringResource(R.string.sidebar_exit),
                    tint = SidebarIconPalette.Exit,
                    onClick = onRequestExit,
                )
                SidebarQuickAction(
                    iconResId = R.drawable.ic_sidebar_theme,
                    label =
                        stringResource(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> R.string.sidebar_theme_system
                                ThemeMode.LIGHT -> R.string.sidebar_theme_light
                                ThemeMode.DARK -> R.string.sidebar_theme_dark
                            },
                        ),
                    tint = SidebarIconPalette.Theme,
                    onClick = onCycleTheme,
                    rotateOnClick = true,
                )
                SidebarQuickAction(
                    iconResId = R.drawable.ic_sidebar_equalizer,
                    label = stringResource(R.string.sidebar_equalizer),
                    tint = SidebarIconPalette.Equalizer,
                    onClick = onEqualizer,
                )
            }
        }
        SidebarCard(contentPadding = 0.dp) {
            SidebarGroups.mediaBrowse.forEachIndexed { index, entry ->
                SidebarEntryRow(
                    entry = entry,
                    selected = entry.route == selectedRoute,
                    isFirst = index == 0,
                    isLast = index == SidebarGroups.mediaBrowse.lastIndex,
                    tint = SidebarIconPalette.media[index],
                    onClick = { onSelect(entry.route) },
                )
            }
        }
        SidebarCard(contentPadding = 0.dp) {
            SidebarGroups.appOperations.forEachIndexed { index, entry ->
                SidebarEntryRow(
                    entry = entry,
                    selected = entry.route == selectedRoute,
                    isFirst = index == 0,
                    isLast = index == SidebarGroups.appOperations.lastIndex,
                    tint = SidebarIconPalette.operations[index],
                    onClick = { onSelect(entry.route) },
                )
            }
        }
    }
}

@Composable
internal fun SidebarExitDialog(
    onChoice: (SidebarExitChoice) -> Unit,
) {
    Dialog(
        onDismissRequest = {
            onChoice(SidebarExitChoice.CANCEL)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题卡片 + 退出选项
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MusicTheme.colors.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.sidebar_exit_dialog_title),
                        modifier = Modifier.padding(
                            start = 24.dp,
                            top = 24.dp,
                            end = 24.dp,
                            bottom = 16.dp,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MusicTheme.colors.onSurface,
                    )

                    HorizontalDivider()

                    DialogOptionRow(
                        text = stringResource(R.string.sidebar_exit_fully),
                        onClick = {
                            onChoice(SidebarExitChoice.FULL_EXIT)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    DialogOptionRow(
                        text = stringResource(R.string.sidebar_return_to_desktop),
                        onClick = {
                            onChoice(SidebarExitChoice.RETURN_TO_DESKTOP)
                        },
                    )
                }
            }

            // 单独的取消卡片
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MusicTheme.colors.surfaceContainer),
            ) {
                DialogOptionRow(
                    text = stringResource(R.string.sidebar_cancel),
                    onClick = {
                        onChoice(SidebarExitChoice.CANCEL)
                    },
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun DialogOptionRow(
    text: String,
    onClick: () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 24.dp,
                vertical = 18.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = textStyle,
            color = MusicTheme.colors.onSurface,
        )
    }
}

@Composable
private fun SidebarCard(
    contentPadding: Dp = MusicTheme.dimensions.sidebarCardContentPadding,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MusicTheme.dimensions.sidebarCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MusicTheme.aeroCardContainerColor,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = { content() },
        )
    }
}

@Composable
private fun SidebarQuickAction(
    iconResId: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    rotateOnClick: Boolean = false,
) {
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    BareIconButton(
        onClick = {
            if (rotateOnClick) {
                coroutineScope.launch {
                    rotation.animateTo(
                        targetValue = rotation.targetValue + 360f,
                        animationSpec =
                            tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing,
                            ),
                    )
                }
            }
            onClick()
        },
        modifier = Modifier.size(MusicTheme.dimensions.minimumTouchTarget),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = label,
            tint = tint,
            modifier =
                Modifier.size(MusicTheme.dimensions.spaceLarge)
                    .graphicsLayer {
                        rotationZ = rotation.value
                    },
        )
    }
}

@Composable
private fun SidebarEntryRow(
    entry: SidebarEntry,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean = true,
    tint: Color,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val edgePadding = dimensions.sidebarCardContentPadding
    val topPadding = if (isFirst) edgePadding else 0.dp
    val bottomPadding = if (isLast) edgePadding else 0.dp
    val shape =
        RoundedCornerShape(
            topStart = if (isFirst) dimensions.sidebarCardCornerRadius else 0.dp,
            topEnd = if (isFirst) dimensions.sidebarCardCornerRadius else 0.dp,
            bottomEnd = if (isLast) dimensions.sidebarCardCornerRadius else 0.dp,
            bottomStart = if (isLast) dimensions.sidebarCardCornerRadius else 0.dp,
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget + topPadding + bottomPadding)
                .clip(shape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .padding(
                    start = edgePadding + dimensions.spaceSmall,
                    top = topPadding,
                    end = edgePadding + dimensions.spaceSmall,
                    bottom = bottomPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(entry.iconResId),
            contentDescription = null,
            tint = if (enabled) tint else MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(dimensions.spaceLarge),
        )
        Spacer(modifier = Modifier.width(dimensions.spaceSmall))
        Text(
            text = stringResource(entry.labelResId),
            style = MusicTheme.typography.bodyLarge,
            color =
                if (enabled) MusicTheme.colors.onSurface
                else MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

private object SidebarIconPalette {
    val Exit = Color(0xFFE25555)
    val Theme = Color(0xFFF2A93B)
    val Equalizer = Color(0xFF9B6BE8)
    val media =
        listOf(
            Color(0xFF4E8EE8),
            Color(0xFF9B6BE8),
            Color(0xFFE45F91),
            Color(0xFFF2A93B),
            Color(0xFF31A985),
        )
    val operations =
        listOf(
            Color(0xFF33A6B8),
            Color(0xFF6D7FE8),
            Color(0xFF7D8795),
            Color(0xFF4E8EE8),
        )
}
