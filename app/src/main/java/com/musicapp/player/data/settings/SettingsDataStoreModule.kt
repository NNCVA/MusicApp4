package com.musicapp.player.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataStoreModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: PreferencesSettingsRepository,
    ): SettingsRepository

    companion object {
        private const val SETTINGS_FILE_NAME = "settings.preferences_pb"

        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
            @ApplicationCoroutineScope applicationScope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE_NAME) },
        )
    }
}
