package com.musicapp.player.data.repository

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeMediaLibraryRepository(
    initialTracks: List<Track> = emptyList(),
    initialRules: List<PathRule> = emptyList(),
) : MediaLibraryRepository {
    private val mutex = Mutex()
    private val tracks = MutableStateFlow(initialTracks.associateBy(Track::id))
    private val hiddenIds = MutableStateFlow(emptySet<TrackId>())
    private val rules = MutableStateFlow(initialRules)
    private var nextRuleId = (initialRules.maxOfOrNull { it.id.value } ?: 0) + 1

    override fun observeTracks(includeHidden: Boolean): Flow<List<Track>> =
        if (includeHidden) {
            tracks.map { it.values.sortedWith(trackComparator) }
        } else {
            combine(tracks, hiddenIds) { values, hidden ->
                values.values.filterNot { it.id in hidden }.sortedWith(trackComparator)
            }
        }

    override fun observeAlbumTracks(albumId: AlbumId): Flow<List<Track>> =
        combine(tracks, hiddenIds) { values, hidden ->
            values.values.filter { it.albumId == albumId && it.id !in hidden }.sortedWith(trackComparator)
        }

    override fun observeArtistTracks(artistId: ArtistId): Flow<List<Track>> =
        combine(tracks, hiddenIds) { values, hidden ->
            values.values.filter { track ->
                track.id !in hidden &&
                    com.musicapp.player.feature.artists.ArtistGrouping.splitArtistNames(track.artistName)
                        .any { it.equals(artistId.name, ignoreCase = true) }
            }.sortedWith(trackComparator)
        }

    override fun observeFolderTracks(volumeName: String, directoryPath: String): Flow<List<Track>> {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        val normalizedDirectory = normalizeFolderDirectoryPath(directoryPath)
        return combine(tracks, hiddenIds) { values, hidden ->
            values.values.filter {
                val trackDirectory = normalizeFolderDirectoryPath(it.relativePath)
                it.id.volumeName == volumeName && it.id !in hidden &&
                    (normalizedDirectory.isEmpty() || trackDirectory == normalizedDirectory ||
                        trackDirectory.startsWith("$normalizedDirectory/"))
            }.sortedWith(compareBy(Track::relativePath).then(trackComparator))
        }
    }

    override suspend fun getTrack(trackId: TrackId): Track? = tracks.value[trackId]

    override suspend fun mergeTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        require(tracks.map(Track::id).distinct().size == tracks.size)
        mutex.withLock { this.tracks.value = this.tracks.value + tracks.associateBy(Track::id) }
    }

    override suspend fun replaceTracksForVolume(volumeName: String, tracks: List<Track>) {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        if (tracks.isEmpty()) return
        require(tracks.all { it.id.volumeName == volumeName })
        require(tracks.map(Track::id).distinct().size == tracks.size)
        mutex.withLock {
            this.tracks.value = this.tracks.value.filterKeys { it.volumeName != volumeName } +
                tracks.associateBy(Track::id)
            hiddenIds.value = hiddenIds.value.intersect(this.tracks.value.keys)
        }
    }

    override suspend fun setVolumeAvailability(volumeName: String, availability: Availability) {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        mutex.withLock {
            tracks.value = tracks.value.mapValues { (id, track) ->
                if (id.volumeName == volumeName) track.copy(availability = availability) else track
            }
        }
    }

    override suspend fun setHidden(trackIds: List<TrackId>, hidden: Boolean, changedAtMs: Long) {
        require(changedAtMs >= 0)
        val distinctTrackIds = trackIds.distinct()
        if (distinctTrackIds.isEmpty()) return
        mutex.withLock {
            require(distinctTrackIds.all(tracks.value::containsKey)) { "track does not exist" }
            hiddenIds.value =
                if (hidden) hiddenIds.value + distinctTrackIds else hiddenIds.value - distinctTrackIds.toSet()
        }
    }

    override fun observePathRules(): Flow<List<PathRule>> = rules

    override suspend fun addPathRule(
        volumeName: String,
        directory: String,
        kind: PathRuleKind,
    ): PathRule = mutex.withLock {
        require(volumeName.isNotBlank())
        val normalizedDirectory = normalizeFolderDirectoryPath(directory)
        require(rules.value.none {
            it.volumeName == volumeName && it.directory == normalizedDirectory && it.kind == kind
        })
        PathRule(PathRuleId(nextRuleId++), volumeName, normalizedDirectory, kind).also {
            rules.value += it
        }
    }

    override suspend fun replacePathRules(rules: List<PathRule>) {
        if (rules.isEmpty()) return
        require(rules.map(PathRule::id).distinct().size == rules.size)
        val normalizedRules = rules.map { rule ->
            rule.copy(directory = normalizeFolderDirectoryPath(rule.directory))
        }
        require(normalizedRules.distinctBy { Triple(it.volumeName, it.directory, it.kind) }.size == rules.size)
        mutex.withLock { this.rules.value = normalizedRules }
    }

    override suspend fun removePathRule(ruleId: PathRuleId) {
        mutex.withLock { rules.value = rules.value.filterNot { it.id == ruleId } }
    }

    override suspend fun clearPathRules() {
        mutex.withLock { rules.value = emptyList() }
    }

    private companion object {
        val trackComparator = compareBy<Track> { it.title.lowercase() }
            .thenBy { it.id.volumeName }
            .thenBy { it.id.mediaStoreId }
    }
}

