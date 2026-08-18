package com.musicapp.player.feature.settings

import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.settings.PendingLibrarySyncState
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.feature.settings.data.SettingsSyncController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    initialLibrarySyncPending: Boolean = false,
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)
    private val mutablePendingLibrarySync = MutableStateFlow(
        PendingLibrarySyncState(isPending = initialLibrarySyncPending),
    )
    private val pendingMutex = Mutex()

    override val settings: StateFlow<AppSettings> = mutableSettings
    override val pendingLibrarySync: StateFlow<PendingLibrarySyncState> = mutablePendingLibrarySync

    override suspend fun currentSettings(): AppSettings = mutableSettings.value
    override suspend fun setColorSource(value: ColorSource) = update { copy(colorSource = value) }
    override suspend fun setPresetTheme(value: PresetTheme) = update { copy(presetTheme = value) }
    override suspend fun setThemeMode(value: ThemeMode) = update { copy(themeMode = value) }
    override suspend fun setAppLanguage(value: AppLanguage) = update { copy(appLanguage = value) }
    override suspend fun setAeroMode(value: AeroMode) = update { copy(aeroMode = value) }
    override suspend fun setFadeThroughDurationMs(value: Long) = update { copy(fadeThroughDurationMs = value) }
    override suspend fun setScanMode(value: ScanMode) = update { copy(scanMode = value) }
    override suspend fun setSkipShortAudio(value: Boolean) = update { copy(skipShortAudio = value) }

    override suspend fun markLibrarySyncPending(): Long = pendingMutex.withLock {
        val revision = mutablePendingLibrarySync.value.revision + 1
        mutablePendingLibrarySync.value = PendingLibrarySyncState(revision, isPending = true)
        revision
    }

    override suspend fun clearLibrarySyncPending(expectedRevision: Long): Boolean =
        pendingMutex.withLock {
            val current = mutablePendingLibrarySync.value
            if (!current.isPending || current.revision != expectedRevision) {
                false
            } else {
                mutablePendingLibrarySync.value = current.copy(isPending = false)
                true
            }
    }

    override suspend fun reset() {
        mutableSettings.value = AppSettings()
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        mutableSettings.value = mutableSettings.value.transform()
    }
}

internal class RecordingSettingsSyncController(
    initialState: LibrarySyncState = LibrarySyncState.Idle(hasSuccessfulScan = true),
) : SettingsSyncController {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableEvents = MutableSharedFlow<LibrarySyncEvent>(extraBufferCapacity = 4)
    override val state: StateFlow<LibrarySyncState> = mutableState
    override val events: SharedFlow<LibrarySyncEvent> = mutableEvents
    var manualSyncRequests: Int = 0
    var acknowledgedEventId: Long? = null
    var awaitedResult: LibrarySyncEvent? = null
    private val awaitedResults = Channel<LibrarySyncEvent>(capacity = Channel.UNLIMITED)

    override fun requestManualSync() {
        manualSyncRequests += 1
    }

    override suspend fun requestManualSyncAndAwait(): LibrarySyncEvent {
        manualSyncRequests += 1
        return awaitedResult?.also { awaitedResult = null } ?: awaitedResults.receive()
    }

    override fun acknowledgeFeedback(eventId: Long) {
        acknowledgedEventId = eventId
    }

    suspend fun emit(event: LibrarySyncEvent) {
        mutableEvents.emit(event)
    }

    fun completeAwaited(event: LibrarySyncEvent) {
        check(awaitedResults.trySend(event).isSuccess)
    }
}
