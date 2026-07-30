package com.musicapp.player.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
internal fun SidebarNavigation(
    policy: WindowLayoutPolicy,
    selectedRoute: TopLevelNavKey,
    themeMode: ThemeMode,
    isLibrarySyncing: Boolean,
    canScanMusic: Boolean,
    onSelect: (TopLevelNavKey) -> Unit,
    onRequestExit: () -> Unit,
    onCycleTheme: () -> Unit,
    onEqualizer: () -> Unit,
    onScanMusic: () -> Unit,
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
                )
                SidebarQuickAction(
                    iconResId = R.drawable.ic_sidebar_equalizer,
                    label = stringResource(R.string.sidebar_equalizer),
                    tint = SidebarIconPalette.Equalizer,
                    onClick = onEqualizer,
                )
            }
        }
        SidebarCard {
            SidebarGroups.mediaBrowse.forEachIndexed { index, entry ->
                SidebarEntryRow(
                    entry = entry,
                    selected = entry.route == selectedRoute,
                    tint = SidebarIconPalette.media[index],
                    onClick = { onSelect(entry.route) },
                )
            }
        }
        SidebarCard {
            SidebarGroups.appOperations.forEachIndexed { index, entry ->
                val enabled =
                    entry !is SidebarEntry.Action ||
                        entry.action != SidebarAction.SCAN_LIBRARY ||
                        (canScanMusic && !isLibrarySyncing)
                SidebarEntryRow(
                    entry = entry,
                    selected = (entry as? SidebarEntry.Destination)?.route == selectedRoute,
                    enabled = enabled,
                    tint = SidebarIconPalette.operations[index],
                    onClick = {
                        when (entry) {
                            is SidebarEntry.Destination -> onSelect(entry.route)
                            is SidebarEntry.Action ->
                                when (entry.action) {
                                    SidebarAction.SCAN_LIBRARY -> onScanMusic()
                                }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SidebarExitDialog(
    onChoice: (SidebarExitChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onChoice(SidebarExitChoice.CANCEL) },
        title = { Text(stringResource(R.string.sidebar_exit_dialog_title)) },
        text = { Text(stringResource(R.string.sidebar_exit_dialog_description)) },
        confirmButton = {
            TextButton(onClick = { onChoice(SidebarExitChoice.FULL_EXIT) }) {
                Text(stringResource(R.string.sidebar_exit_fully))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(SidebarExitChoice.RETURN_TO_DESKTOP) }) {
                    Text(stringResource(R.string.sidebar_return_to_desktop))
                }
                TextButton(onClick = { onChoice(SidebarExitChoice.CANCEL) }) {
                    Text(stringResource(R.string.sidebar_cancel))
                }
            }
        },
    )
}

@Composable
private fun SidebarCard(
    contentPadding: Dp = MusicTheme.dimensions.sidebarCardContentPadding,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MusicTheme.dimensions.sidebarCardCornerRadius),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MusicTheme.colors.surfaceContainer,
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = MusicTheme.dimensions.spaceExtraSmall,
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
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(MusicTheme.dimensions.minimumTouchTarget),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(MusicTheme.dimensions.spaceLarge),
        )
    }
}

@Composable
private fun SidebarEntryRow(
    entry: SidebarEntry,
    selected: Boolean,
    enabled: Boolean = true,
    tint: Color,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val interactionModifier =
        when (entry) {
            is SidebarEntry.Destination ->
                Modifier.selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                )
            is SidebarEntry.Action ->
                Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .then(interactionModifier)
                .padding(end = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.width(dimensions.sidebarSelectionIndicatorWidth)
                    .height(dimensions.spaceLarge),
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MusicTheme.shapes.extraSmall,
                    color = MusicTheme.colors.primary,
                    content = {},
                )
            }
        }
        Spacer(modifier = Modifier.width(dimensions.spaceSmall))
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
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
