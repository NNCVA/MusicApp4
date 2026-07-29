package com.musicapp.player.di

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.musicapp.player.core.system.AppClock
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.local.dao.HiddenTrackDao
import com.musicapp.player.data.local.dao.PathRuleDao
import com.musicapp.player.data.local.dao.PlayHistoryDao
import com.musicapp.player.data.local.dao.PlaybackSnapshotDao
import com.musicapp.player.data.local.dao.PlaylistDao
import com.musicapp.player.data.local.dao.PlaylistTrackDao
import com.musicapp.player.data.local.dao.TrackDao
import com.musicapp.player.data.repository.api.HistoryRepository
import com.musicapp.player.data.repository.api.MediaLibraryRepository
import com.musicapp.player.data.repository.api.PathRuleRepository
import com.musicapp.player.data.repository.api.PlaybackSnapshotRepository
import com.musicapp.player.data.repository.api.PlaylistRepository
import com.musicapp.player.data.repository.room.RoomHistoryRepository
import com.musicapp.player.data.repository.room.RoomMediaLibraryRepository
import com.musicapp.player.data.repository.room.RoomPathRuleRepository
import com.musicapp.player.data.repository.room.RoomPlaybackSnapshotRepository
import com.musicapp.player.data.repository.room.RoomPlaylistRepository
import com.musicapp.player.data.settings.DataStoreSettingsRepository
import com.musicapp.player.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
  @Provides
  @Singleton
  fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
    Room.databaseBuilder(context, MusicDatabase::class.java, MusicDatabase.DATABASE_NAME).build()

  @Provides fun provideTrackDao(database: MusicDatabase): TrackDao = database.trackDao()
  @Provides fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()
  @Provides fun providePlaylistTrackDao(database: MusicDatabase): PlaylistTrackDao = database.playlistTrackDao()
  @Provides fun providePlayHistoryDao(database: MusicDatabase): PlayHistoryDao = database.playHistoryDao()
  @Provides fun provideHiddenTrackDao(database: MusicDatabase): HiddenTrackDao = database.hiddenTrackDao()
  @Provides fun providePathRuleDao(database: MusicDatabase): PathRuleDao = database.pathRuleDao()
  @Provides fun providePlaybackSnapshotDao(database: MusicDatabase): PlaybackSnapshotDao = database.playbackSnapshotDao()

  @Provides
  @Singleton
  fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE_NAME) }

  @Provides
  @Singleton
  fun provideSettingsRepository(
    dataStore: DataStore<Preferences>,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
  ): SettingsRepository =
    DataStoreSettingsRepository(
      dataStore = dataStore,
      dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
      scope = scope,
    )

  @Provides
  @Singleton
  @Named(DATA_REPOSITORY_SCOPE)
  fun provideDataRepositoryScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Provides
  @Singleton
  fun provideMediaLibraryRepository(
    trackDao: TrackDao,
    hiddenTrackDao: HiddenTrackDao,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
    clock: AppClock,
  ): MediaLibraryRepository = RoomMediaLibraryRepository(trackDao, hiddenTrackDao, scope, clock)

  @Provides
  @Singleton
  fun providePlaylistRepository(
    playlistDao: PlaylistDao,
    playlistTrackDao: PlaylistTrackDao,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
    clock: AppClock,
  ): PlaylistRepository = RoomPlaylistRepository(playlistDao, playlistTrackDao, scope, clock)

  @Provides
  @Singleton
  fun provideHistoryRepository(
    playHistoryDao: PlayHistoryDao,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
  ): HistoryRepository = RoomHistoryRepository(playHistoryDao, scope)

  @Provides
  @Singleton
  fun providePlaybackSnapshotRepository(
    playbackSnapshotDao: PlaybackSnapshotDao,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
  ): PlaybackSnapshotRepository = RoomPlaybackSnapshotRepository(playbackSnapshotDao, scope)

  @Provides
  @Singleton
  fun providePathRuleRepository(
    pathRuleDao: PathRuleDao,
    @Named(DATA_REPOSITORY_SCOPE) scope: CoroutineScope,
    clock: AppClock,
  ): PathRuleRepository = RoomPathRuleRepository(pathRuleDao, scope, clock)

  private const val SETTINGS_FILE_NAME = "settings.preferences_pb"
  private const val DATA_REPOSITORY_SCOPE = "dataRepositoryScope"
}
