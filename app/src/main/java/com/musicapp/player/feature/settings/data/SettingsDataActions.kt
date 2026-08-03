package com.musicapp.player.feature.settings.data

import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface SettingsSyncController {
    val state: StateFlow<LibrarySyncState>
    val events: SharedFlow<LibrarySyncEvent>

    fun requestManualSync()

    suspend fun requestManualSyncAndAwait(): LibrarySyncEvent

    fun acknowledgeFeedback(eventId: Long)
}

@Singleton
class DefaultSettingsSyncController @Inject constructor(
    private val coordinator: LibrarySyncCoordinator,
) : SettingsSyncController {
    override val state: StateFlow<LibrarySyncState>
        get() = coordinator.state
    override val events: SharedFlow<LibrarySyncEvent>
        get() = coordinator.events

    override fun requestManualSync() = coordinator.requestManualSync()

    override suspend fun requestManualSyncAndAwait(): LibrarySyncEvent =
        coordinator.requestManualSyncAndAwait()

    override fun acknowledgeFeedback(eventId: Long) = coordinator.acknowledgeFeedback(eventId)
}

data class PathRuleChangeState(
    val revision: Long = 0,
    val pendingLibrarySync: Boolean = false,
    val rescanPromptVisible: Boolean = false,
)

@Singleton
class PathRuleChangeCoordinator @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val syncController: SettingsSyncController,
    @ApplicationCoroutineScope private val applicationScope: CoroutineScope,
) {
    private val initialPendingState = settingsRepository.pendingLibrarySync.value
    private val mutableState = MutableStateFlow(
        PathRuleChangeState(
            revision = initialPendingState.revision,
            pendingLibrarySync = initialPendingState.isPending,
        ),
    )
    val state: StateFlow<PathRuleChangeState> = mutableState.asStateFlow()

    init {
        applicationScope.launch {
            settingsRepository.pendingLibrarySync.collect { pending ->
                mutableState.value = mutableState.value.copy(
                    revision = pending.revision,
                    pendingLibrarySync = pending.isPending,
                    rescanPromptVisible = mutableState.value.rescanPromptVisible && pending.isPending,
                )
            }
        }
    }

    suspend fun addPathRule(
        volumeName: String,
        directory: String,
        kind: PathRuleKind,
    ): PathRule = mediaLibraryRepository.addPathRule(volumeName, directory, kind).also {
        markLibraryOutOfSync()
    }

    suspend fun removePathRule(ruleId: PathRuleId) {
        mediaLibraryRepository.removePathRule(ruleId)
        markLibraryOutOfSync()
    }

    suspend fun markScanPolicyChanged() {
        markLibraryOutOfSync()
    }

    fun confirmRescan() {
        val revision = mutableState.value
            .takeIf { it.rescanPromptVisible && it.pendingLibrarySync }
            ?.revision
            ?: return
        mutableState.value = mutableState.value.copy(rescanPromptVisible = false)
        applicationScope.launch {
            val event = syncController.requestManualSyncAndAwait()
            if (event is LibrarySyncEvent.Completed) {
                settingsRepository.clearLibrarySyncPending(revision)
            } else if (
                mutableState.value.revision == revision &&
                mutableState.value.pendingLibrarySync
            ) {
                mutableState.value = mutableState.value.copy(rescanPromptVisible = true)
            }
        }
    }

    fun cancelRescan() {
        mutableState.value = mutableState.value.copy(rescanPromptVisible = false)
    }

    private suspend fun markLibraryOutOfSync() {
        val revision = settingsRepository.markLibrarySyncPending()
        mutableState.value = PathRuleChangeState(
            revision = revision,
            pendingLibrarySync = true,
            rescanPromptVisible = true,
        )
    }
}

enum class DataManagementAction {
    CLEAR_HISTORY,
    DELETE_ALL_PLAYLISTS,
    REBUILD_LIBRARY_CACHE,
}

class DataManagementUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val syncController: SettingsSyncController,
) {
    suspend fun execute(action: DataManagementAction) {
        when (action) {
            DataManagementAction.CLEAR_HISTORY -> historyRepository.clearHistory()
            DataManagementAction.DELETE_ALL_PLAYLISTS -> playlistRepository.deleteAllPlaylists()
            DataManagementAction.REBUILD_LIBRARY_CACHE -> {
                check(syncController.requestManualSyncAndAwait() is LibrarySyncEvent.Completed) {
                    "media library rebuild failed"
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataModule {
    @Binds
    @Singleton
    abstract fun bindSettingsSyncController(
        implementation: DefaultSettingsSyncController,
    ): SettingsSyncController
}
