package com.musicapp.player.fakes

import com.musicapp.player.data.sort.SortPreferencesRepository
import com.musicapp.player.feature.albums.AlbumSort
import com.musicapp.player.feature.artists.ArtistSort
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.folders.FolderSort
import com.musicapp.player.feature.playlists.PlaylistTrackSort
import com.musicapp.player.feature.tracks.TrackSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSortPreferencesRepository(
    initialTrackSort: TrackSort = TrackSort(),
    initialAlbumSort: AlbumSort = AlbumSort(),
    initialArtistSort: ArtistSort = ArtistSort(),
    initialFolderSort: FolderSort = FolderSort(),
    initialPlaylistTrackSort: PlaylistTrackSort = PlaylistTrackSort.DEFAULT,
    initialFolderTrackSort: CategoryTrackSort = CategoryTrackSort(field = CategoryTrackSortField.TITLE, direction = CategorySortDirection.ASCENDING),
    initialArtistTrackSort: CategoryTrackSort = CategoryTrackSort(field = CategoryTrackSortField.ALBUM, direction = CategorySortDirection.ASCENDING),
) : SortPreferencesRepository {
    private val _trackSort = MutableStateFlow(initialTrackSort)
    override val trackSort: StateFlow<TrackSort> = _trackSort.asStateFlow()

    private val _albumSort = MutableStateFlow(initialAlbumSort)
    override val albumSort: StateFlow<AlbumSort> = _albumSort.asStateFlow()

    private val _artistSort = MutableStateFlow(initialArtistSort)
    override val artistSort: StateFlow<ArtistSort> = _artistSort.asStateFlow()

    private val _folderSort = MutableStateFlow(initialFolderSort)
    override val folderSort: StateFlow<FolderSort> = _folderSort.asStateFlow()

    private val _playlistTrackSort = MutableStateFlow(initialPlaylistTrackSort)
    override val playlistTrackSort: StateFlow<PlaylistTrackSort> = _playlistTrackSort.asStateFlow()

    private val _folderTrackSort = MutableStateFlow(initialFolderTrackSort)
    override val folderTrackSort: StateFlow<CategoryTrackSort> = _folderTrackSort.asStateFlow()

    private val _artistTrackSort = MutableStateFlow(initialArtistTrackSort)
    override val artistTrackSort: StateFlow<CategoryTrackSort> = _artistTrackSort.asStateFlow()

    override suspend fun setTrackSort(sort: TrackSort) {
        _trackSort.value = sort
    }

    override suspend fun setAlbumSort(sort: AlbumSort) {
        _albumSort.value = sort
    }

    override suspend fun setArtistSort(sort: ArtistSort) {
        _artistSort.value = sort
    }

    override suspend fun setFolderSort(sort: FolderSort) {
        _folderSort.value = sort
    }

    override suspend fun setPlaylistTrackSort(sort: PlaylistTrackSort) {
        _playlistTrackSort.value = sort
    }

    override suspend fun setFolderTrackSort(sort: CategoryTrackSort) {
        _folderTrackSort.value = sort
    }

    override suspend fun setArtistTrackSort(sort: CategoryTrackSort) {
        _artistTrackSort.value = sort
    }

    override suspend fun reset() {
        _trackSort.value = TrackSort()
        _albumSort.value = AlbumSort()
        _artistSort.value = ArtistSort()
        _folderSort.value = FolderSort()
        _playlistTrackSort.value = PlaylistTrackSort.DEFAULT
        _folderTrackSort.value = CategoryTrackSort(field = CategoryTrackSortField.TITLE, direction = CategorySortDirection.ASCENDING)
        _artistTrackSort.value = CategoryTrackSort(field = CategoryTrackSortField.ALBUM, direction = CategorySortDirection.ASCENDING)
    }
}
