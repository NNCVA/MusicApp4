package com.musicapp.player.di

import com.musicapp.player.core.system.AppClock
import com.musicapp.player.core.system.DefaultAppClock
import com.musicapp.player.core.system.DefaultRandomSource
import com.musicapp.player.core.system.RandomSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
  @Binds
  @Singleton
  abstract fun bindAppClock(implementation: DefaultAppClock): AppClock

  @Binds
  @Singleton
  abstract fun bindRandomSource(implementation: DefaultRandomSource): RandomSource
}
