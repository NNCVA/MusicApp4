package com.musicapp.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicapp.player.data.repository.api.InvalidInputReason
import com.musicapp.player.data.repository.api.RepositoryError
import com.musicapp.player.data.repository.api.RepositoryResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ColorSource { SYSTEM_DYNAMIC, PRESET }
enum class PresetTheme { DEFAULT_BLUE, EMERALD_GREEN, SUNSET_ORANGE, VIOLET }
enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }
enum class AppLanguage { FOLLOW_SYSTEM, SIMPLIFIED_CHINESE, ENGLISH }
enum class AeroMode { FLUID_MESH, GLOW_AURA, STATIC_COLOR }
enum class ScanMode { ALL, SELECTED_DIRECTORIES }

data class AppSettings(
  val colorSource: ColorSource = ColorSource.SYSTEM_DYNAMIC,
  val presetTheme: PresetTheme = PresetTheme.DEFAULT_BLUE,
  val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
  val appLanguage: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
  val aeroMode: AeroMode = AeroMode.GLOW_AURA,
  val fadeDurationMillis: Int = DEFAULT_FADE_DURATION_MILLIS,
  val scanMode: ScanMode = ScanMode.ALL,
  val libraryNeedsSync: Boolean = false,
) {
  companion object {
    const val DEFAULT_FADE_DURATION_MILLIS = 500
    const val MIN_FADE_DURATION_MILLIS = 0
    const val MAX_FADE_DURATION_MILLIS = 2_000
    const val FADE_DURATION_STEP_MILLIS = 250
  }
}

interface SettingsRepository {
  val settings: StateFlow<AppSettings>

  suspend fun setColorSource(value: ColorSource): RepositoryResult<Unit>
  suspend fun setPresetTheme(value: PresetTheme): RepositoryResult<Unit>
  suspend fun setDarkMode(value: DarkMode): RepositoryResult<Unit>
  suspend fun setAppLanguage(value: AppLanguage): RepositoryResult<Unit>
  suspend fun setAeroMode(value: AeroMode): RepositoryResult<Unit>
  suspend fun setScanMode(value: ScanMode): RepositoryResult<Unit>
  suspend fun setLibraryNeedsSync(value: Boolean): RepositoryResult<Unit>
  suspend fun setFadeDurationMillis(value: Int): RepositoryResult<Unit>
  suspend fun reset(): RepositoryResult<Unit>
}

class DataStoreSettingsRepository(
  private val dataStore: DataStore<Preferences>,
  private val dynamicColorSupported: Boolean,
  scope: CoroutineScope,
) : SettingsRepository {
  private val defaults =
    AppSettings(
      colorSource = if (dynamicColorSupported) ColorSource.SYSTEM_DYNAMIC else ColorSource.PRESET,
    )

  override val settings: StateFlow<AppSettings> =
    dataStore.data
      .catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
      }
      .map(::decode)
      .stateIn(scope, SharingStarted.Eagerly, defaults)

  override suspend fun setColorSource(value: ColorSource) = settingsWrite { update(Keys.COLOR_SOURCE, value.name) }
  override suspend fun setPresetTheme(value: PresetTheme) = settingsWrite { update(Keys.PRESET_THEME, value.name) }
  override suspend fun setDarkMode(value: DarkMode) = settingsWrite { update(Keys.DARK_MODE, value.name) }
  override suspend fun setAppLanguage(value: AppLanguage) = settingsWrite { update(Keys.APP_LANGUAGE, value.name) }
  override suspend fun setAeroMode(value: AeroMode) = settingsWrite { update(Keys.AERO_MODE, value.name) }
  override suspend fun setScanMode(value: ScanMode) = settingsWrite { update(Keys.SCAN_MODE, value.name) }
  override suspend fun setLibraryNeedsSync(value: Boolean) = settingsWrite { update(Keys.LIBRARY_NEEDS_SYNC, value) }

  override suspend fun setFadeDurationMillis(value: Int): RepositoryResult<Unit> =
    if (!isValidFadeDuration(value)) {
      RepositoryResult.Failure(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
    } else settingsWrite { update(Keys.FADE_DURATION_MILLIS, value) }

  override suspend fun reset(): RepositoryResult<Unit> = settingsWrite { dataStore.edit { it.clear() } }

  private suspend fun <T> update(key: Preferences.Key<T>, value: T) {
    dataStore.edit { preferences -> preferences[key] = value }
  }

  private fun decode(preferences: Preferences): AppSettings =
    AppSettings(
      colorSource =
        enumOrDefault(
          preferences[Keys.COLOR_SOURCE],
          if (dynamicColorSupported) ColorSource.SYSTEM_DYNAMIC else ColorSource.PRESET,
        ),
      presetTheme = enumOrDefault(preferences[Keys.PRESET_THEME], PresetTheme.DEFAULT_BLUE),
      darkMode = enumOrDefault(preferences[Keys.DARK_MODE], DarkMode.FOLLOW_SYSTEM),
      appLanguage = enumOrDefault(preferences[Keys.APP_LANGUAGE], AppLanguage.FOLLOW_SYSTEM),
      aeroMode = enumOrDefault(preferences[Keys.AERO_MODE], AeroMode.GLOW_AURA),
      fadeDurationMillis =
        preferences[Keys.FADE_DURATION_MILLIS].takeIf(::isValidFadeDuration)
          ?: AppSettings.DEFAULT_FADE_DURATION_MILLIS,
      scanMode = enumOrDefault(preferences[Keys.SCAN_MODE], ScanMode.ALL),
      libraryNeedsSync = preferences[Keys.LIBRARY_NEEDS_SYNC] ?: false,
    )

  private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
    raw?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: default

  private fun isValidFadeDuration(value: Int?): Boolean =
    value != null &&
      value in AppSettings.MIN_FADE_DURATION_MILLIS..AppSettings.MAX_FADE_DURATION_MILLIS &&
      value % AppSettings.FADE_DURATION_STEP_MILLIS == 0

  private object Keys {
    val COLOR_SOURCE = stringPreferencesKey("color_source")
    val PRESET_THEME = stringPreferencesKey("preset_theme")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val AERO_MODE = stringPreferencesKey("aero_mode")
    val FADE_DURATION_MILLIS = intPreferencesKey("fade_duration_millis")
    val SCAN_MODE = stringPreferencesKey("scan_mode")
    val LIBRARY_NEEDS_SYNC = booleanPreferencesKey("library_needs_sync")
  }

  private suspend fun settingsWrite(block: suspend () -> Unit): RepositoryResult<Unit> =
    try {
      block()
      RepositoryResult.Success(Unit)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: Exception) {
      RepositoryResult.Failure(RepositoryError.PersistenceUnavailable)
    }
}

typealias SettingsStore = DataStoreSettingsRepository
