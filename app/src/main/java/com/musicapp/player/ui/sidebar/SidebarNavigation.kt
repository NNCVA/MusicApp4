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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlinx.coroutines.launch

@Composable
internal fun SidebarNavigation(
    policy: WindowLayoutPolicy,
    selectedRoute: TopLevelNavKey,
    themeMode: ThemeMode,
    playerSheetVisible: Boolean = false,
    onSelect: (TopLevelNavKey) -> Unit,
    onRequestExit: () -> Unit,
    onCycleTheme: () -> Unit,
    onEqualizer: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val accent = MusicTheme.accentPalette
    val outerPadding =
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            dimensions.spaceSmall
        } else {
            dimensions.sidebarOuterPadding
        }
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(
                    start = outerPadding,
                    top = outerPadding,
                    end = outerPadding,
                    bottom = (if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp) +
                        bottomInset +
                        outerPadding,
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
                    tint = accent.exit,
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
                    tint = accent.theme,
                    onClick = onCycleTheme,
                    rotateOnClick = true,
                )
                SidebarQuickAction(
                    iconResId = R.drawable.ic_sidebar_equalizer,
                    label = stringResource(R.string.sidebar_equalizer),
                    tint = accent.equalizer,
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
                    tint = accent.mediaIconColors[index],
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
                    tint = accent.operationIconColors[index],
                    onClick = { onSelect(entry.route) },
                )
            }
        }
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
            tint = if (enabled) tint else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
            modifier = Modifier.size(dimensions.spaceLarge),
        )
        Spacer(modifier = Modifier.width(dimensions.spaceSmall))
        Text(
            text = stringResource(entry.labelResId),
            style = MusicTheme.typography.bodyLarge,
            color =
                if (enabled) MusicTheme.colors.onSurface
                else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
        )
    }
}