class FakePlaylistRepository(
    initialPlaylists: List<Playlist> = emptyList(),
    private val existingTrackIds: Set<TrackId> = emptySet(),
) : PlaylistRepository {
    private val mutex = Mutex()
    private val playlists = MutableStateFlow(initialPlaylists.associateBy(Playlist::id))
    private var nextPlaylistId = (initialPlaylists.maxOfOrNull { it.id.value } ?: 0) + 1

    override fun observePlaylists(): Flow<List<Playlist>> = playlists.map { values ->
        values.values.sortedWith(
            compareByDescending<Playlist> { it.createdAtMs }.thenByDescending { it.id.value },
        )
    }

    override fun observePlaylist(playlistId: PlaylistId): Flow<Playlist?> =
        playlists.map { it[playlistId] }

    override suspend fun createPlaylist(
        displayName: String,
        normalizedName: String,
        createdAtMs: Long,
    ): PlaylistId = mutex.withLock {
        validatePlaylistNames(displayName, normalizedName)
        require(createdAtMs >= 0) { "createdAtMs must not be negative" }
        require(playlists.value.values.none { it.normalizedName.equals(normalizedName, ignoreCase = true) })
        val id = PlaylistId(nextPlaylistId++)
        playlists.value += id to Playlist(id, displayName, normalizedName, createdAtMs = createdAtMs)
        id
    }

    override suspend fun renamePlaylist(
        playlistId: PlaylistId,
        displayName: String,
        normalizedName: String,
        updatedAtMs: Long,
    ) {
        validatePlaylistNames(displayName, normalizedName)
        mutex.withLock {
            require(playlists.value.values.none {
                it.id != playlistId && it.normalizedName.equals(normalizedName, ignoreCase = true)
            })
            val old = requireNotNull(playlists.value[playlistId])
            playlists.value += playlistId to old.copy(
                displayName = displayName,
                normalizedName = normalizedName,
                updatedAtMs = updatedAtMs,
            )
        }
    }

    override suspend fun deletePlaylist(playlistId: PlaylistId) {
        mutex.withLock { playlists.value -= playlistId }
    }

    override suspend fun deleteAllPlaylists() {
        mutex.withLock { playlists.value = emptyMap() }
    }

    override suspend fun addTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ): PlaylistTrackChangeResult {
        if (trackIds.isEmpty()) return PlaylistTrackChangeResult(0, 0)
        return mutex.withLock {
            val old = requireNotNull(playlists.value[playlistId])
            require(trackIds.all(existingTrackIds::contains)) { "every referenced track must exist" }
            val additions = trackIds.distinct().filterNot(old.trackIds::contains)
            if (additions.isEmpty()) return@withLock PlaylistTrackChangeResult(0, trackIds.size)
            playlists.value += playlistId to old.copy(
                trackIds = old.trackIds + additions,
                updatedAtMs = updatedAtMs,
            )
            PlaylistTrackChangeResult(additions.size, trackIds.size - additions.size)
        }
    }

    override suspend fun removeTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ): PlaylistTrackChangeResult {
        if (trackIds.isEmpty()) return PlaylistTrackChangeResult(0, 0)
        return mutex.withLock {
            val old = requireNotNull(playlists.value[playlistId])
            val requestedIds = trackIds.toSet()
            val remaining = old.trackIds.filterNot(requestedIds::contains)
            val removedCount = old.trackIds.size - remaining.size
            if (removedCount == 0) return@withLock PlaylistTrackChangeResult(0, trackIds.size)
            playlists.value += playlistId to old.copy(trackIds = remaining, updatedAtMs = updatedAtMs)
            PlaylistTrackChangeResult(removedCount, trackIds.size - removedCount)
        }
    }

    override suspend fun replaceTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ) {
        if (trackIds.isEmpty()) return
        require(trackIds.distinct().size == trackIds.size)
        mutex.withLock {
            val old = requireNotNull(playlists.value[playlistId])
            require(trackIds.all(existingTrackIds::contains)) { "every referenced track must exist" }
            playlists.value += playlistId to old.copy(trackIds = trackIds, updatedAtMs = updatedAtMs)
        }
    }

    override suspend fun clearTracks(playlistId: PlaylistId, updatedAtMs: Long) {
        mutex.withLock {
            val old = requireNotNull(playlists.value[playlistId])
            playlists.value += playlistId to old.copy(trackIds = emptyList(), updatedAtMs = updatedAtMs)
        }
    }
}

