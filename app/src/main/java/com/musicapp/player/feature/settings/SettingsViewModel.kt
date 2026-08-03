package com.musicapp.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import com.musicapp.player.feature.settings.data.DataManagementAction
import com.musicapp.player.feature.settings.data.DataManagementUseCase
import com.musicapp.player.feature.settings.data.PathRuleChangeCoordinator
import com.musicapp.player.feature.settings.data.SettingsSyncController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SettingsConfirmation {
    RESET_SETTINGS,
    CLEAR_HISTORY,
    DELETE_ALL_PLAYLISTS,
    REBUILD_LIBRARY_CACHE,
}

enum class SettingsMessage {
    SETTINGS_RESET,
    HISTORY_CLEARED,
    PLAYLISTS_DELETED,
    LIBRARY_REBUILT,
    ACTION_FAILED,
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val pathRules: List<PathRule> = emptyList(),
    val pendingLibrarySync: Boolean = false,
    val rescanPromptVisible: Boolean = false,
    val syncState: LibrarySyncState = LibrarySyncState.Idle(hasSuccessfulScan = false),
    val confirmation: SettingsConfirmation? = null,
    val message: SettingsMessage? = null,
    val isWorking: Boolean = false,
) {
    val pendingSyncFeedback: PendingLibrarySyncFeedback?
        get() = syncState.pendingFeedback
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    private val pathRuleChangeCoordinator: PathRuleChangeCoordinator,
    private val dataManagementUseCase: DataManagementUseCase,
    private val syncController: SettingsSyncController,
) : ViewModel() {
    private val controls = MutableStateFlow(SettingsControls())

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.settings,
            mediaLibraryRepository.observePathRules(),
            pathRuleChangeCoordinator.state,
            syncController.state,
            controls,
        ) { settings, pathRules, pathState, syncState, controls ->
            SettingsUiState(
                settings = settings,
                pathRules = pathRules,
                pendingLibrarySync = pathState.pendingLibrarySync,
                rescanPromptVisible = pathState.rescanPromptVisible,
                syncState = syncState,
                confirmation = controls.confirmation,
                message = controls.message,
                isWorking = controls.isWorking,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(settings = settingsRepository.settings.value),
        )

    fun setColorSource(value: ColorSource) = updateSetting { settingsRepository.setColorSource(value) }

    fun setPresetTheme(value: PresetTheme) = updateSetting { settingsRepository.setPresetTheme(value) }

    fun setThemeMode(value: ThemeMode) = updateSetting { settingsRepository.setThemeMode(value) }

    fun setAppLanguage(value: AppLanguage) = updateSetting { settingsRepository.setAppLanguage(value) }

    fun setAeroMode(value: AeroMode) = updateSetting { settingsRepository.setAeroMode(value) }

    fun setFadeThroughDurationMs(value: Long) = updateSetting {
        settingsRepository.setFadeThroughDurationMs(value)
    }

    fun setScanMode(value: ScanMode) {
        if (uiState.value.settings.scanMode == value) return
        updateSetting {
            settingsRepository.setScanMode(value)
            pathRuleChangeCoordinator.markScanPolicyChanged()
        }
    }

    fun addPathRule(volumeName: String, directory: String, kind: PathRuleKind) = updateSetting {
        pathRuleChangeCoordinator.addPathRule(volumeName.trim(), directory, kind)
    }

    fun removePathRule(ruleId: PathRuleId) = updateSetting {
        pathRuleChangeCoordinator.removePathRule(ruleId)
    }

    fun confirmPathRescan() = pathRuleChangeCoordinator.confirmRescan()

    fun cancelPathRescan() = pathRuleChangeCoordinator.cancelRescan()

    fun requestConfirmation(confirmation: SettingsConfirmation) {
        controls.value = controls.value.copy(confirmation = confirmation)
    }

    fun cancelConfirmation() {
        controls.value = controls.value.copy(confirmation = null)
    }

    fun confirmAction() {
        val confirmation = controls.value.confirmation ?: return
        if (controls.value.isWorking) return
        controls.value = controls.value.copy(confirmation = null, isWorking = true, message = null)
        viewModelScope.launch {
            val message =
                try {
                    when (confirmation) {
                        SettingsConfirmation.RESET_SETTINGS -> {
                            val scanModeChanged =
                                settingsRepository.settings.value.scanMode != AppSettings().scanMode
                            settingsRepository.reset()
                            if (scanModeChanged) pathRuleChangeCoordinator.markScanPolicyChanged()
                        }
                        SettingsConfirmation.CLEAR_HISTORY ->
                            dataManagementUseCase.execute(DataManagementAction.CLEAR_HISTORY)
                        SettingsConfirmation.DELETE_ALL_PLAYLISTS ->
                            dataManagementUseCase.execute(DataManagementAction.DELETE_ALL_PLAYLISTS)
                        SettingsConfirmation.REBUILD_LIBRARY_CACHE ->
                            dataManagementUseCase.execute(DataManagementAction.REBUILD_LIBRARY_CACHE)
                    }
                    confirmation.successMessage
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    SettingsMessage.ACTION_FAILED
                }
            controls.value = controls.value.copy(isWorking = false, message = message)
        }
    }

    fun acknowledgeMessage() {
        controls.value = controls.value.copy(message = null)
    }

    fun acknowledgeSyncFeedback(eventId: Long) = syncController.acknowledgeFeedback(eventId)

    private fun updateSetting(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                controls.value = controls.value.copy(message = SettingsMessage.ACTION_FAILED)
            }
        }
    }
}

private data class SettingsControls(
    val confirmation: SettingsConfirmation? = null,
    val message: SettingsMessage? = null,
    val isWorking: Boolean = false,
)

private val SettingsConfirmation.successMessage: SettingsMessage
    get() =
        when (this) {
            SettingsConfirmation.RESET_SETTINGS -> SettingsMessage.SETTINGS_RESET
            SettingsConfirmation.CLEAR_HISTORY -> SettingsMessage.HISTORY_CLEARED
            SettingsConfirmation.DELETE_ALL_PLAYLISTS -> SettingsMessage.PLAYLISTS_DELETED
            SettingsConfirmation.REBUILD_LIBRARY_CACHE -> SettingsMessage.LIBRARY_REBUILT
        }

private const val STOP_TIMEOUT_MS = 5_000L
