package com.musicapp.player.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySyncCoordinatorTest {
    @Test
    fun coldStartWithoutSuccessfulScanRunsFullSyncAndRetainsResultFeedback() = runTest {
        val fixture = fixture(cache = cache(false), snapshot = snapshot("v1"))

        fixture.coordinator.onColdStart()
        advanceUntilIdle()

        assertEquals(listOf(MediaLibrarySyncMode.FULL), fixture.synchronizer.modes)
        val state = fixture.coordinator.state.value as LibrarySyncState.Idle
        assertTrue(state.hasSuccessfulScan)
        val feedback = state.pendingFeedback
        assertEquals(MediaLibrarySyncFeedback.RESULT_DIALOG, feedback?.event?.feedback)

        fixture.coordinator.acknowledgeFeedback(requireNotNull(feedback).eventId)
        assertNull(fixture.coordinator.state.value.pendingFeedback)
    }

    @Test
    fun coldStartWithMatchingCachedSignatureDoesNotSync() = runTest {
        val fixture = fixture(cache = cache(true, "v1"), snapshot = snapshot("v1"))

        fixture.coordinator.onColdStart()
        advanceUntilIdle()

        assertTrue(fixture.synchronizer.modes.isEmpty())
        assertTrue(fixture.coordinator.state.value.hasSuccessfulScan)
    }

    @Test
    fun coldStartWithChangedVersionOrVolumeSetRunsSilentFullSync() = runTest {
        val versionFixture = fixture(cache = cache(true, "old"), snapshot = snapshot("new"))
        versionFixture.coordinator.onColdStart()
        advanceUntilIdle()

        val volumeFixture = fixture(
            cache = MediaLibraryCacheSnapshot(true, mapOf(PRIMARY to "v1")),
            snapshot = MediaStoreSnapshot(setOf(PRIMARY, CARD), mapOf(PRIMARY to "v1", CARD to "v2")),
        )
        volumeFixture.coordinator.onColdStart()
        advanceUntilIdle()

        assertEquals(listOf(MediaLibrarySyncMode.FULL), versionFixture.synchronizer.modes)
        assertNull(versionFixture.coordinator.state.value.pendingFeedback)
        assertEquals(listOf(MediaLibrarySyncMode.FULL), volumeFixture.synchronizer.modes)
        assertNull(volumeFixture.coordinator.state.value.pendingFeedback)
    }

    @Test
    fun foregroundChangesAreMergedByOneSecondDebounce() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val fixture = fixture(cache(true, "v1"), snapshot("v1"), changes)
        fixture.coordinator.startForeground()
        runCurrent()

        changes.tryEmit(Unit)
        advanceTimeBy(400)
        changes.tryEmit(Unit)
        advanceTimeBy(400)
        changes.tryEmit(Unit)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(fixture.synchronizer.modes.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(listOf(MediaLibrarySyncMode.INCREMENTAL), fixture.synchronizer.modes)
        assertNull(fixture.coordinator.state.value.pendingFeedback)
        fixture.coordinator.stopForeground()
    }

    @Test
    fun contentChangesDuringSyncScheduleOnlyOneSuccessor() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(cache(true, "v1"), snapshot("v1"), changes)
        fixture.synchronizer.blockFirstSync = gate
        fixture.coordinator.startForeground()
        runCurrent()

        changes.tryEmit(Unit)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, fixture.synchronizer.modes.size)

        repeat(3) {
            changes.tryEmit(Unit)
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(1, fixture.synchronizer.modes.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            listOf(MediaLibrarySyncMode.INCREMENTAL, MediaLibrarySyncMode.INCREMENTAL),
            fixture.synchronizer.modes,
        )
        fixture.coordinator.stopForeground()
    }

    @Test
    fun manualFeedbackIsRetainedWhileAutomaticFeedbackIsSilent() = runTest {
        val fixture = fixture(cache(true, "v1"), snapshot("v1"))

        fixture.coordinator.requestPermissionGrantedSync()
        advanceUntilIdle()
        assertNull(fixture.coordinator.state.value.pendingFeedback)

        fixture.coordinator.requestManualSync()
        advanceUntilIdle()
        val feedback = requireNotNull(fixture.coordinator.state.value.pendingFeedback)
        val event = feedback.event as LibrarySyncEvent.Completed
        assertEquals(MediaLibrarySyncTrigger.MANUAL, event.trigger)
        assertEquals(MediaLibrarySyncFeedback.RESULT_DIALOG, event.feedback)
    }

    @Test
    fun failedManualSyncPreservesSuccessfulCacheAndRetainsFailureFeedback() = runTest {
        val fixture = fixture(cache(true, "v1"), snapshot("v1"))
        fixture.synchronizer.results += failedReport()

        fixture.coordinator.requestManualSync()
        advanceUntilIdle()

        val state = fixture.coordinator.state.value as LibrarySyncState.Failed
        assertTrue(state.hasSuccessfulScan)
        assertEquals(MediaLibrarySyncFailure.QUERY_FAILED, state.failure)
        assertTrue(state.pendingFeedback?.event is LibrarySyncEvent.Failed)
        assertTrue(fixture.synchronizer.cache.hasSuccessfulScan)
    }

    @Test
    fun concurrentAwaitedManualRequestsCompleteWithTheirOwnQueuedBatches() = runTest {
        val fixture = fixture(cache(true, "v1"), snapshot("v1"))
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        fixture.synchronizer.syncGates += firstGate
        fixture.synchronizer.syncGates += secondGate
        fixture.synchronizer.results += successfulReport(generation = 101)
        fixture.synchronizer.results += successfulReport(generation = 202)

        val first = async { fixture.coordinator.requestManualSyncAndAwait() }
        runCurrent()
        val second = async { fixture.coordinator.requestManualSyncAndAwait() }
        runCurrent()
        assertTrue(first.isActive)
        assertTrue(second.isActive)

        firstGate.complete(Unit)
        runCurrent()
        val firstEvent = first.await() as LibrarySyncEvent.Completed
        assertEquals(101L, firstEvent.result.generation)
        assertTrue(second.isActive)

        secondGate.complete(Unit)
        runCurrent()
        val secondEvent = second.await() as LibrarySyncEvent.Completed
        assertEquals(202L, secondEvent.result.generation)
        assertEquals(
            listOf(MediaLibrarySyncMode.FULL, MediaLibrarySyncMode.FULL),
            fixture.synchronizer.modes,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        cache: MediaLibraryCacheSnapshot,
        snapshot: MediaStoreSnapshot,
        changes: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 8),
    ): Fixture {
        val synchronizer = FakeSynchronizer(cache)
        val source = MediaLibraryScanSource {
            CompleteMediaLibraryScan(snapshot.mountedVolumeNames, emptyList(), snapshot.volumeSignatures)
        }
        return Fixture(
            coordinator = LibrarySyncCoordinator(
                synchronizer = synchronizer,
                scanSource = source,
                snapshotSource = MediaStoreSnapshotSource { snapshot },
                changeSource = MediaStoreChangeSource { changes },
                applicationScope = this,
            ),
            synchronizer = synchronizer,
        )
    }

    private data class Fixture(
        val coordinator: LibrarySyncCoordinator,
        val synchronizer: FakeSynchronizer,
    )

    private class FakeSynchronizer(
        var cache: MediaLibraryCacheSnapshot,
    ) : MediaLibrarySynchronizer {
        val modes = mutableListOf<MediaLibrarySyncMode>()
        val results = ArrayDeque<MediaLibrarySyncResult>()
        val syncGates = ArrayDeque<CompletableDeferred<Unit>>()
        var blockFirstSync: CompletableDeferred<Unit>? = null

        override suspend fun synchronize(
            mode: MediaLibrarySyncMode,
            source: MediaLibraryScanSource,
        ): MediaLibrarySyncResult {
            modes += mode
            if (syncGates.isNotEmpty()) {
                syncGates.removeFirst().await()
            } else if (modes.size == 1) {
                blockFirstSync?.await()
            }
            val scan = source.queryMountedAudio()
            val result = if (results.isEmpty()) successfulReport(scan.summary) else results.removeFirst()
            if (result.succeeded) {
                cache = MediaLibraryCacheSnapshot(true, scan.volumeSignatures)
            }
            return result
        }

        override suspend fun cacheSnapshot(): MediaLibraryCacheSnapshot = cache
    }

    private companion object {
        const val PRIMARY = "external_primary"
        const val CARD = "card"

        fun cache(successful: Boolean, version: String? = null) = MediaLibraryCacheSnapshot(
            hasSuccessfulScan = successful,
            mountedVolumeSignatures = if (version == null) emptyMap() else mapOf(PRIMARY to version),
        )

        fun snapshot(version: String) = MediaStoreSnapshot(
            mountedVolumeNames = setOf(PRIMARY),
            volumeSignatures = mapOf(PRIMARY to version),
        )

        fun successfulReport(summary: MediaLibraryScanSummary) = SyncReport(
            generation = 1,
            upsertedTrackCount = summary.acceptedCandidates.size,
            removedTrackCount = 0,
            temporarilyUnavailableVolumeNames = emptySet(),
            scanSummary = summary,
        )

        fun successfulReport(generation: Long) = SyncReport(
            generation = generation,
            upsertedTrackCount = 0,
            removedTrackCount = 0,
            temporarilyUnavailableVolumeNames = emptySet(),
        )

        fun failedReport() = SyncReport(
            generation = 1,
            upsertedTrackCount = 0,
            removedTrackCount = 0,
            temporarilyUnavailableVolumeNames = setOf(PRIMARY),
            failure = MediaLibrarySyncFailure.QUERY_FAILED,
        )
    }
}
