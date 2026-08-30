package com.musicapp.player.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlin.math.roundToInt

@Composable
fun SettingsScreenRoute(
    viewModel: SettingsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onColorSourceChange = viewModel::setColorSource,
        onPresetThemeChange = viewModel::setPresetTheme,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setAppLanguage,
        onAeroModeChange = viewModel::setAeroMode,
        onFadeDurationChange = viewModel::setFadeThroughDurationMs,
        onRequestConfirmation = viewModel::requestConfirmation,
        onCancelConfirmation = viewModel::cancelConfirmation,
        onConfirmAction = viewModel::confirmAction,
        onAcknowledgeMessage = viewModel::acknowledgeMessage,
        onShowMessage = onShowMessage,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onPresetThemeChange: (PresetTheme) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAeroModeChange: (AeroMode) -> Unit,
    onFadeDurationChange: (Long) -> Unit,
    onRequestConfirmation: (SettingsConfirmation) -> Unit,
    onCancelConfirmation: () -> Unit,
    onConfirmAction: () -> Unit,
    onAcknowledgeMessage: () -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        onShowMessage(message.labelRes())
        onAcknowledgeMessage()
    }
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = dimensions.settingsContentMaxWidth),
        ) {
            CategoryHeader(
                title = stringResource(R.string.navigation_settings),
                policy = policy,
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = onBack,
            )
            if (state.isWorking || state.syncState is LibrarySyncState.Syncing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                item { AppearanceSettings(state.settings, onColorSourceChange, onPresetThemeChange, onThemeModeChange) }
                item { LanguageSettings(state.settings.appLanguage, onLanguageChange) }
                item { AeroSettings(state.settings.aeroMode, onAeroModeChange) }
                item { FadeSettings(state.settings.fadeThroughDurationMs, onFadeDurationChange) }
                item { DataManagementSettings(onRequestConfirmation) }
            }
        }
    }
    state.confirmation?.let { confirmation ->
        ConfirmationDialog(
            title = stringResource(confirmation.titleRes()),
            description = stringResource(confirmation.descriptionRes()),
            confirmLabel = stringResource(confirmation.actionRes()),
            onConfirm = onConfirmAction,
            onDismiss = onCancelConfirmation,
        )
    }
}

@Composable
private fun AppearanceSettings(
    settings: AppSettings,
    onColorSourceChange: (ColorSource) -> Unit,
    onPresetThemeChange: (PresetTheme) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_appearance)) {
        ChoiceGroup(
            title = stringResource(R.string.settings_color_source),
            values = ColorSource.entries,
            selected = settings.colorSource,
            label = { stringResource(it.labelRes()) },
            onSelect = onColorSourceChange,
        )
        ChoiceGroup(
            title = stringResource(R.string.settings_preset_theme),
            values = PresetTheme.entries,
            selected = settings.presetTheme,
            label = { stringResource(it.labelRes()) },
            onSelect = onPresetThemeChange,
        )
        ChoiceGroup(
            title = stringResource(R.string.settings_theme_mode),
            values = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onThemeModeChange,
        )
    }
}

