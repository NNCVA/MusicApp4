package com.musicapp.player.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.local.MusicDatabase

internal fun createInMemoryDatabase(): MusicDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    MusicDatabase::class.java,
).build()

internal fun track(
    volumeName: String = "external_primary",
    mediaStoreId: Long,
    title: String = "Track $mediaStoreId",
    artistId: Long = 7,
    albumId: Long = 8,
    relativePath: String = "Music/Album/",
) = Track(
    id = TrackId(volumeName, mediaStoreId),
    title = title,
    artistName = "Artist",
    artistId = ArtistId(artistId),
    albumTitle = "Album",
    albumId = AlbumId(volumeName, albumId),
    durationMs = 180_000,
    dateAddedMs = 1_000 + mediaStoreId,
    dateModifiedMs = 2_000 + mediaStoreId,
    relativePath = relativePath,
    displayName = "$title.mp3",
    mimeType = "audio/mpeg",
    sizeBytes = 4_096,
)
