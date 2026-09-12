package com.musicapp.player.feature.equalizer

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.HorizontalThinRingSlider
import com.musicapp.player.core.designsystem.component.VerticalEqualizerConsole
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun CustomEqualizerScreenRoute(
    viewModel: CustomEqualizerViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CustomEqualizerScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onEnabledChange = viewModel::setCustomEnabled,
        onSelectPreset = viewModel::selectPreset,
        onBandLevelChange = viewModel::setBandLevel,
        onResetToFlat = viewModel::resetToFlat,
        onBassBoostDbChange = viewModel::setBassBoostDb,
        onVirtualizerDbChange = viewModel::setVirtualizerDb,
        bottomPadding = bottomPadding,
    )
}

@Composable
internal fun CustomEqualizerScreen(
    state: CustomEqualizerUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onResetToFlat: () -> Unit,
    onBassBoostDbChange: (Float) -> Unit,
    onVirtualizerDbChange: (Float) -> Unit,
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
                title = stringResource(R.string.equalizer_custom_title),
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
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceLarge + bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                // 1. 上方：左侧默认设置（预设下拉），右侧开关（非卡片式平铺）
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PresetDropdownSelector(
                            selectedPresetIndex = state.selectedPresetIndex,
                            presets = state.presets,
                            enabled = state.isEnabled,
                            onSelectPreset = onSelectPreset,
                            onResetToFlat = onResetToFlat,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MusicTheme.shapes.medium)
                                .toggleable(
                                    value = state.isEnabled,
                                    role = Role.Switch,
                                    onValueChange = onEnabledChange,
                                )
                                .semantics(mergeDescendants = true) {}
                                .padding(horizontal = dimensions.spaceSmall, vertical = dimensions.spaceExtraSmall),
                        ) {
                            Switch(
                                checked = state.isEnabled,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }

                // 2. 中间：频段调节调音台（横轴频率、纵轴分贝大小，-10dB 到 10dB，细条加细圆环，非卡片平铺）
                item {
                    VerticalEqualizerConsole(
                        bands = state.bands,
                        enabled = state.isEnabled,
                        onBandLevelChange = onBandLevelChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                        sliderHeight = 190.dp,
                    )
                }

                // 3. 下方：低音增强与环绕声场（从 0-10dB，细条加细圆环平滑滑动，非卡片平铺）
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_effects_section),
                            style = MusicTheme.typography.titleMedium,
                            color = if (state.isEnabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                            modifier = Modifier.padding(bottom = dimensions.spaceSmall),
                        )

                        // 低音增强 (0-10dB)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensions.spaceSmall),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.equalizer_bass_boost),
                                    style = MusicTheme.typography.bodyLarge,
                                    color = if (state.isEnabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                                )
                                Text(
                                    text = stringResource(R.string.equalizer_effect_db_format, state.bassBoostDb),
                                    style = MusicTheme.typography.bodyMedium,
                                    color = if (state.isEnabled && state.bassBoostDb > 0f) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                                )
                            }
                            Spacer(modifier = Modifier.height(dimensions.spaceSmall))
                            HorizontalThinRingSlider(
                                value = state.bassBoostDb,
                                onValueChange = onBassBoostDbChange,
                                enabled = state.isEnabled,
                                valueRange = 0f..10f,
                            )
                        }

                        Spacer(modifier = Modifier.height(dimensions.spaceSmall))

                        // 环绕声场 (0-10dB)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensions.spaceSmall),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.equalizer_virtualizer),
                                    style = MusicTheme.typography.bodyLarge,
                                    color = if (state.isEnabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                                )
                                Text(
                                    text = stringResource(R.string.equalizer_effect_db_format, state.virtualizerDb),
                                    style = MusicTheme.typography.bodyMedium,
                                    color = if (state.isEnabled && state.virtualizerDb > 0f) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                                )
                            }
                            Spacer(modifier = Modifier.height(dimensions.spaceSmall))
                            HorizontalThinRingSlider(
                                value = state.virtualizerDb,
                                onValueChange = onVirtualizerDbChange,
                                enabled = state.isEnabled,
                                valueRange = 0f..10f,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetDropdownSelector(
    selectedPresetIndex: Int,
    presets: List<EqualizerPreset>,
    enabled: Boolean,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onResetToFlat: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = MusicTheme.dimensions
    val currentPresetName = when {
        selectedPresetIndex == EqualizerSettings.PRESET_CUSTOM -> stringResource(R.string.equalizer_preset_custom)
        else -> {
            val p = presets.find { it.index == selectedPresetIndex }
            if (p != null && p.nameResId != 0) stringResource(p.nameResId) else p?.name ?: stringResource(R.string.equalizer_preset_custom)
        }
    }

    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MusicTheme.colors.surfaceContainerHigh,
            contentColor = if (enabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(enabled = enabled) { expanded = true }
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                },
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceSmallMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmallMedium),
            ) {
                Text(
                    text = currentPresetName,
                    style = MusicTheme.typography.titleSmall,
                    color = if (enabled) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_common_dropdown),
                    contentDescription = stringResource(R.string.equalizer_preset_dropdown_desc),
                    tint = if (enabled) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // 自定义项
            val isCustomSelected = selectedPresetIndex == EqualizerSettings.PRESET_CUSTOM
            AppDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.equalizer_preset_custom),
                        color = if (isCustomSelected) MusicTheme.colors.primary else MusicTheme.colors.onSurface,
                        style = MusicTheme.typography.bodyMedium,
                    )
                },
                trailingIcon = if (isCustomSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_status_check),
                            contentDescription = null,
                            tint = MusicTheme.colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
                onClick = {
                    expanded = false
                },
            )

            HorizontalDivider(
                color = MusicTheme.colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
                modifier = Modifier.padding(horizontal = dimensions.spaceMedium),
            )

            // 预设列表
            presets.forEach { preset ->
                val isSelected = selectedPresetIndex == preset.index
                val displayName = if (preset.nameResId != 0) stringResource(preset.nameResId) else preset.name
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            color = if (isSelected) MusicTheme.colors.primary else MusicTheme.colors.onSurface,
                            style = MusicTheme.typography.bodyMedium,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_status_check),
                                contentDescription = null,
                                tint = MusicTheme.colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelectPreset(preset)
                    },
                )
            }
        }
    }
}
