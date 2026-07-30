package com.musicapp.player.feature.tracks.batch

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

interface BatchTrackActionExecutor {
    suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult
}

class DefaultBatchTrackActionExecutor @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
    private val clock: Clock,
) : BatchTrackActionExecutor {
    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        val distinctTrackIds = orderedTrackIds.distinct()
        if (distinctTrackIds.isEmpty()) return BatchTrackActionResult.EmptySelection

        return try {
            when (action) {
                is BatchTrackAction.AddToPlaylist -> {
                    val result =
                        playlistRepository.addTracks(
                            playlistId = action.playlistId,
                            trackIds = distinctTrackIds,
                            updatedAtMs = clock.currentTimeMillis(),
                        )
                    BatchTrackActionResult.Completed(
                        action = action,
                        selectedCount = distinctTrackIds.size,
                        affectedCount = result.changedCount,
                        skippedCount = result.skippedCount,
                    )
                }

                BatchTrackAction.AddToQueue -> {
                    playbackController.addToQueue(distinctTrackIds)
                    completed(action, distinctTrackIds.size)
                }

                BatchTrackAction.PlayNext -> {
                    playbackController.playNext(distinctTrackIds)
                    completed(action, distinctTrackIds.size)
                }

                BatchTrackAction.Hide -> {
                    mediaLibraryRepository.setHidden(
                        trackIds = distinctTrackIds,
                        hidden = true,
                        changedAtMs = clock.currentTimeMillis(),
                    )
                    completed(action, distinctTrackIds.size)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            BatchTrackActionResult.Failed(action, distinctTrackIds.size)
        }
    }

    private fun completed(action: BatchTrackAction, selectedCount: Int) =
        BatchTrackActionResult.Completed(
            action = action,
            selectedCount = selectedCount,
            affectedCount = selectedCount,
            skippedCount = 0,
        )
}
