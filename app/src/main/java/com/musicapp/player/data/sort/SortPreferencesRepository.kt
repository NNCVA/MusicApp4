package com.musicapp.player.data.sort

import com.musicapp.player.feature.albums.AlbumSort
import com.musicapp.player.feature.artists.ArtistSort
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.folders.FolderSort
import com.musicapp.player.feature.playlists.PlaylistTrackSort
import com.musicapp.player.feature.tracks.TrackSort
import kotlinx.coroutines.flow.StateFlow

interface SortPreferencesRepository {
    val trackSort: StateFlow<TrackSort>
    val albumSort: StateFlow<AlbumSort>
    val artistSort: StateFlow<ArtistSort>
    val folderSort: StateFlow<FolderSort>
    val playlistTrackSort: StateFlow<PlaylistTrackSort>
    val folderTrackSort: StateFlow<CategoryTrackSort>
    val artistTrackSort: StateFlow<CategoryTrackSort>

    suspend fun setTrackSort(sort: TrackSort)
    suspend fun setAlbumSort(sort: AlbumSort)
    suspend fun setArtistSort(sort: ArtistSort)
    suspend fun setFolderSort(sort: FolderSort)
    suspend fun setPlaylistTrackSort(sort: PlaylistTrackSort)
    suspend fun setFolderTrackSort(sort: CategoryTrackSort)
    suspend fun setArtistTrackSort(sort: CategoryTrackSort)

    suspend fun reset()
}

class InMemorySortPreferencesRepository(
    initialTrackSort: TrackSort = TrackSort(),
    initialAlbumSort: AlbumSort = AlbumSort(),
    initialArtistSort: ArtistSort = ArtistSort(),
    initialFolderSort: FolderSort = FolderSort(),
    initialPlaylistTrackSort: PlaylistTrackSort = PlaylistTrackSort.DEFAULT,
    initialFolderTrackSort: CategoryTrackSort = CategoryTrackSort(
        field = com.musicapp.player.feature.category.CategoryTrackSortField.TITLE,
        direction = com.musicapp.player.feature.category.CategorySortDirection.ASCENDING,
    ),
    initialArtistTrackSort: CategoryTrackSort = CategoryTrackSort(
        field = com.musicapp.player.feature.category.CategoryTrackSortField.ALBUM,
        direction = com.musicapp.player.feature.category.CategorySortDirection.ASCENDING,
    ),
) : SortPreferencesRepository {
    private val _trackSort = kotlinx.coroutines.flow.MutableStateFlow(initialTrackSort)
    override val trackSort: StateFlow<TrackSort> = _trackSort

    private val _albumSort = kotlinx.coroutines.flow.MutableStateFlow(initialAlbumSort)
    override val albumSort: StateFlow<AlbumSort> = _albumSort

    private val _artistSort = kotlinx.coroutines.flow.MutableStateFlow(initialArtistSort)
    override val artistSort: StateFlow<ArtistSort> = _artistSort

    private val _folderSort = kotlinx.coroutines.flow.MutableStateFlow(initialFolderSort)
    override val folderSort: StateFlow<FolderSort> = _folderSort

    private val _playlistTrackSort = kotlinx.coroutines.flow.MutableStateFlow(initialPlaylistTrackSort)
    override val playlistTrackSort: StateFlow<PlaylistTrackSort> = _playlistTrackSort

    private val _folderTrackSort = kotlinx.coroutines.flow.MutableStateFlow(initialFolderTrackSort)
    override val folderTrackSort: StateFlow<CategoryTrackSort> = _folderTrackSort

    private val _artistTrackSort = kotlinx.coroutines.flow.MutableStateFlow(initialArtistTrackSort)
    override val artistTrackSort: StateFlow<CategoryTrackSort> = _artistTrackSort

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
        _folderTrackSort.value = CategoryTrackSort(
            field = com.musicapp.player.feature.category.CategoryTrackSortField.TITLE,
            direction = com.musicapp.player.feature.category.CategorySortDirection.ASCENDING,
        )
        _artistTrackSort.value = CategoryTrackSort(
            field = com.musicapp.player.feature.category.CategoryTrackSortField.ALBUM,
            direction = com.musicapp.player.feature.category.CategorySortDirection.ASCENDING,
        )
    }
}
