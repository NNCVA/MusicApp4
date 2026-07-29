package com.musicapp.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
  private lateinit var scope: CoroutineScope
  private lateinit var file: File
  private lateinit var dataStore: DataStore<Preferences>

  @Before
  fun setUp() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
  }

  @After
  fun tearDown() {
    scope.cancel()
    file.delete()
  }

  @Test
  fun defaults_followPlatformDynamicColorCapability() = runBlocking {
    assertEquals(ColorSource.SYSTEM_DYNAMIC, SettingsStore(dataStore, true, scope).settings.first().colorSource)
    assertEquals(AppSettings.DEFAULT_FADE_DURATION_MILLIS, SettingsStore(dataStore, true, scope).settings.first().fadeDurationMillis)
    assertEquals(ScanMode.ALL, SettingsStore(dataStore, true, scope).settings.first().scanMode)

    assertEquals(ColorSource.PRESET, SettingsStore(dataStore, false, scope).settings.first().colorSource)
  }

  @Test
  fun updates_areAtomicAndResetRestoresOnlySettingsDefaults() = runBlocking {
    val store = SettingsStore(dataStore, true, scope)
    store.setPresetTheme(PresetTheme.VIOLET)
    store.setFadeDurationMillis(2_000)
    store.setScanMode(ScanMode.SELECTED_DIRECTORIES)
    store.setLibraryNeedsSync(true)

    store.settings.first { it.presetTheme == PresetTheme.VIOLET && it.fadeDurationMillis == 2_000 }
    store.reset()
    assertEquals(AppSettings(), store.settings.first { it == AppSettings() })
  }

  @Test
  fun invalidPersistedValues_fallBackToDefaults() = runBlocking {
    dataStore.edit {
      it[stringPreferencesKey("aero_mode")] = "NOT_A_MODE"
      it[intPreferencesKey("fade_duration_millis")] = 251
    }

    val settings = SettingsStore(dataStore, true, scope).settings.first()
    assertEquals(AeroMode.GLOW_AURA, settings.aeroMode)
    assertEquals(500, settings.fadeDurationMillis)
  }

  @Test
  fun concurrentUpdates_doNotOverwriteUnrelatedKeys() = runBlocking {
    val store = SettingsStore(dataStore, true, scope)
    coroutineScope {
      launch { store.setPresetTheme(PresetTheme.EMERALD_GREEN) }
      launch { store.setDarkMode(DarkMode.DARK) }
      launch { store.setAppLanguage(AppLanguage.ENGLISH) }
    }

    val settings = store.settings.first {
      it.presetTheme == PresetTheme.EMERALD_GREEN &&
        it.darkMode == DarkMode.DARK &&
        it.appLanguage == AppLanguage.ENGLISH
    }
    assertEquals(PresetTheme.EMERALD_GREEN, settings.presetTheme)
    assertEquals(DarkMode.DARK, settings.darkMode)
    assertEquals(AppLanguage.ENGLISH, settings.appLanguage)
  }

  @Test
  fun invalidFadeUpdate_isRejected() = runBlocking {
    assertTrue(SettingsStore(dataStore, true, scope).setFadeDurationMillis(251) is com.musicapp.player.data.repository.api.RepositoryResult.Failure)
  }
}
