package com.musicapp.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @Test
    fun firstReadReturnsAppSettingsDefaults() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun currentSettingsReadsPersistedValueWhileObservedStateIsStillInitialDefault() = runTest {
        val persisted = mutablePreferencesOf(
            stringPreferencesKey("scan_mode") to ScanMode.SELECTED_DIRECTORIES.name,
        )
        val unstartedObserverScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        val repository = PreferencesSettingsRepository(
            dataStore = FixedDataStore(persisted),
            applicationScope = unstartedObserverScope,
        )

        assertEquals(ScanMode.ALL, repository.settings.value.scanMode)
        assertEquals(ScanMode.SELECTED_DIRECTORIES, repository.currentSettings().scanMode)
        assertEquals(ScanMode.ALL, repository.settings.value.scanMode)
        unstartedObserverScope.cancel()
    }

    @Test
    fun ioExceptionWhileReadingReturnsDefaults() = runTest {
        val repository = PreferencesSettingsRepository(
            dataStore = ThrowingDataStore(IOException("Cannot read settings")),
            applicationScope = backgroundScope,
        )

        advanceUntilIdle()

        assertEquals(AppSettings(), repository.settings.value)
    }

    @Test
    fun nonIoExceptionWhileReadingIsRethrown() = runTest {
        val expected = IllegalStateException("Unexpected settings failure")
        val observed = CompletableDeferred<Throwable>()
        val exceptionHandler = CoroutineExceptionHandler { _, exception -> observed.complete(exception) }
        val repositoryScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + exceptionHandler,
        )
        PreferencesSettingsRepository(
            dataStore = ThrowingDataStore(expected),
            applicationScope = repositoryScope,
        )

        advanceUntilIdle()

        assertSame(expected, observed.await())
        repositoryScope.cancel()
    }

    @Test
    fun everyEnumValueAndScanModeRoundTripsImmediately() = runTest {
        val repository = createRepository()

        ColorSource.entries.forEach { value ->
            repository.setColorSource(value)
            assertEquals(value, repository.settings.first { it.colorSource == value }.colorSource)
        }
        PresetTheme.entries.forEach { value ->
            repository.setPresetTheme(value)
            assertEquals(value, repository.settings.first { it.presetTheme == value }.presetTheme)
        }
        ThemeMode.entries.forEach { value ->
            repository.setThemeMode(value)
            assertEquals(value, repository.settings.first { it.themeMode == value }.themeMode)
        }
        AppLanguage.entries.forEach { value ->
            repository.setAppLanguage(value)
            assertEquals(value, repository.settings.first { it.appLanguage == value }.appLanguage)
        }
        AeroMode.entries.forEach { value ->
            repository.setAeroMode(value)
            assertEquals(value, repository.settings.first { it.aeroMode == value }.aeroMode)
        }
        ScanMode.entries.forEach { value ->
            repository.setScanMode(value)
            assertEquals(value, repository.settings.first { it.scanMode == value }.scanMode)
        }
    }

    @Test
    fun fadeThroughAcceptsBothBounds() = runTest {
        val repository = createRepository()

        repository.setFadeThroughDurationMs(0)
        assertEquals(0, repository.settings.first { it.fadeThroughDurationMs == 0L }.fadeThroughDurationMs)

        repository.setFadeThroughDurationMs(2_000)
        assertEquals(
            2_000,
            repository.settings.first { it.fadeThroughDurationMs == 2_000L }.fadeThroughDurationMs,
        )
    }

    @Test
    fun fadeThroughRejectsNonStepAndOutOfRangeWithoutChangingStoredValue() = runTest {
        val repository = createRepository()
        repository.setFadeThroughDurationMs(750)

        listOf(-250L, 1L, 251L, 2_250L).forEach { invalidValue ->
            val result = runCatching { repository.setFadeThroughDurationMs(invalidValue) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(750, repository.settings.first { it.fadeThroughDurationMs == 750L }.fadeThroughDurationMs)
        }
    }

    @Test
    fun resetRestoresAllDefaults() = runTest {
        val repository = createRepository()
        repository.setColorSource(ColorSource.PRESET)
        repository.setPresetTheme(PresetTheme.VIOLET)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAppLanguage(AppLanguage.ENGLISH)
        repository.setAeroMode(AeroMode.SOLID)
        repository.setFadeThroughDurationMs(2_000)
        repository.setScanMode(ScanMode.SELECTED_DIRECTORIES)
        val firstPendingRevision = repository.markLibrarySyncPending()
        val pendingRevision = repository.markLibrarySyncPending()
        assertEquals(firstPendingRevision + 1, pendingRevision)

        repository.reset()

        assertEquals(AppSettings(), repository.settings.first { it == AppSettings() })
        assertEquals(
            PendingLibrarySyncState(pendingRevision, isPending = true),
            repository.pendingLibrarySync.first { it.isPending },
        )
        assertFalse(repository.clearLibrarySyncPending(firstPendingRevision))
        assertTrue(repository.clearLibrarySyncPending(pendingRevision))
        assertEquals(
            PendingLibrarySyncState(pendingRevision, isPending = false),
            repository.pendingLibrarySync.first { !it.isPending && it.revision == pendingRevision },
        )
    }

    private fun TestScope.createRepository(): SettingsRepository {
        val dataStore = InMemoryDataStore()
        return PreferencesSettingsRepository(
            dataStore = dataStore,
            applicationScope = backgroundScope,
        )
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val preferences = MutableStateFlow<Preferences>(emptyPreferences())
        private val updateMutex = Mutex()

        override val data: Flow<Preferences> = preferences

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            updateMutex.withLock {
                transform(preferences.value).also { updatedPreferences ->
                    preferences.value = updatedPreferences
                }
            }
    }

    private class ThrowingDataStore(
        private val exception: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw exception }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("Read-only test DataStore")
    }

    private class FixedDataStore(
        private val preferences: Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { emit(preferences) }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(preferences)
    }
}
