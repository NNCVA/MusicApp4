package com.musicapp.player.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import com.musicapp.player.core.designsystem.component.ConfirmationDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import androidx.compose.foundation.isSystemInDarkTheme
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.previewColor
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import kotlin.math.roundToInt

@Composable
fun SettingsScreenRoute(
    viewModel: SettingsViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onShowMessage: (Int) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        bottomPadding = bottomPadding,
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
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        onShowMessage(message.labelRes())
        onAcknowledgeMessage()
    }
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
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
            val workingProgressAlpha by animateFloatAsState(
                targetValue = if (state.isWorking || state.syncState is LibrarySyncState.Syncing) 1f else 0f,
                label = "settings-progress-alpha",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.spaceExtraSmall)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
            ) {
                if (workingProgressAlpha > 0f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = workingProgressAlpha },
                        trackColor = Color.Transparent,
                        color = MusicTheme.colors.primary,
                    )
                }
            }
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                contentPadding = PaddingValues(
                    top = dimensions.spaceSmallMedium,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
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
            text = stringResource(confirmation.descriptionRes()),
            confirmLabel = stringResource(confirmation.actionRes()),
            cancelLabel = stringResource(R.string.settings_cancel),
            onConfirm = onConfirmAction,
            onDismiss = onCancelConfirmation,
            isDestructive = true,
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
    val isPresetThemeEnabled = settings.colorSource != ColorSource.DYNAMIC
    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SettingsSection(stringResource(R.string.settings_appearance)) {
        ChoiceCardGrid(
            title = stringResource(R.string.settings_color_source),
            values = ColorSource.entries,
            selected = settings.colorSource,
            columns = 2,
            iconRes = { it.iconRes() },
            label = { stringResource(it.labelRes()) },
            onSelect = onColorSourceChange,
        )
        HorizontalDivider()
        ChoiceCardGrid(
            title = stringResource(R.string.settings_preset_theme),
            values = PresetTheme.entries,
            selected = settings.presetTheme,
            columns = 2,
            enabled = isPresetThemeEnabled,
            accentColor = { it.previewColor(isDark) },
            iconRes = { it.iconRes() },
            label = { stringResource(it.labelRes()) },
            onSelect = onPresetThemeChange,
        )
        HorizontalDivider()
        ChoiceCardGrid(
            title = stringResource(R.string.settings_theme_mode),
            values = ThemeMode.entries,
            selected = settings.themeMode,
            columns = 3,
            iconRes = { it.iconRes() },
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
        ChoiceCardGrid(
            title = stringResource(R.string.settings_aero_mode),
            values = AeroMode.entries,
            selected = selected,
            columns = 3,
            iconRes = { it.iconRes() },
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
internal fun <T> ChoiceCardGrid(
    title: String,
    values: List<T>,
    selected: T,
    columns: Int,
    iconRes: (T) -> Int,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    accentColor: ((T) -> Color)? = null,
) {
    Text(
        text = title,
        style = MusicTheme.typography.titleMedium,
        color = if (enabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurface.copy(alpha = 0.38f),
    )
    val rows = values.chunked(columns)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MusicTheme.dimensions.spaceSmallMedium),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicTheme.dimensions.spaceSmallMedium),
            ) {
                rowItems.forEach { value ->
                    ChoiceCard(
                        selected = value == selected,
                        iconRes = iconRes(value),
                        label = label(value),
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        accentColor = accentColor?.invoke(value),
                    )
                }
                val emptySlots = columns - rowItems.size
                repeat(emptySlots) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = null,
) {
    val dimensions = MusicTheme.dimensions
    val shape = MusicTheme.shapes.medium
    val accent = accentColor ?: MusicTheme.colors.primary

    val containerColor = when {
        !enabled -> if (selected) accent.copy(alpha = 0.04f) else Color.Transparent
        selected -> accent.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> if (selected) accent.copy(alpha = 0.38f) else MusicTheme.colors.outlineVariant.copy(alpha = 0.38f)
        selected -> accent
        else -> MusicTheme.colors.outlineVariant
    }
    val contentColor = if (enabled) accent else accent.copy(alpha = 0.38f)
    val labelColor = if (enabled) MusicTheme.colors.onSurface else MusicTheme.colors.onSurface.copy(alpha = 0.38f)

    Surface(
        modifier = modifier
            .heightIn(min = 84.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = if (selected && enabled) 1.5.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = dimensions.spaceMedium,
                    horizontal = dimensions.spaceExtraSmall,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.height(dimensions.spaceSmallMedium))
            Text(
                text = label,
                style = MusicTheme.typography.bodyMedium,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun <T> ChoiceGroup(
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MusicTheme.dimensions.minimumTouchTarget)
                .selectable(
                    selected = value == selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(value) },
                )
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = value == selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
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

private fun ColorSource.iconRes() = when (this) {
    ColorSource.DYNAMIC -> R.drawable.ic_common_auto_awesome
    ColorSource.PRESET -> R.drawable.ic_common_palette
}

private fun PresetTheme.labelRes() = when (this) {
    PresetTheme.DEFAULT_BLUE -> R.string.settings_theme_blue
    PresetTheme.EMERALD_GREEN -> R.string.settings_theme_emerald
    PresetTheme.SUNSET_ORANGE -> R.string.settings_theme_sunset
    PresetTheme.VIOLET -> R.string.settings_theme_violet
}

private fun PresetTheme.iconRes() = when (this) {
    PresetTheme.DEFAULT_BLUE,
    PresetTheme.EMERALD_GREEN,
    PresetTheme.SUNSET_ORANGE,
    PresetTheme.VIOLET -> R.drawable.ic_common_palette
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_system
    ThemeMode.LIGHT -> R.string.settings_light
    ThemeMode.DARK -> R.string.settings_dark
}

private fun ThemeMode.iconRes() = when (this) {
    ThemeMode.SYSTEM -> R.drawable.ic_common_android
    ThemeMode.LIGHT -> R.drawable.ic_common_light_mode
    ThemeMode.DARK -> R.drawable.ic_common_dark_mode
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

private fun AeroMode.iconRes() = when (this) {
    AeroMode.FLUID_MESH -> R.drawable.ic_common_grid_on
    AeroMode.GLOW_AURA -> R.drawable.ic_common_blur_circular
    AeroMode.SOLID -> R.drawable.ic_common_circle
}

private const val FADE_SLIDER_STEPS = 7
