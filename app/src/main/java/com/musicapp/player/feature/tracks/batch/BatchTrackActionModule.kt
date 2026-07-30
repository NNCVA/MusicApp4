package com.musicapp.player.feature.tracks.batch

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BatchTrackActionModule {
    @Binds
    @Singleton
    abstract fun bindBatchTrackActionExecutor(
        implementation: DefaultBatchTrackActionExecutor,
    ): BatchTrackActionExecutor
}