class FakeHistoryRepository(
    initialHistory: List<PlayHistory> = emptyList(),
    private val existingTrackIds: Set<TrackId> = emptySet(),
) : HistoryRepository {
    private val mutex = Mutex()
    private val history = MutableStateFlow(initialHistory.associateBy(PlayHistory::trackId))

    override fun observeHistory(): Flow<List<PlayHistory>> = history.map { values ->
        values.values.sortedByDescending(PlayHistory::lastPlayedAtMs)
    }

    override suspend fun recordPlayback(trackId: TrackId, playedAtMs: Long) {
        require(playedAtMs >= 0) { "playedAtMs must not be negative" }
        mutex.withLock {
            require(trackId in existingTrackIds) { "track does not exist" }
            val old = history.value[trackId]
            history.value += trackId to PlayHistory(trackId, playedAtMs, (old?.playCount ?: 0) + 1)
        }
    }

    override suspend fun clearHistory() {
        mutex.withLock { history.value = emptyMap() }
    }
}

class FakePlaybackSnapshotRepository(
    initialSnapshot: PlaybackSnapshot? = null,
) : PlaybackSnapshotRepository {
    private val snapshot = MutableStateFlow(initialSnapshot)

    override fun observeSnapshot(): Flow<PlaybackSnapshot?> = snapshot
    override suspend fun getSnapshot(): PlaybackSnapshot? = snapshot.value
    override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
        this.snapshot.value = snapshot
    }
    override suspend fun clearSnapshot() {
        snapshot.value = null
    }
}

class FakeSettingsRepository(
    initialSettings: com.musicapp.player.core.domain.model.AppSettings = com.musicapp.player.core.domain.model.AppSettings(),
    initialLibrarySyncPending: Boolean = false,
) : com.musicapp.player.data.settings.SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)
    private val mutablePendingLibrarySync = MutableStateFlow(
        com.musicapp.player.data.settings.PendingLibrarySyncState(isPending = initialLibrarySyncPending),
    )
    private val pendingMutex = Mutex()

    override val settings: StateFlow<com.musicapp.player.core.domain.model.AppSettings> = mutableSettings
    override val pendingLibrarySync: StateFlow<com.musicapp.player.data.settings.PendingLibrarySyncState> = mutablePendingLibrarySync

    override suspend fun currentSettings(): com.musicapp.player.core.domain.model.AppSettings = mutableSettings.value
    override suspend fun setColorSource(value: com.musicapp.player.core.domain.model.ColorSource) = update { copy(colorSource = value) }
    override suspend fun setPresetTheme(value: com.musicapp.player.core.domain.model.PresetTheme) = update { copy(presetTheme = value) }
    override suspend fun setThemeMode(value: com.musicapp.player.core.domain.model.ThemeMode) = update { copy(themeMode = value) }
    override suspend fun setAppLanguage(value: com.musicapp.player.core.domain.model.AppLanguage) = update { copy(appLanguage = value) }
    override suspend fun setAeroMode(value: com.musicapp.player.core.domain.model.AeroMode) = update { copy(aeroMode = value) }
    override suspend fun setFadeThroughDurationMs(value: Long) = update { copy(fadeThroughDurationMs = value) }
    override suspend fun setScanMode(value: com.musicapp.player.core.domain.model.ScanMode) = update { copy(scanMode = value) }
    override suspend fun setSkipShortAudio(value: Boolean) = update { copy(skipShortAudio = value) }
    override suspend fun setAlbumGridColumns(value: Int) = update { copy(albumGridColumns = value) }

    override suspend fun markLibrarySyncPending(): Long = pendingMutex.withLock {
        val revision = mutablePendingLibrarySync.value.revision + 1
        mutablePendingLibrarySync.value = com.musicapp.player.data.settings.PendingLibrarySyncState(revision, isPending = true)
        revision
    }

    override suspend fun clearLibrarySyncPending(expectedRevision: Long): Boolean =
        pendingMutex.withLock {
            val current = mutablePendingLibrarySync.value
            if (!current.isPending || current.revision != expectedRevision) {
                false
            } else {
                mutablePendingLibrarySync.value = current.copy(isPending = false)
                true
            }
        }

    override suspend fun reset() {
        mutableSettings.value = com.musicapp.player.core.domain.model.AppSettings()
    }

    private fun update(transform: com.musicapp.player.core.domain.model.AppSettings.() -> com.musicapp.player.core.domain.model.AppSettings) {
        mutableSettings.value = mutableSettings.value.transform()
    }
}

private fun validatePlaylistNames(displayName: String, normalizedName: String) {
    require(displayName.isNotBlank()) { "displayName must not be blank" }
    require(displayName == displayName.trim()) { "displayName must be trimmed" }
    require(normalizedName.isNotBlank()) { "normalizedName must not be blank" }
}
