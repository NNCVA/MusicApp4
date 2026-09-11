package com.musicapp.player.feature.equalizer

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.ChoiceRow
import com.musicapp.player.core.designsystem.component.InsetPillSlider
import com.musicapp.player.core.designsystem.component.SettingsSection
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlin.math.roundToInt

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
        onBassBoostEnabledChange = viewModel::setBassBoostEnabled,
        onBassBoostStrengthChange = viewModel::setBassBoostStrength,
        onVirtualizerEnabledChange = viewModel::setVirtualizerEnabled,
        onVirtualizerStrengthChange = viewModel::setVirtualizerStrength,
        bottomPadding = bottomPadding,
    )
}

@Composable
private fun CustomEqualizerScreen(
    state: CustomEqualizerUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onResetToFlat: () -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Int) -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
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
                    start = dimensions.contentHorizontalPadding,
                    end = dimensions.contentHorizontalPadding,
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceLarge + bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                // 1. Master Toggle
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MusicTheme.shapes.large,
                        color = MusicTheme.aeroCardContainerColor,
                        contentColor = MusicTheme.colors.onSurface,
                    ) {
                        ChoiceRow(
                            title = stringResource(R.string.equalizer_custom_enable),
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

                // 2. Presets Selection & Reset
                item {
                    SettingsSection(title = stringResource(R.string.equalizer_presets)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensions.spaceSmall),
                        ) {
                            val scrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState)
                                    .padding(horizontal = dimensions.spaceMedium),
                                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Custom chip
                                val isCustomSelected = state.selectedPresetIndex == EqualizerSettings.PRESET_CUSTOM
                                EqualizerChip(
                                    label = stringResource(R.string.equalizer_preset_custom),
                                    selected = isCustomSelected,
                                    enabled = state.isEnabled,
                                    onClick = {},
                                )

                                state.presets.forEach { preset ->
                                    val isSelected = state.selectedPresetIndex == preset.index
                                    EqualizerChip(
                                        label = preset.name,
                                        selected = isSelected,
                                        enabled = state.isEnabled,
                                        onClick = { onSelectPreset(preset) },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = dimensions.spaceMedium,
                                        end = dimensions.spaceMedium,
                                        top = dimensions.spaceSmall,
                                    ),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = onResetToFlat,
                                    enabled = state.isEnabled,
                                ) {
                                    Text(
                                        text = stringResource(R.string.equalizer_reset_to_flat),
                                        style = MusicTheme.typography.labelMedium,
                                        color = if (state.isEnabled) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Frequency Bands Sliders
                item {
                    SettingsSection(title = stringResource(R.string.equalizer_bands)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                        ) {
                            state.bands.forEach { band ->
                                BandSliderRow(
                                    band = band,
                                    enabled = state.isEnabled,
                                    onLevelChange = { newLevel -> onBandLevelChange(band.index, newLevel) },
                                )
                            }
                        }
                    }
                }

                // 4. Bass Boost & Virtualizer
                item {
                    SettingsSection(title = stringResource(R.string.playback_equalizer)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
                        ) {
                            // Bass Boost
                            ChoiceRow(
                                title = stringResource(R.string.equalizer_bass_boost),
                                subtitle = stringResource(R.string.equalizer_effect_strength, (state.bassBoostStrength / 10)),
                                interactionModifier = Modifier.toggleable(
                                    value = state.bassBoostEnabled,
                                    role = Role.Switch,
                                    enabled = state.isEnabled,
                                    onValueChange = onBassBoostEnabledChange,
                                ),
                                trailingContent = {
                                    Switch(
                                        checked = state.bassBoostEnabled,
                                        onCheckedChange = null,
                                        enabled = state.isEnabled,
                                        modifier = Modifier.clearAndSetSemantics {},
                                    )
                                },
                            )
                            InsetPillSlider(
                                value = state.bassBoostStrength.toFloat(),
                                onValueChange = { onBassBoostStrengthChange(it.roundToInt()) },
                                enabled = state.isEnabled && state.bassBoostEnabled,
                                valueRange = EqualizerSettings.MIN_EFFECT_STRENGTH.toFloat()..EqualizerSettings.MAX_EFFECT_STRENGTH.toFloat(),
                                steps = 20,
                            )

                            Spacer(modifier = Modifier.height(dimensions.spaceExtraSmall))

                            // Virtualizer
                            ChoiceRow(
                                title = stringResource(R.string.equalizer_virtualizer),
                                subtitle = stringResource(R.string.equalizer_effect_strength, (state.virtualizerStrength / 10)),
                                interactionModifier = Modifier.toggleable(
                                    value = state.virtualizerEnabled,
                                    role = Role.Switch,
                                    enabled = state.isEnabled,
                                    onValueChange = onVirtualizerEnabledChange,
                                ),
                                trailingContent = {
                                    Switch(
                                        checked = state.virtualizerEnabled,
                                        onCheckedChange = null,
                                        enabled = state.isEnabled,
                                        modifier = Modifier.clearAndSetSemantics {},
                                    )
                                },
                            )
                            InsetPillSlider(
                                value = state.virtualizerStrength.toFloat(),
                                onValueChange = { onVirtualizerStrengthChange(it.roundToInt()) },
                                enabled = state.isEnabled && state.virtualizerEnabled,
                                valueRange = EqualizerSettings.MIN_EFFECT_STRENGTH.toFloat()..EqualizerSettings.MAX_EFFECT_STRENGTH.toFloat(),
                                steps = 20,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSliderRow(
    band: EqualizerBand,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatFrequency(band.centerFreqHz),
                style = MusicTheme.typography.labelMedium,
                color = if (enabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
            )
            Text(
                text = formatDb(band.levelMb),
                style = MusicTheme.typography.bodySmall,
                color = if (enabled) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Disabled),
            )
        }
        InsetPillSlider(
            value = band.levelMb.toFloat(),
            onValueChange = { onLevelChange(it.roundToInt()) },
            enabled = enabled,
            valueRange = EqualizerSettings.MIN_BAND_LEVEL_MB.toFloat()..EqualizerSettings.MAX_BAND_LEVEL_MB.toFloat(),
            steps = 30,
        )
    }
}

@Composable
private fun EqualizerChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val containerColor = if (selected) {
        MusicTheme.colors.primaryContainer
    } else {
        MusicTheme.colors.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MusicTheme.colors.onPrimaryContainer
    } else {
        MusicTheme.colors.onSurfaceVariant
    }
    Surface(
        shape = MusicTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            style = MusicTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceSmall),
        )
    }
}

private fun formatFrequency(freqHz: Int): String {
    return if (freqHz >= 1000) {
        val kHz = freqHz / 1000.0
        if (freqHz % 1000 == 0) "${freqHz / 1000} kHz" else "%.1f kHz".format(kHz)
    } else {
        "$freqHz Hz"
    }
}

private fun formatDb(levelMb: Int): String {
    val db = levelMb / 100.0
    return if (db > 0) "+%.1f dB".format(db) else "%.1f dB".format(db)
}
