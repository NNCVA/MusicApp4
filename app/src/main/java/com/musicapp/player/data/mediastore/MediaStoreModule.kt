package com.musicapp.player.data.mediastore

import android.content.ContentResolver
import android.content.Context
import com.musicapp.player.data.sync.MediaLibraryScanSource
import com.musicapp.player.data.sync.MediaLibrarySynchronizer
import com.musicapp.player.data.sync.MediaLibrarySyncCoordinator
import com.musicapp.player.data.sync.MediaStoreChangeSource
import com.musicapp.player.data.sync.MediaStoreSnapshotSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaStoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindScanSource(implementation: AndroidMediaLibraryScanSource): MediaLibraryScanSource

    @Binds
    @Singleton
    abstract fun bindSnapshotSource(implementation: AndroidMediaStoreSnapshotSource): MediaStoreSnapshotSource

    @Binds
    @Singleton
    abstract fun bindChangeSource(implementation: AndroidMediaStoreChangeSource): MediaStoreChangeSource

    @Binds
    @Singleton
    abstract fun bindObserverRegistry(
        implementation: ContentResolverMediaStoreObserverRegistry,
    ): MediaStoreObserverRegistry

    @Binds
    @Singleton
    abstract fun bindSynchronizer(implementation: MediaLibrarySyncCoordinator): MediaLibrarySynchronizer
}

@Module
@InstallIn(SingletonComponent::class)
object MediaStorePlatformModule {
    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideQueryAdapter(@ApplicationContext context: Context): MediaStoreQueryAdapter =
        AndroidMediaStoreQueryAdapter(context)

}
