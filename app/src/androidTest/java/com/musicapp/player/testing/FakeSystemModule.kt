package com.musicapp.player.testing

import com.musicapp.player.core.system.AppClock
import com.musicapp.player.core.system.RandomSource
import com.musicapp.player.di.SystemModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

private class TestAppClock : AppClock {
  override fun currentTimeMillis(): Long = 42L

  override fun elapsedRealtimeMillis(): Long = 24L
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SystemModule::class])
object FakeSystemModule {
  @Provides
  @Singleton
  fun provideAppClock(): AppClock = TestAppClock()

  @Provides
  @Singleton
  fun provideRandomSource(): RandomSource = RandomSource { 0 }
}
