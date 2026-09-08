package com.musicapp.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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

    override suspend fun setAlbumGridColumns(value: Int) {
        require(value in AppSettings.MIN_ALBUM_GRID_COLUMNS..AppSettings.MAX_ALBUM_GRID_COLUMNS) {
            "albumGridColumns must be between ${AppSettings.MIN_ALBUM_GRID_COLUMNS} and ${AppSettings.MAX_ALBUM_GRID_COLUMNS}"
        }
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_GRID_COLUMNS] = value
        }
    }

    override suspend fun setSleepTimerPreferences(durationMinutes: Int, extendToEndOfTrack: Boolean) {
        require(
            durationMinutes in AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES..AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES,
        ) {
            "durationMinutes must be between ${AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES} and ${AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES}"
        }
        dataStore.edit { preferences ->
            preferences[Keys.SLEEP_TIMER_DURATION_MINUTES] = durationMinutes
            preferences[Keys.SLEEP_TIMER_EXTEND_TO_END_OF_TRACK] = extendToEndOfTrack
        }
    }

    override suspend fun setLyricsFontSizeSp(value: Int) {
        require(value in AppSettings.MIN_LYRICS_FONT_SIZE_SP..AppSettings.MAX_LYRICS_FONT_SIZE_SP) {
            "lyricsFontSizeSp must be between ${AppSettings.MIN_LYRICS_FONT_SIZE_SP} and ${AppSettings.MAX_LYRICS_FONT_SIZE_SP}"
        }
        dataStore.edit { preferences ->
            preferences[Keys.LYRICS_FONT_SIZE_SP] = value
        }
    }

    override suspend fun setLyricsTextCentered(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LYRICS_TEXT_CENTERED] = value
        }
    }

    override suspend fun setLyricsFontWeight(value: Int) {
        require(value in AppSettings.MIN_LYRICS_FONT_WEIGHT..AppSettings.MAX_LYRICS_FONT_WEIGHT) {
            "lyricsFontWeight must be between ${AppSettings.MIN_LYRICS_FONT_WEIGHT} and ${AppSettings.MAX_LYRICS_FONT_WEIGHT}"
        }
        require((value - AppSettings.MIN_LYRICS_FONT_WEIGHT) % AppSettings.LYRICS_FONT_WEIGHT_STEP == 0) {
            "lyricsFontWeight must use ${AppSettings.LYRICS_FONT_WEIGHT_STEP} steps"
        }
        dataStore.edit { preferences ->
            preferences[Keys.LYRICS_FONT_WEIGHT] = value
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
            preferences.remove(Keys.ALBUM_GRID_COLUMNS)
            preferences.remove(Keys.SLEEP_TIMER_DURATION_MINUTES)
            preferences.remove(Keys.SLEEP_TIMER_EXTEND_TO_END_OF_TRACK)
            preferences.remove(Keys.LYRICS_FONT_SIZE_SP)
            preferences.remove(Keys.LYRICS_TEXT_CENTERED)
            preferences.remove(Keys.LYRICS_FONT_WEIGHT)
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
            albumGridColumns = preferences[Keys.ALBUM_GRID_COLUMNS]
                ?.takeIf { it in AppSettings.MIN_ALBUM_GRID_COLUMNS..AppSettings.MAX_ALBUM_GRID_COLUMNS }
                ?: defaults.albumGridColumns,
            sleepTimerDurationMinutes = preferences[Keys.SLEEP_TIMER_DURATION_MINUTES]
                ?.takeIf { it in AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES..AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES }
                ?: defaults.sleepTimerDurationMinutes,
            sleepTimerExtendToEndOfTrack = preferences[Keys.SLEEP_TIMER_EXTEND_TO_END_OF_TRACK]
                ?: defaults.sleepTimerExtendToEndOfTrack,
            lyricsFontSizeSp = preferences[Keys.LYRICS_FONT_SIZE_SP]
                ?.takeIf { it in AppSettings.MIN_LYRICS_FONT_SIZE_SP..AppSettings.MAX_LYRICS_FONT_SIZE_SP }
                ?: defaults.lyricsFontSizeSp,
            lyricsTextCentered = preferences[Keys.LYRICS_TEXT_CENTERED] ?: defaults.lyricsTextCentered,
            lyricsFontWeight = preferences[Keys.LYRICS_FONT_WEIGHT]
                ?.takeIf {
                    it in AppSettings.MIN_LYRICS_FONT_WEIGHT..AppSettings.MAX_LYRICS_FONT_WEIGHT &&
                        (it - AppSettings.MIN_LYRICS_FONT_WEIGHT) % AppSettings.LYRICS_FONT_WEIGHT_STEP == 0
                }
                ?: defaults.lyricsFontWeight,
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
        val ALBUM_GRID_COLUMNS = intPreferencesKey("album_grid_columns")
        val SLEEP_TIMER_DURATION_MINUTES = intPreferencesKey("sleep_timer_duration_minutes")
        val SLEEP_TIMER_EXTEND_TO_END_OF_TRACK = booleanPreferencesKey("sleep_timer_extend_to_end_of_track")
        val LYRICS_FONT_SIZE_SP = intPreferencesKey("lyrics_font_size_sp")
        val LYRICS_TEXT_CENTERED = booleanPreferencesKey("lyrics_text_centered")
        val LYRICS_FONT_WEIGHT = intPreferencesKey("lyrics_font_weight")
        val LIBRARY_SYNC_PENDING = booleanPreferencesKey("library_sync_pending")
        val LIBRARY_SYNC_REVISION = longPreferencesKey("library_sync_revision")
    }
}
