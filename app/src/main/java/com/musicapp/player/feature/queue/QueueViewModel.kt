package com.musicapp.player.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class QueueUiItem(
    val queueItemId: QueueItemId,
    val trackId: TrackId,
    val track: Track?,
    val isCurrent: Boolean,
) {
    val hasMetadata: Boolean
        get() = track != null
}

data class QueueUiState(
    val items: List<QueueUiItem> = emptyList(),
    val currentQueueItemId: QueueItemId? = null,
    val connectionState: PlaybackConnectionState = PlaybackConnectionState.DISCONNECTED,
) {
    val currentItem: QueueUiItem?
        get() = items.firstOrNull(QueueUiItem::isCurrent)
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    val uiState: StateFlow<QueueUiState> =
        combine(
            playbackController.state,
            mediaLibraryRepository.observeTracks(includeHidden = true),
        ) { playbackState, tracks ->
            playbackState.toQueueUiState(tracks.associateBy(Track::id))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = playbackController.state.value.toQueueUiState(emptyMap()),
        )

    fun jumpToQueueItem(queueItemId: QueueItemId) {
        if (uiState.value.items.none { it.queueItemId == queueItemId }) return
        playbackController.jumpToQueueItem(queueItemId)
    }

    fun removeFromQueue(queueItemId: QueueItemId) {
        if (uiState.value.items.none { it.queueItemId == queueItemId }) return
        playbackController.removeFromQueue(queueItemId)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun PlaybackControllerState.toQueueUiState(tracksById: Map<TrackId, Track>): QueueUiState =
    QueueUiState(
        items = queue.playbackOrder.map { item ->
            QueueUiItem(
                queueItemId = item.id,
                trackId = item.trackId,
                track = tracksById[item.trackId],
                isCurrent = item.id == queue.currentItemId,
            )
        },
        currentQueueItemId = queue.currentItemId,
        connectionState = connectionState,
    )
