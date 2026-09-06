package com.musicapp.player.data.sort

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SortPreferencesRepositoryTest {

    @Test
    fun `initial values return defaults for all sorts`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        assertEquals(TrackSort(), repository.trackSort.value)
        assertEquals(AlbumSort(), repository.albumSort.value)
        assertEquals(ArtistSort(), repository.artistSort.value)
        assertEquals(FolderSort(), repository.folderSort.value)
        assertEquals(PlaylistTrackSort.DEFAULT, repository.playlistTrackSort.value)
        assertEquals(CategoryTrackSort(field = CategoryTrackSortField.TITLE, direction = CategorySortDirection.ASCENDING), repository.folderTrackSort.value)
        assertEquals(CategoryTrackSort(field = CategoryTrackSortField.ALBUM, direction = CategorySortDirection.ASCENDING), repository.artistTrackSort.value)
    }

    @Test
    fun `trackSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = TrackSort(field = TrackSortField.DURATION, direction = TrackSortDirection.DESCENDING)
        repository.setTrackSort(newSort)
        assertEquals(newSort, repository.trackSort.first { it == newSort })
    }

    @Test
    fun `albumSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = AlbumSort(field = AlbumSortField.RELEASE_YEAR, direction = CategorySortDirection.DESCENDING)
        repository.setAlbumSort(newSort)
        assertEquals(newSort, repository.albumSort.first { it == newSort })
    }

    @Test
    fun `artistSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = ArtistSort(field = ArtistSortField.TRACK_COUNT, direction = CategorySortDirection.DESCENDING)
        repository.setArtistSort(newSort)
        assertEquals(newSort, repository.artistSort.first { it == newSort })
    }

    @Test
    fun `folderSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = FolderSort(field = FolderSortField.TRACK_COUNT, direction = CategorySortDirection.DESCENDING)
        repository.setFolderSort(newSort)
        assertEquals(newSort, repository.folderSort.first { it == newSort })
    }

    @Test
    fun `playlistTrackSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = PlaylistTrackSort(field = PlaylistTrackSortField.ARTIST, direction = PlaylistTrackSortDirection.DESCENDING)
        repository.setPlaylistTrackSort(newSort)
        assertEquals(newSort, repository.playlistTrackSort.first { it == newSort })
    }

    @Test
    fun `folderTrackSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = CategoryTrackSort(field = CategoryTrackSortField.DATE_ADDED, direction = CategorySortDirection.DESCENDING)
        repository.setFolderTrackSort(newSort)
        assertEquals(newSort, repository.folderTrackSort.first { it == newSort })
    }

    @Test
    fun `artistTrackSort updates and round trips`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        val newSort = CategoryTrackSort(field = CategoryTrackSortField.DURATION, direction = CategorySortDirection.DESCENDING)
        repository.setArtistTrackSort(newSort)
        assertEquals(newSort, repository.artistTrackSort.first { it == newSort })
    }

    @Test
    fun `reset restores all defaults`() = runTest {
        val repository = createRepository()
        advanceUntilIdle()

        repository.setTrackSort(TrackSort(TrackSortField.DURATION, TrackSortDirection.DESCENDING))
        repository.setAlbumSort(AlbumSort(AlbumSortField.RELEASE_YEAR, CategorySortDirection.DESCENDING))
        repository.setArtistSort(ArtistSort(ArtistSortField.TRACK_COUNT, CategorySortDirection.DESCENDING))
        repository.setFolderSort(FolderSort(FolderSortField.TRACK_COUNT, CategorySortDirection.DESCENDING))
        repository.setPlaylistTrackSort(PlaylistTrackSort(PlaylistTrackSortField.ARTIST, PlaylistTrackSortDirection.DESCENDING))
        repository.setFolderTrackSort(CategoryTrackSort(CategoryTrackSortField.DATE_ADDED, CategorySortDirection.DESCENDING))
        repository.setArtistTrackSort(CategoryTrackSort(CategoryTrackSortField.DURATION, CategorySortDirection.DESCENDING))

        repository.reset()

        assertEquals(TrackSort(), repository.trackSort.first { it == TrackSort() })
        assertEquals(AlbumSort(), repository.albumSort.first { it == AlbumSort() })
        assertEquals(ArtistSort(), repository.artistSort.first { it == ArtistSort() })
        assertEquals(FolderSort(), repository.folderSort.first { it == FolderSort() })
        assertEquals(PlaylistTrackSort.DEFAULT, repository.playlistTrackSort.first { it == PlaylistTrackSort.DEFAULT })
        assertEquals(CategoryTrackSort(CategoryTrackSortField.TITLE, CategorySortDirection.ASCENDING), repository.folderTrackSort.first { it == CategoryTrackSort(CategoryTrackSortField.TITLE, CategorySortDirection.ASCENDING) })
        assertEquals(CategoryTrackSort(CategoryTrackSortField.ALBUM, CategorySortDirection.ASCENDING), repository.artistTrackSort.first { it == CategoryTrackSort(CategoryTrackSortField.ALBUM, CategorySortDirection.ASCENDING) })
    }

    @Test
    fun `corrupted or unknown enum values fall back gracefully to defaults`() = runTest {
        val invalidPreferences = mutablePreferencesOf(
            stringPreferencesKey("track_sort_field") to "NON_EXISTENT_FIELD",
            stringPreferencesKey("track_sort_direction") to "UNKNOWN_DIRECTION",
            stringPreferencesKey("album_sort_field") to "INVALID",
            stringPreferencesKey("artist_sort_field") to "INVALID",
            stringPreferencesKey("folder_sort_field") to "INVALID",
            stringPreferencesKey("playlist_track_sort_field") to "INVALID",
        )
        val repository = DataStoreSortPreferencesRepository(
            dataStore = FixedDataStore(invalidPreferences),
            applicationScope = backgroundScope,
        )
        advanceUntilIdle()

        assertEquals(TrackSort(), repository.trackSort.first { it == TrackSort() })
        assertEquals(AlbumSort(), repository.albumSort.first { it == AlbumSort() })
        assertEquals(ArtistSort(), repository.artistSort.first { it == ArtistSort() })
        assertEquals(FolderSort(), repository.folderSort.first { it == FolderSort() })
        assertEquals(PlaylistTrackSort.DEFAULT, repository.playlistTrackSort.first { it == PlaylistTrackSort.DEFAULT })
    }

    @Test
    fun `IOException on data flow recovers with defaults`() = runTest {
        val repository = DataStoreSortPreferencesRepository(
            dataStore = ThrowingDataStore(IOException("Disk error")),
            applicationScope = backgroundScope,
        )
        advanceUntilIdle()

        assertEquals(TrackSort(), repository.trackSort.first { it == TrackSort() })
    }

    @Test
    fun `write completes successfully even when caller job is cancelled`() = runTest {
        val slowDataStore = object : DataStore<Preferences> {
            val preferences = MutableStateFlow<Preferences>(emptyPreferences())
            val insideEdit = kotlinx.coroutines.CompletableDeferred<Unit>()
            override val data: Flow<Preferences> = preferences
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                insideEdit.complete(Unit)
                kotlinx.coroutines.delay(50)
                val updated = transform(preferences.value)
                preferences.value = updated
                return updated
            }
        }
        val repository = DataStoreSortPreferencesRepository(
            dataStore = slowDataStore,
            applicationScope = backgroundScope,
        )
        advanceUntilIdle()

        val newSort = TrackSort(field = TrackSortField.DURATION, direction = TrackSortDirection.DESCENDING)
        val job = launch {
            repository.setTrackSort(newSort)
        }
        slowDataStore.insideEdit.await()
        job.cancel()
        advanceUntilIdle()

        assertEquals(newSort, repository.trackSort.first { it == newSort })
    }

    private fun TestScope.createRepository(): SortPreferencesRepository {
        val dataStore = InMemoryDataStore()
        return DataStoreSortPreferencesRepository(
            dataStore = dataStore,
            applicationScope = backgroundScope,
        )
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val preferences = MutableStateFlow<Preferences>(emptyPreferences())
        private val updateMutex = Mutex()

        override val data: Flow<Preferences> = preferences

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            updateMutex.withLock {
                transform(preferences.value).also { updatedPreferences ->
                    preferences.value = updatedPreferences
                }
            }
    }

    private class ThrowingDataStore(
        private val exception: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw exception }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("Read-only test DataStore")
    }

    private class FixedDataStore(
        private val preferences: Preferences,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { emit(preferences) }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(preferences)
    }
}
