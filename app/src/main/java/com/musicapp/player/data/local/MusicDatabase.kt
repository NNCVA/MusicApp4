package com.musicapp.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.musicapp.player.data.local.dao.HiddenTrackDao
import com.musicapp.player.data.local.dao.PathRuleDao
import com.musicapp.player.data.local.dao.PlayHistoryDao
import com.musicapp.player.data.local.dao.PlaybackSnapshotDao
import com.musicapp.player.data.local.dao.PlaylistDao
import com.musicapp.player.data.local.dao.PlaylistTrackDao
import com.musicapp.player.data.local.dao.TrackDao
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
        HiddenTrackEntity::class,
        PathRuleEntity::class,
        PlaybackSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun hiddenTrackDao(): HiddenTrackDao
    abstract fun pathRuleDao(): PathRuleDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playbackSnapshotDao(): PlaybackSnapshotDao
}
