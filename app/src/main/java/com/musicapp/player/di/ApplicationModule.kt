package com.musicapp.player.di

import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.core.common.random.DefaultRandomSource
import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.common.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideRandomSource(): RandomSource = DefaultRandomSource()

    @Provides
    @Singleton
    @ApplicationCoroutineScope
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("MusicApp"))
}
