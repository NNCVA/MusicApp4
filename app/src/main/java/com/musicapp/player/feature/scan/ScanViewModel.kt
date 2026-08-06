package com.musicapp.player.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.feature.settings.data.PathRuleChangeCoordinator
import com.musicapp.player.feature.tracks.TracksSyncController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanUiState(
    val settings: AppSettings = AppSettings(),
    val pathRules: List<PathRule> = emptyList(),
    val syncState: LibrarySyncState = LibrarySyncState.Idle(hasSuccessfulScan = false),
    val actionFailed: Boolean = false,
) {
    val includeFolders: List<PathRule>
        get() = pathRules.filter { it.kind == PathRuleKind.INCLUDE }

    val blockedFolders: List<PathRule>
        get() = pathRules.filter { it.kind == PathRuleKind.EXCLUDE }

    val isScanning: Boolean
        get() = syncState is LibrarySyncState.Syncing

    val canScan: Boolean
        get() = settings.scanMode == ScanMode.ALL || includeFolders.isNotEmpty()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    private val pathRuleChangeCoordinator: PathRuleChangeCoordinator,
    tracksSyncController: TracksSyncController,
) : ViewModel() {
    private val actionFailed = MutableStateFlow(false)

    val uiState: StateFlow<ScanUiState> =
        combine(
            settingsRepository.settings,
            mediaLibraryRepository.observePathRules(),
            tracksSyncController.state,
            actionFailed,
        ) { settings, pathRules, syncState, failed ->
            ScanUiState(
                settings = settings,
                pathRules = pathRules,
                syncState = syncState,
                actionFailed = failed,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ScanUiState(settings = settingsRepository.settings.value),
        )

    fun setUseAndroidMediaLibrary(enabled: Boolean) {
        val mode = if (enabled) ScanMode.ALL else ScanMode.SELECTED_DIRECTORIES
        if (settingsRepository.settings.value.scanMode == mode) return
        update {
            settingsRepository.setScanMode(mode)
            pathRuleChangeCoordinator.markScanPolicyChanged()
        }
    }

    fun setSkipShortAudio(enabled: Boolean) {
        if (settingsRepository.settings.value.skipShortAudio == enabled) return
        update {
            settingsRepository.setSkipShortAudio(enabled)
            pathRuleChangeCoordinator.markScanPolicyChanged()
        }
    }

    fun addFolder(volumeName: String, directory: String, kind: PathRuleKind = PathRuleKind.INCLUDE) {
        update {
            pathRuleChangeCoordinator.addPathRule(volumeName, directory, kind)
        }
    }

    fun removeFolder(ruleId: PathRuleId) {
        update {
            pathRuleChangeCoordinator.removePathRule(ruleId)
        }
    }

    fun acknowledgeActionFailure() {
        actionFailed.value = false
    }

    private fun update(action: suspend () -> Unit) {
        actionFailed.value = false
        viewModelScope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                actionFailed.value = true
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
