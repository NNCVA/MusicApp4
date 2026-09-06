package com.musicapp.player.data.sort

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.musicapp.player.feature.albums.AlbumSort
import com.musicapp.player.feature.albums.AlbumSortField
import com.musicapp.player.feature.artists.ArtistSort
import com.musicapp.player.feature.artists.ArtistSortField
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.folders.FolderSort
import com.musicapp.player.feature.folders.FolderSortField
import com.musicapp.player.feature.playlists.PlaylistTrackSort
import com.musicapp.player.feature.playlists.PlaylistTrackSortDirection
import com.musicapp.player.feature.playlists.PlaylistTrackSortField
import com.musicapp.player.feature.tracks.TrackSort
import com.musicapp.player.feature.tracks.TrackSortDirection
import com.musicapp.player.feature.tracks.TrackSortField
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class DataStoreSortPreferencesRepository @Inject constructor(
    @param:SortDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationCoroutineScope applicationScope: CoroutineScope,
) : SortPreferencesRepository {

    private val preferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    override val trackSort: StateFlow<TrackSort> = preferencesFlow
        .map(::toTrackSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = TrackSort(),
        )

    override val albumSort: StateFlow<AlbumSort> = preferencesFlow
        .map(::toAlbumSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AlbumSort(),
        )

    override val artistSort: StateFlow<ArtistSort> = preferencesFlow
        .map(::toArtistSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = ArtistSort(),
        )

    override val folderSort: StateFlow<FolderSort> = preferencesFlow
        .map(::toFolderSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = FolderSort(),
        )

    override val playlistTrackSort: StateFlow<PlaylistTrackSort> = preferencesFlow
        .map(::toPlaylistTrackSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = PlaylistTrackSort.DEFAULT,
        )

    override val folderTrackSort: StateFlow<CategoryTrackSort> = preferencesFlow
        .map(::toFolderTrackSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = CategoryTrackSort(field = CategoryTrackSortField.TITLE, direction = CategorySortDirection.ASCENDING),
        )

    override val artistTrackSort: StateFlow<CategoryTrackSort> = preferencesFlow
        .map(::toArtistTrackSort)
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = CategoryTrackSort(field = CategoryTrackSortField.ALBUM, direction = CategorySortDirection.ASCENDING),
        )

    override suspend fun setTrackSort(sort: TrackSort) {
        editPreferences { preferences ->
            preferences[Keys.TRACK_SORT_FIELD] = sort.field.name
            preferences[Keys.TRACK_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setAlbumSort(sort: AlbumSort) {
        editPreferences { preferences ->
            preferences[Keys.ALBUM_SORT_FIELD] = sort.field.name
            preferences[Keys.ALBUM_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setArtistSort(sort: ArtistSort) {
        editPreferences { preferences ->
            preferences[Keys.ARTIST_SORT_FIELD] = sort.field.name
            preferences[Keys.ARTIST_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setFolderSort(sort: FolderSort) {
        editPreferences { preferences ->
            preferences[Keys.FOLDER_SORT_FIELD] = sort.field.name
            preferences[Keys.FOLDER_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setPlaylistTrackSort(sort: PlaylistTrackSort) {
        editPreferences { preferences ->
            preferences[Keys.PLAYLIST_TRACK_SORT_FIELD] = sort.field.name
            preferences[Keys.PLAYLIST_TRACK_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setFolderTrackSort(sort: CategoryTrackSort) {
        editPreferences { preferences ->
            preferences[Keys.FOLDER_TRACK_SORT_FIELD] = sort.field.name
            preferences[Keys.FOLDER_TRACK_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun setArtistTrackSort(sort: CategoryTrackSort) {
        editPreferences { preferences ->
            preferences[Keys.ARTIST_TRACK_SORT_FIELD] = sort.field.name
            preferences[Keys.ARTIST_TRACK_SORT_DIRECTION] = sort.direction.name
        }
    }

    override suspend fun reset() {
        editPreferences { preferences ->
            preferences.remove(Keys.TRACK_SORT_FIELD)
            preferences.remove(Keys.TRACK_SORT_DIRECTION)
            preferences.remove(Keys.ALBUM_SORT_FIELD)
            preferences.remove(Keys.ALBUM_SORT_DIRECTION)
            preferences.remove(Keys.ARTIST_SORT_FIELD)
            preferences.remove(Keys.ARTIST_SORT_DIRECTION)
            preferences.remove(Keys.FOLDER_SORT_FIELD)
            preferences.remove(Keys.FOLDER_SORT_DIRECTION)
            preferences.remove(Keys.PLAYLIST_TRACK_SORT_FIELD)
            preferences.remove(Keys.PLAYLIST_TRACK_SORT_DIRECTION)
            preferences.remove(Keys.FOLDER_TRACK_SORT_FIELD)
            preferences.remove(Keys.FOLDER_TRACK_SORT_DIRECTION)
            preferences.remove(Keys.ARTIST_TRACK_SORT_FIELD)
            preferences.remove(Keys.ARTIST_TRACK_SORT_DIRECTION)
        }
    }

    private suspend fun editPreferences(action: (MutablePreferences) -> Unit) {
        withContext(NonCancellable) {
            dataStore.edit { preferences ->
                action(preferences)
            }
        }
    }

    private fun toTrackSort(preferences: Preferences): TrackSort {
        val default = TrackSort()
        return TrackSort(
            field = preferences.enumValue(Keys.TRACK_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.TRACK_SORT_DIRECTION, default.direction),
        )
    }

    private fun toAlbumSort(preferences: Preferences): AlbumSort {
        val default = AlbumSort()
        return AlbumSort(
            field = preferences.enumValue(Keys.ALBUM_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.ALBUM_SORT_DIRECTION, default.direction),
        )
    }

    private fun toArtistSort(preferences: Preferences): ArtistSort {
        val default = ArtistSort()
        return ArtistSort(
            field = preferences.enumValue(Keys.ARTIST_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.ARTIST_SORT_DIRECTION, default.direction),
        )
    }

    private fun toFolderSort(preferences: Preferences): FolderSort {
        val default = FolderSort()
        return FolderSort(
            field = preferences.enumValue(Keys.FOLDER_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.FOLDER_SORT_DIRECTION, default.direction),
        )
    }

    private fun toPlaylistTrackSort(preferences: Preferences): PlaylistTrackSort {
        val default = PlaylistTrackSort.DEFAULT
        return PlaylistTrackSort(
            field = preferences.enumValue(Keys.PLAYLIST_TRACK_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.PLAYLIST_TRACK_SORT_DIRECTION, default.direction),
        )
    }

    private fun toFolderTrackSort(preferences: Preferences): CategoryTrackSort {
        val default = CategoryTrackSort(field = CategoryTrackSortField.TITLE, direction = CategorySortDirection.ASCENDING)
        return CategoryTrackSort(
            field = preferences.enumValue(Keys.FOLDER_TRACK_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.FOLDER_TRACK_SORT_DIRECTION, default.direction),
        )
    }

    private fun toArtistTrackSort(preferences: Preferences): CategoryTrackSort {
        val default = CategoryTrackSort(field = CategoryTrackSortField.ALBUM, direction = CategorySortDirection.ASCENDING)
        return CategoryTrackSort(
            field = preferences.enumValue(Keys.ARTIST_TRACK_SORT_FIELD, default.field),
            direction = preferences.enumValue(Keys.ARTIST_TRACK_SORT_DIRECTION, default.direction),
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(
        key: Preferences.Key<String>,
        default: T,
    ): T = this[key]?.let { storedValue -> enumValues<T>().firstOrNull { it.name == storedValue } } ?: default

    private object Keys {
        val TRACK_SORT_FIELD = stringPreferencesKey("track_sort_field")
        val TRACK_SORT_DIRECTION = stringPreferencesKey("track_sort_direction")
        val ALBUM_SORT_FIELD = stringPreferencesKey("album_sort_field")
        val ALBUM_SORT_DIRECTION = stringPreferencesKey("album_sort_direction")
        val ARTIST_SORT_FIELD = stringPreferencesKey("artist_sort_field")
        val ARTIST_SORT_DIRECTION = stringPreferencesKey("artist_sort_direction")
        val FOLDER_SORT_FIELD = stringPreferencesKey("folder_sort_field")
        val FOLDER_SORT_DIRECTION = stringPreferencesKey("folder_sort_direction")
        val PLAYLIST_TRACK_SORT_FIELD = stringPreferencesKey("playlist_track_sort_field")
        val PLAYLIST_TRACK_SORT_DIRECTION = stringPreferencesKey("playlist_track_sort_direction")
        val FOLDER_TRACK_SORT_FIELD = stringPreferencesKey("folder_track_sort_field")
        val FOLDER_TRACK_SORT_DIRECTION = stringPreferencesKey("folder_track_sort_direction")
        val ARTIST_TRACK_SORT_FIELD = stringPreferencesKey("artist_track_sort_field")
        val ARTIST_TRACK_SORT_DIRECTION = stringPreferencesKey("artist_track_sort_direction")
    }
}
