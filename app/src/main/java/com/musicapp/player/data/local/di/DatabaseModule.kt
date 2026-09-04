package com.musicapp.player.data.local.di

import android.content.Context
import androidx.room.Room
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.local.MIGRATION_1_2
import com.musicapp.player.data.local.MIGRATION_2_3
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaybackSnapshotRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.RoomHistoryRepository
import com.musicapp.player.data.repository.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.RoomPlaybackSnapshotRepository
import com.musicapp.player.data.repository.RoomPlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "music.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMediaLibraryRepository(implementation: RoomMediaLibraryRepository): MediaLibraryRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(implementation: RoomPlaylistRepository): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(implementation: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackSnapshotRepository(
        implementation: RoomPlaybackSnapshotRepository,
    ): PlaybackSnapshotRepository
}
