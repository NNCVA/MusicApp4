package com.musicapp.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class PreferencesSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationCoroutineScope applicationScope: CoroutineScope,
) : SettingsRepository {
    private val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::toAppSettings)

    override val settings: StateFlow<AppSettings> = settingsFlow
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    override val pendingLibrarySync: StateFlow<PendingLibrarySyncState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            PendingLibrarySyncState(
                revision = preferences[Keys.LIBRARY_SYNC_REVISION] ?: 0,
                isPending = preferences[Keys.LIBRARY_SYNC_PENDING] ?: false,
            )
        }
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = PendingLibrarySyncState(),
        )

    override suspend fun currentSettings(): AppSettings = settingsFlow.first()

    override suspend fun setColorSource(value: ColorSource) {
        setEnum(Keys.COLOR_SOURCE, value)
    }

    override suspend fun setPresetTheme(value: PresetTheme) {
        setEnum(Keys.PRESET_THEME, value)
    }

    override suspend fun setThemeMode(value: ThemeMode) {
        setEnum(Keys.THEME_MODE, value)
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        setEnum(Keys.APP_LANGUAGE, value)
    }

    override suspend fun setAeroMode(value: AeroMode) {
        setEnum(Keys.AERO_MODE, value)
    }

    override suspend fun setFadeThroughDurationMs(value: Long) {
        AppSettings(fadeThroughDurationMs = value)
        dataStore.edit { preferences ->
            preferences[Keys.FADE_THROUGH_DURATION_MS] = value
        }
    }

    override suspend fun setScanMode(value: ScanMode) {
        setEnum(Keys.SCAN_MODE, value)
    }

    override suspend fun setSkipShortAudio(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_SHORT_AUDIO] = value
        }
    }

    override suspend fun markLibrarySyncPending(): Long {
        var markedRevision = 0L
        dataStore.edit { preferences ->
            val currentRevision = preferences[Keys.LIBRARY_SYNC_REVISION] ?: 0
            check(currentRevision < Long.MAX_VALUE) { "library sync revision exhausted" }
            markedRevision = currentRevision + 1
            preferences[Keys.LIBRARY_SYNC_REVISION] = markedRevision
            preferences[Keys.LIBRARY_SYNC_PENDING] = true
        }
        return markedRevision
    }

    override suspend fun clearLibrarySyncPending(expectedRevision: Long): Boolean {
        require(expectedRevision >= 0) { "expectedRevision must not be negative" }
        var cleared = false
        dataStore.edit { preferences ->
            val currentRevision = preferences[Keys.LIBRARY_SYNC_REVISION] ?: 0
            if (currentRevision == expectedRevision && preferences[Keys.LIBRARY_SYNC_PENDING] == true) {
                preferences[Keys.LIBRARY_SYNC_PENDING] = false
                cleared = true
            }
        }
        return cleared
    }

    override suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.COLOR_SOURCE)
            preferences.remove(Keys.PRESET_THEME)
            preferences.remove(Keys.THEME_MODE)
            preferences.remove(Keys.APP_LANGUAGE)
            preferences.remove(Keys.AERO_MODE)
            preferences.remove(Keys.FADE_THROUGH_DURATION_MS)
            preferences.remove(Keys.SCAN_MODE)
            preferences.remove(Keys.SKIP_SHORT_AUDIO)
        }
    }

    private suspend fun setEnum(key: Preferences.Key<String>, value: Enum<*>) {
        dataStore.edit { preferences ->
            preferences[key] = value.name
        }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            colorSource = preferences.enumValue(Keys.COLOR_SOURCE, defaults.colorSource),
            presetTheme = preferences.enumValue(Keys.PRESET_THEME, defaults.presetTheme),
            themeMode = preferences.enumValue(Keys.THEME_MODE, defaults.themeMode),
            appLanguage = preferences.enumValue(Keys.APP_LANGUAGE, defaults.appLanguage),
            aeroMode = preferences.enumValue(Keys.AERO_MODE, defaults.aeroMode),
            fadeThroughDurationMs = preferences[Keys.FADE_THROUGH_DURATION_MS]
                ?.let { storedValue ->
                    runCatching {
                        defaults.copy(fadeThroughDurationMs = storedValue).fadeThroughDurationMs
                    }.getOrNull()
                }
                ?: defaults.fadeThroughDurationMs,
            scanMode = preferences.enumValue(Keys.SCAN_MODE, defaults.scanMode),
            skipShortAudio = preferences[Keys.SKIP_SHORT_AUDIO] ?: defaults.skipShortAudio,
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(
        key: Preferences.Key<String>,
        default: T,
    ): T = this[key]?.let { storedValue -> enumValues<T>().firstOrNull { it.name == storedValue } } ?: default

    private object Keys {
        val COLOR_SOURCE = stringPreferencesKey("color_source")
        val PRESET_THEME = stringPreferencesKey("preset_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val AERO_MODE = stringPreferencesKey("aero_mode")
        val FADE_THROUGH_DURATION_MS = longPreferencesKey("fade_through_duration_ms")
        val SCAN_MODE = stringPreferencesKey("scan_mode")
        val SKIP_SHORT_AUDIO = booleanPreferencesKey("skip_short_audio")
        val LIBRARY_SYNC_PENDING = booleanPreferencesKey("library_sync_pending")
        val LIBRARY_SYNC_REVISION = longPreferencesKey("library_sync_revision")
    }
}
