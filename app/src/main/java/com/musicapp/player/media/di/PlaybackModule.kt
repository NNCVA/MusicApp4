package com.musicapp.player.media.di

import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.media.playback.DefaultPlaybackControllerFacade
import com.musicapp.player.media.playback.Media3PlaybackControllerConnection
import com.musicapp.player.media.playback.PlaybackControllerConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackControllerConnection(
        implementation: Media3PlaybackControllerConnection,
    ): PlaybackControllerConnection

    @Binds
    @Singleton
    abstract fun bindPlaybackControllerFacade(
        implementation: DefaultPlaybackControllerFacade,
    ): PlaybackControllerFacade
}
