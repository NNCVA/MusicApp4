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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
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
        onScanModeChange = viewModel::setScanMode,
        onAddPathRule = viewModel::addPathRule,
        onRemovePathRule = { viewModel.removePathRule(it.id) },
        onConfirmPathRescan = viewModel::confirmPathRescan,
        onCancelPathRescan = viewModel::cancelPathRescan,
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
    onScanModeChange: (ScanMode) -> Unit,
    onAddPathRule: (String, String, PathRuleKind) -> Unit,
    onRemovePathRule: (PathRule) -> Unit,
    onConfirmPathRescan: () -> Unit,
    onCancelPathRescan: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().widthIn(max = dimensions.settingsContentMaxWidth)
                .padding(horizontal = dimensions.contentHorizontalPadding),
        ) {
            CategoryHeader(
                title = stringResource(R.string.navigation_settings),
                policy = policy,
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = onBack,
            )
            if (state.isWorking || state.syncState is LibrarySyncState.Syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                item { AppearanceSettings(state.settings, onColorSourceChange, onPresetThemeChange, onThemeModeChange) }
                item { LanguageSettings(state.settings.appLanguage, onLanguageChange) }
                item { AeroSettings(state.settings.aeroMode, onAeroModeChange) }
                item { FadeSettings(state.settings.fadeThroughDurationMs, onFadeDurationChange) }
                item { ScanModeSettings(state.settings.scanMode, state.pendingLibrarySync, onScanModeChange) }
                item { PathRuleEditor(onAddPathRule) }
                items(state.pathRules, key = { it.id.value }) { rule ->
                    PathRuleRow(rule, onRemovePathRule)
                }
                item { DataManagementSettings(onRequestConfirmation) }
            }
        }
    }
    if (state.rescanPromptVisible) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_rescan_title),
            description = stringResource(R.string.settings_rescan_description),
            confirmLabel = stringResource(R.string.settings_rescan_now),
            onConfirm = onConfirmPathRescan,
            onDismiss = onCancelPathRescan,
        )
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
private fun ScanModeSettings(
    selected: ScanMode,
    pendingLibrarySync: Boolean,
    onSelect: (ScanMode) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_library_scan)) {
        ChoiceGroup(
            title = stringResource(R.string.settings_scan_mode),
            values = ScanMode.entries,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
        )
        if (pendingLibrarySync) {
            Text(
                text = stringResource(R.string.settings_library_pending_sync),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun PathRuleEditor(onAdd: (String, String, PathRuleKind) -> Unit) {
    var volumeName by rememberSaveable { mutableStateOf("") }
    var directory by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(PathRuleKind.INCLUDE.name) }
    val kind = PathRuleKind.valueOf(kindName)
    SettingsSection(stringResource(R.string.settings_path_rules)) {
        OutlinedTextField(
            value = volumeName,
            onValueChange = { volumeName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_volume_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = directory,
            onValueChange = { directory = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_directory)) },
            singleLine = true,
        )
        ChoiceGroup(
            title = stringResource(R.string.settings_rule_kind),
            values = PathRuleKind.entries,
            selected = kind,
            label = { stringResource(it.labelRes()) },
            onSelect = { kindName = it.name },
        )
        Button(
            onClick = {
                onAdd(volumeName, directory, kind)
                directory = ""
            },
            enabled = volumeName.isNotBlank(),
            modifier = Modifier.heightIn(min = MusicTheme.dimensions.minimumTouchTarget),
            shape = MusicTheme.shapes.small,
        ) { Text(stringResource(R.string.settings_add_path_rule)) }
    }
}

@Composable
private fun PathRuleRow(rule: PathRule, onRemove: (PathRule) -> Unit) {
    val dimensions = MusicTheme.dimensions
    val directoryLabel =
        if (rule.directory.isEmpty()) stringResource(R.string.settings_volume_root) else rule.directory
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(directoryLabel, color = MusicTheme.colors.onSurface)
            Text(
                text = stringResource(
                    R.string.settings_path_rule_summary,
                    stringResource(rule.kind.labelRes()),
                    rule.volumeName,
                ),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onRemove(rule) }) {
            Text(stringResource(R.string.settings_remove_path_rule))
        }
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
        SettingsAction(R.string.settings_rebuild_library, R.string.settings_rebuild_library_summary) {
            onRequest(SettingsConfirmation.REBUILD_LIBRARY_CACHE)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Text(title, style = MusicTheme.typography.titleLarge)
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
    Text(title, style = MusicTheme.typography.titleMedium)
    values.forEach { value ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicTheme.dimensions.minimumTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = value == selected, onClick = { onSelect(value) })
            Text(label(value), style = MusicTheme.typography.bodyLarge)
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
        title = { Text(title) },
        text = { Text(description) },
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

private fun ScanMode.labelRes() = when (this) {
    ScanMode.ALL -> R.string.settings_scan_all
    ScanMode.SELECTED_DIRECTORIES -> R.string.settings_scan_selected
}

private fun PathRuleKind.labelRes() = when (this) {
    PathRuleKind.INCLUDE -> R.string.settings_path_include
    PathRuleKind.EXCLUDE -> R.string.settings_path_exclude
}

private const val FADE_SLIDER_STEPS = 7
