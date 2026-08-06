package com.musicapp.player.data.settings

import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class PendingLibrarySyncState(
    val revision: Long = 0,
    val isPending: Boolean = false,
) {
    init {
        require(revision >= 0) { "revision must not be negative" }
    }
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    val pendingLibrarySync: StateFlow<PendingLibrarySyncState>
        get() = NO_PENDING_LIBRARY_SYNC

    suspend fun currentSettings(): AppSettings

    suspend fun setColorSource(value: ColorSource)

    suspend fun setPresetTheme(value: PresetTheme)

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setAeroMode(value: AeroMode)

    suspend fun setFadeThroughDurationMs(value: Long)

    suspend fun setScanMode(value: ScanMode)

    suspend fun setSkipShortAudio(value: Boolean)

    suspend fun markLibrarySyncPending(): Long = pendingLibrarySync.value.revision

    suspend fun clearLibrarySyncPending(expectedRevision: Long): Boolean = false

    suspend fun reset()

    companion object {
        private val NO_PENDING_LIBRARY_SYNC = MutableStateFlow(PendingLibrarySyncState())
    }
}