@Composable
private fun LanguageSettings(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    SettingsSection(stringResource(R.string.settings_language)) {
        ChoiceGroup(
            title = stringResource(R.string.settings_language),
            values = AppLanguage.entries,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AeroSettings(selected: AeroMode, onSelect: (AeroMode) -> Unit) {
    SettingsSection(stringResource(R.string.settings_aero)) {
        ChoiceGroup(
            title = stringResource(R.string.settings_aero_mode),
            values = AeroMode.entries,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun FadeSettings(value: Long, onValueChange: (Long) -> Unit) {
    var draft by rememberSaveable { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { draft = value.toFloat() }
    SettingsSection(stringResource(R.string.settings_playback)) {
        Text(
            text = stringResource(R.string.settings_fade_duration, draft.roundToInt()),
            style = MusicTheme.typography.titleMedium,
            color = MusicTheme.colors.onSurface,
        )
        Slider(
            value = draft,
            onValueChange = { raw ->
                val step = AppSettings.FADE_THROUGH_STEP_MS.toFloat()
                draft = (raw / step).roundToInt() * step
            },
            onValueChangeFinished = { onValueChange(draft.toLong()) },
            valueRange = AppSettings.MIN_FADE_THROUGH_DURATION_MS.toFloat()..
                AppSettings.MAX_FADE_THROUGH_DURATION_MS.toFloat(),
            steps = FADE_SLIDER_STEPS,
        )
        Text(
            text = stringResource(R.string.settings_fade_effective_next_transition),
            style = MusicTheme.typography.bodySmall,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataManagementSettings(onRequest: (SettingsConfirmation) -> Unit) {
    SettingsSection(stringResource(R.string.settings_data_management)) {
        SettingsAction(R.string.settings_reset, R.string.settings_reset_summary) {
            onRequest(SettingsConfirmation.RESET_SETTINGS)
        }
        SettingsAction(R.string.settings_clear_history, R.string.settings_clear_history_summary) {
            onRequest(SettingsConfirmation.CLEAR_HISTORY)
        }
        SettingsAction(R.string.settings_delete_playlists, R.string.settings_delete_playlists_summary) {
            onRequest(SettingsConfirmation.DELETE_ALL_PLAYLISTS)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Text(
                text = title,
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(
        text = title,
        style = MusicTheme.typography.titleMedium,
        color = MusicTheme.colors.onSurface,
    )
    values.forEach { value ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicTheme.dimensions.minimumTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = value == selected, onClick = { onSelect(value) })
            Text(
                text = label(value),
                style = MusicTheme.typography.bodyLarge,
                color = MusicTheme.colors.onSurface,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun SettingsAction(titleRes: Int, summaryRes: Int, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onClick) { Text(stringResource(titleRes)) }
        Text(
            text = stringResource(summaryRes),
            style = MusicTheme.typography.bodySmall,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MusicTheme.colors.onSurface) },
        text = { Text(description, color = MusicTheme.colors.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private fun SettingsConfirmation.titleRes(): Int =
    when (this) {
        SettingsConfirmation.RESET_SETTINGS -> R.string.settings_reset_confirm_title
        SettingsConfirmation.CLEAR_HISTORY -> R.string.settings_clear_history_confirm_title
        SettingsConfirmation.DELETE_ALL_PLAYLISTS -> R.string.settings_delete_playlists_confirm_title
        SettingsConfirmation.REBUILD_LIBRARY_CACHE -> R.string.settings_rebuild_library_confirm_title
    }

private fun SettingsConfirmation.descriptionRes(): Int =
    when (this) {
        SettingsConfirmation.RESET_SETTINGS -> R.string.settings_reset_confirm_description
        SettingsConfirmation.CLEAR_HISTORY -> R.string.settings_clear_history_confirm_description
        SettingsConfirmation.DELETE_ALL_PLAYLISTS -> R.string.settings_delete_playlists_confirm_description
        SettingsConfirmation.REBUILD_LIBRARY_CACHE -> R.string.settings_rebuild_library_confirm_description
    }

private fun SettingsConfirmation.actionRes(): Int =
    when (this) {
        SettingsConfirmation.RESET_SETTINGS -> R.string.settings_reset_confirm_action
        SettingsConfirmation.CLEAR_HISTORY -> R.string.settings_clear_history_confirm_action
        SettingsConfirmation.DELETE_ALL_PLAYLISTS -> R.string.settings_delete_playlists_confirm_action
        SettingsConfirmation.REBUILD_LIBRARY_CACHE -> R.string.settings_rebuild_library_confirm_action
    }

private fun SettingsMessage.labelRes(): Int =
    when (this) {
        SettingsMessage.SETTINGS_RESET -> R.string.settings_reset_done
        SettingsMessage.HISTORY_CLEARED -> R.string.settings_clear_history_done
        SettingsMessage.PLAYLISTS_DELETED -> R.string.settings_delete_playlists_done
        SettingsMessage.LIBRARY_REBUILT -> R.string.settings_rebuild_library_done
        SettingsMessage.ACTION_FAILED -> R.string.settings_action_failed
    }

private fun ColorSource.labelRes() = when (this) {
    ColorSource.DYNAMIC -> R.string.settings_color_dynamic
    ColorSource.PRESET -> R.string.settings_color_preset
}

private fun PresetTheme.labelRes() = when (this) {
    PresetTheme.DEFAULT_BLUE -> R.string.settings_theme_blue
    PresetTheme.EMERALD_GREEN -> R.string.settings_theme_emerald
    PresetTheme.SUNSET_ORANGE -> R.string.settings_theme_sunset
    PresetTheme.VIOLET -> R.string.settings_theme_violet
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_system
    ThemeMode.LIGHT -> R.string.settings_light
    ThemeMode.DARK -> R.string.settings_dark
}

private fun AppLanguage.labelRes() = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_system
    AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_chinese
    AppLanguage.ENGLISH -> R.string.settings_language_english
}

private fun AeroMode.labelRes() = when (this) {
    AeroMode.FLUID_MESH -> R.string.settings_aero_fluid_mesh
    AeroMode.GLOW_AURA -> R.string.settings_aero_glow_aura
    AeroMode.SOLID -> R.string.settings_aero_solid
}

private const val FADE_SLIDER_STEPS = 7
