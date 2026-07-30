package com.musicapp.player.media.playback

import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.MediaLibraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Singleton
internal class DefaultPlaybackControllerFacade @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val connection: PlaybackControllerConnection,
    @param:ApplicationCoroutineScope private val applicationScope: CoroutineScope,
) : PlaybackControllerFacade {
    private var queueLoadJob: Job? = null
    private var startedClientCount = 0

    override val state: StateFlow<PlaybackControllerState>
        get() = connection.state

    @Synchronized
    override fun connect() {
        startedClientCount += 1
        if (startedClientCount == 1) connection.connect()
    }

    @Synchronized
    override fun disconnect() {
        if (startedClientCount == 0) return
        startedClientCount -= 1
        if (startedClientCount > 0) return
        queueLoadJob?.cancel()
        queueLoadJob = null
        connection.disconnect()
    }

    override fun play(context: PlaybackContext) {
        queueLoadJob?.cancel()
        queueLoadJob = applicationScope.launch {
            val tracks = buildList {
                context.orderedTrackIds.forEach { trackId ->
                    mediaLibraryRepository.getTrack(trackId)?.let(::add)
                }
            }
            val startIndex = tracks.indexOfFirst { it.id == context.selectedTrackId }
            if (startIndex >= 0) {
                connection.replaceQueue(
                    tracks = tracks,
                    startIndex = startIndex,
                    playWhenReady = true,
                )
            }
        }
    }

    override fun play() = connection.play()

    override fun pause() = connection.pause()

    override fun skipToPrevious() = connection.skipToPrevious()

    override fun skipToNext() = connection.skipToNext()

    override fun seekTo(positionMs: Long) = connection.seekTo(positionMs.coerceAtLeast(0))

    override fun setPlaybackMode(mode: PlaybackMode) = connection.setPlaybackMode(mode)

    override fun addToQueue(trackIds: List<TrackId>) = loadTracks(trackIds, connection::addToQueue)

    override fun playNext(trackIds: List<TrackId>) = loadTracks(trackIds, connection::playNext)

    override fun removeFromQueue(queueItemId: QueueItemId) = connection.removeFromQueue(queueItemId)

    private fun loadTracks(
        trackIds: List<TrackId>,
        command: (List<com.musicapp.player.core.domain.model.Track>) -> Unit,
    ) {
        if (trackIds.isEmpty()) return
        queueLoadJob?.cancel()
        queueLoadJob = applicationScope.launch {
            val tracks = trackIds.mapNotNull { mediaLibraryRepository.getTrack(it) }
            if (tracks.isNotEmpty()) command(tracks)
        }
    }
}
