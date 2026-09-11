package com.musicapp.player.data.equalizer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EqualizerDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class EqualizerDataStoreModule {

    @Binds
    @Singleton
    abstract fun bindEqualizerRepository(
        implementation: PreferencesEqualizerRepository,
    ): EqualizerRepository

    companion object {
        private const val EQUALIZER_FILE_NAME = "equalizer.preferences_pb"

        @Provides
        @Singleton
        @EqualizerDataStore
        fun provideEqualizerDataStore(
            @ApplicationContext context: Context,
            @ApplicationCoroutineScope applicationScope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = { context.preferencesDataStoreFile(EQUALIZER_FILE_NAME) },
        )
    }
}
