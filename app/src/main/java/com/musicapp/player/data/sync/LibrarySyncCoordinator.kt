package com.musicapp.player.data.sync

import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LibrarySyncCoordinator @Inject constructor(
    private val synchronizer: MediaLibrarySynchronizer,
    private val scanSource: MediaLibraryScanSource,
    private val snapshotSource: MediaStoreSnapshotSource,
    private val changeSource: MediaStoreChangeSource,
    @param:ApplicationCoroutineScope private val applicationScope: CoroutineScope,
) {
    private val requestMutex = Mutex()
    private var processing = false
    private var pendingRequest: SyncRequest? = null
    private var nextEventId = 1L
    private var foregroundJob: Job? = null
    private val mutableState = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle(false))
    private val mutableEvents = MutableSharedFlow<LibrarySyncEvent>(extraBufferCapacity = 8)

    val state: StateFlow<LibrarySyncState> = mutableState.asStateFlow()
    val events: SharedFlow<LibrarySyncEvent> = mutableEvents.asSharedFlow()

    fun onColdStart() {
        applicationScope.launch {
            val cache = synchronizer.cacheSnapshot()
            mutableState.value = LibrarySyncState.Idle(cache.hasSuccessfulScan)
            val current = try {
                snapshotSource.currentSnapshot()
            } catch (_: Exception) {
                enqueue(SyncRequest.coldStart(cache.hasSuccessfulScan))
                return@launch
            }
            if (!cache.hasSuccessfulScan || current.volumeSignatures != cache.mountedVolumeSignatures) {
                enqueue(SyncRequest.coldStart(cache.hasSuccessfulScan))
            }
        }
    }

    fun requestPermissionGrantedSync() {
        launchRequest(
            SyncRequest(
                trigger = MediaLibrarySyncTrigger.PERMISSION_GRANTED,
                mode = MediaLibrarySyncMode.FULL,
                feedback = MediaLibrarySyncFeedback.SILENT,
            ),
        )
    }

    fun requestManualSync() {
        launchRequest(
            SyncRequest(
                trigger = MediaLibrarySyncTrigger.MANUAL,
                mode = MediaLibrarySyncMode.FULL,
                feedback = MediaLibrarySyncFeedback.RESULT_DIALOG,
            ),
        )
    }

    @OptIn(FlowPreview::class)
    fun startForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = applicationScope.launch {
            changeSource.changes()
                .debounce(CONTENT_CHANGE_DEBOUNCE_MS)
                .collect {
                    enqueue(
                        SyncRequest(
                            trigger = MediaLibrarySyncTrigger.CONTENT_CHANGE,
                            mode = MediaLibrarySyncMode.INCREMENTAL,
                            feedback = MediaLibrarySyncFeedback.SILENT,
                        ),
                    )
                }
        }
    }

    fun stopForeground() {
        foregroundJob?.cancel()
        foregroundJob = null
    }

    fun acknowledgeFeedback(eventId: Long) {
        mutableState.value = when (val current = mutableState.value) {
            is LibrarySyncState.Idle -> current.copy(
                pendingFeedback = current.pendingFeedback?.takeUnless { it.eventId == eventId },
            )
            is LibrarySyncState.Syncing -> current.copy(
                pendingFeedback = current.pendingFeedback?.takeUnless { it.eventId == eventId },
            )
            is LibrarySyncState.Failed -> current.copy(
                pendingFeedback = current.pendingFeedback?.takeUnless { it.eventId == eventId },
            )
        }
    }

    private fun launchRequest(request: SyncRequest) {
        applicationScope.launch { enqueue(request) }
    }

    private suspend fun enqueue(request: SyncRequest) {
        val shouldStart = requestMutex.withLock {
            if (processing) {
                pendingRequest = pendingRequest?.merge(request) ?: request
                false
            } else {
                processing = true
                true
            }
        }
        if (shouldStart) {
            applicationScope.launch { processRequests(request) }
        }
    }

    private suspend fun processRequests(first: SyncRequest) {
        var current: SyncRequest? = first
        while (current != null) {
            execute(current)
            current = requestMutex.withLock {
                pendingRequest.also { pendingRequest = null }.also {
                    if (it == null) processing = false
                }
            }
        }
    }

    private suspend fun execute(request: SyncRequest) {
        val hadSuccessfulScan = state.value.hasSuccessfulScan || synchronizer.cacheSnapshot().hasSuccessfulScan
        val effectiveRequest = if (
            request.trigger == MediaLibrarySyncTrigger.PERMISSION_GRANTED && !hadSuccessfulScan
        ) {
            request.copy(feedback = MediaLibrarySyncFeedback.RESULT_DIALOG)
        } else {
            request
        }
        val previousFeedback = state.value.pendingFeedback
        mutableState.value = LibrarySyncState.Syncing(
            hadSuccessfulScan,
            effectiveRequest.trigger,
            previousFeedback,
        )
        val result = try {
            synchronizer.synchronize(effectiveRequest.mode, scanSource)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SyncReport(
                generation = 0,
                upsertedTrackCount = 0,
                removedTrackCount = 0,
                temporarilyUnavailableVolumeNames = emptySet(),
                failure = MediaLibrarySyncFailure.QUERY_FAILED,
            )
        }
        if (result.succeeded) {
            val event = LibrarySyncEvent.Completed(effectiveRequest.trigger, effectiveRequest.feedback, result)
            mutableState.value = LibrarySyncState.Idle(
                hasSuccessfulScan = true,
                pendingFeedback = event.pendingFeedbackOr(previousFeedback),
            )
            mutableEvents.emit(event)
        } else {
            val failure = checkNotNull(result.failure)
            val event = LibrarySyncEvent.Failed(effectiveRequest.trigger, effectiveRequest.feedback, failure)
            mutableState.value = LibrarySyncState.Failed(
                hasSuccessfulScan = hadSuccessfulScan,
                trigger = effectiveRequest.trigger,
                failure = failure,
                pendingFeedback = event.pendingFeedbackOr(previousFeedback),
            )
            mutableEvents.emit(event)
        }
    }

    private fun LibrarySyncEvent.pendingFeedbackOr(
        previous: PendingLibrarySyncFeedback?,
    ): PendingLibrarySyncFeedback? =
        if (feedback == MediaLibrarySyncFeedback.RESULT_DIALOG) {
            PendingLibrarySyncFeedback(nextEventId++, this)
        } else {
            previous
        }

    private data class SyncRequest(
        val trigger: MediaLibrarySyncTrigger,
        val mode: MediaLibrarySyncMode,
        val feedback: MediaLibrarySyncFeedback,
    ) {
        fun merge(other: SyncRequest): SyncRequest {
            val preferred = if (priority >= other.priority) this else other
            return preferred.copy(
                mode = if (mode == MediaLibrarySyncMode.FULL || other.mode == MediaLibrarySyncMode.FULL) {
                    MediaLibrarySyncMode.FULL
                } else {
                    MediaLibrarySyncMode.INCREMENTAL
                },
                feedback = if (
                    feedback == MediaLibrarySyncFeedback.RESULT_DIALOG ||
                    other.feedback == MediaLibrarySyncFeedback.RESULT_DIALOG
                ) {
                    MediaLibrarySyncFeedback.RESULT_DIALOG
                } else {
                    MediaLibrarySyncFeedback.SILENT
                },
            )
        }

        private val priority: Int
            get() = when (trigger) {
                MediaLibrarySyncTrigger.MANUAL -> 3
                MediaLibrarySyncTrigger.PERMISSION_GRANTED -> 2
                MediaLibrarySyncTrigger.COLD_START -> 1
                MediaLibrarySyncTrigger.CONTENT_CHANGE -> 0
            }

        companion object {
            fun coldStart(hasSuccessfulScan: Boolean) = SyncRequest(
                trigger = MediaLibrarySyncTrigger.COLD_START,
                mode = MediaLibrarySyncMode.FULL,
                feedback = if (hasSuccessfulScan) {
                    MediaLibrarySyncFeedback.SILENT
                } else {
                    MediaLibrarySyncFeedback.RESULT_DIALOG
                },
            )
        }
    }

    companion object {
        const val CONTENT_CHANGE_DEBOUNCE_MS: Long = 1_000
    }
}
