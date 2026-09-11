package com.musicapp.player.feature.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.ChoiceRow
import com.musicapp.player.core.designsystem.component.SettingsSection
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun SystemEqualizerScreenRoute(
    viewModel: SystemEqualizerViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SystemEqualizerScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onEnabledChange = viewModel::setSystemEnabled,
        onOpenSystemPanel = viewModel::openSystemPanel,
        bottomPadding = bottomPadding,
    )
}

@Composable
private fun SystemEqualizerScreen(
    state: SystemEqualizerUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSystemPanel: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = dimensions.settingsContentMaxWidth),
        ) {
            CategoryHeader(
                title = stringResource(R.string.equalizer_system_title),
                policy = policy,
                onBack = onBack,
            )

            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceOverscroll(overscrollEffect),
                contentPadding = PaddingValues(
                    start = dimensions.contentHorizontalPadding,
                    end = dimensions.contentHorizontalPadding,
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceLarge + bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MusicTheme.shapes.large,
                        color = MusicTheme.aeroCardContainerColor,
                        contentColor = MusicTheme.colors.onSurface,
                    ) {
                        ChoiceRow(
                            title = stringResource(R.string.equalizer_system_enable),
                            interactionModifier = Modifier.toggleable(
                                value = state.isEnabled,
                                role = Role.Switch,
                                onValueChange = onEnabledChange,
                            ),
                            trailingContent = {
                                Switch(
                                    checked = state.isEnabled,
                                    onCheckedChange = null,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            },
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.equalizer_system_panel_card_title)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_system_panel_card_desc),
                                style = MusicTheme.typography.bodyMedium,
                                color = MusicTheme.colors.onSurfaceVariant,
                            )

                            if (state.isSystemPanelSupported) {
                                Button(
                                    onClick = onOpenSystemPanel,
                                    enabled = state.isEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MusicTheme.colors.primary,
                                        contentColor = MusicTheme.colors.onPrimary,
                                    ),
                                    shape = MusicTheme.shapes.large,
                                ) {
                                    Text(
                                        text = stringResource(R.string.equalizer_system_open_panel),
                                        style = MusicTheme.typography.labelLarge,
                                    )
                                }
                            } else {
                                Surface(
                                    color = MusicTheme.colors.surfaceContainerHigh,
                                    shape = MusicTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(dimensions.spaceSmall),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_common_close_circle),
                                            contentDescription = null,
                                            tint = MusicTheme.colors.error,
                                            modifier = Modifier.size(dimensions.spaceMedium),
                                        )
                                        Text(
                                            text = stringResource(R.string.equalizer_system_unavailable),
                                            style = MusicTheme.typography.bodySmall,
                                            color = MusicTheme.colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        color = MusicTheme.colors.surfaceContainerLow,
                        shape = MusicTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_system_overlay_notice),
                            style = MusicTheme.typography.bodySmall,
                            color = MusicTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(dimensions.spaceMedium),
                        )
                    }
                }
            }
        }
    }
}
