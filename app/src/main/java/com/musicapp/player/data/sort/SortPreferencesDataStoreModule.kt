package com.musicapp.player.data.sort

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
annotation class SortDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SortPreferencesDataStoreModule {
    @Binds
    @Singleton
    abstract fun bindSortPreferencesRepository(
        implementation: DataStoreSortPreferencesRepository,
    ): SortPreferencesRepository

    companion object {
        private const val SORT_PREFERENCES_FILE_NAME = "sort_preferences.preferences_pb"

        @Provides
        @Singleton
        @SortDataStore
        fun provideSortPreferencesDataStore(
            @ApplicationContext context: Context,
            @ApplicationCoroutineScope applicationScope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = { context.preferencesDataStoreFile(SORT_PREFERENCES_FILE_NAME) },
        )
    }
}
