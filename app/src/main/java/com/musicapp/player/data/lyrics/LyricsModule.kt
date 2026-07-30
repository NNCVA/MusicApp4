package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LyricsModule {
    @Binds
    @Singleton
    abstract fun bindLyricsRepository(implementation: AndroidLyricsRepository): LyricsRepository
}
