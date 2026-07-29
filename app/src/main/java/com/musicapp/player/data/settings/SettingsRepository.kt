package com.musicapp.player.data.settings

import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun currentSettings(): AppSettings

    suspend fun setColorSource(value: ColorSource)

    suspend fun setPresetTheme(value: PresetTheme)

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setAeroMode(value: AeroMode)

    suspend fun setFadeThroughDurationMs(value: Long)

    suspend fun setScanMode(value: ScanMode)

    suspend fun reset()
}
