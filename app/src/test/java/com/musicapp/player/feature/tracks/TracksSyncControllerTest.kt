package com.musicapp.player.feature.tracks

import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.sync.CompleteMediaLibraryScan
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.data.sync.MediaLibraryCacheSnapshot
import com.musicapp.player.data.sync.MediaLibraryScanSource
import com.musicapp.player.data.sync.MediaLibrarySyncFailure
import com.musicapp.player.data.sync.MediaLibrarySyncMode
import com.musicapp.player.data.sync.MediaLibrarySyncResult
import com.musicapp.player.data.sync.MediaLibrarySynchronizer
import com.musicapp.player.data.sync.MediaStoreChangeSource
import com.musicapp.player.data.sync.MediaStoreSnapshot
import com.musicapp.player.data.sync.MediaStoreSnapshotSource
import com.musicapp.player.data.sync.SyncReport
import com.musicapp.player.feature.settings.FakeSettingsRepository
import com.musicapp.player.feature.settings.RecordingSettingsSyncController
import com.musicapp.player.feature.settings.data.PathRuleChangeCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TracksSyncControllerTest {
    @Test
    fun `manual full sync after canceled path prompt clears captured pending revision`() = runTest {
        val fixture = fixture(successfulReport())
        val pathCoordinator =
            PathRuleChangeCoordinator(
                mediaLibraryRepository = FakeMediaLibraryRepository(),
                settingsRepository = fixture.settings,
                syncController = RecordingSettingsSyncController(),
                applicationScope = backgroundScope,
            )
        runCurrent()
        pathCoordinator.markScanPolicyChanged()
        pathCoordinator.cancelRescan()
        val capturedRevision = fixture.settings.pendingLibrarySync.value.revision

        fixture.controller.requestManualSync()
        runCurrent()

        assertFalse(fixture.settings.pendingLibrarySync.value.isPending)
        assertFalse(pathCoordinator.state.value.pendingLibrarySync)
        assertEquals(capturedRevision, fixture.settings.pendingLibrarySync.value.revision)
        assertEquals(listOf(MediaLibrarySyncMode.FULL), fixture.synchronizer.modes)
        val feedback = fixture.coordinator.state.value.pendingFeedback
        assertTrue(feedback?.event is LibrarySyncEvent.Completed)
    }

    @Test
    fun `failed tracks manual full sync retains pending revision`() = runTest {
        val fixture = fixture(failedReport())
        val revision = fixture.settings.markLibrarySyncPending()

        fixture.controller.requestManualSync()
        runCurrent()

        assertTrue(fixture.settings.pendingLibrarySync.value.isPending)
        assertEquals(revision, fixture.settings.pendingLibrarySync.value.revision)
        assertTrue(fixture.coordinator.state.value is LibrarySyncState.Failed)
    }

    @Test
    fun `path revision changed during tracks scan rejects captured completion`() = runTest {
        val fixture = fixture(successfulReport())
        val gate = CompletableDeferred<Unit>()
        fixture.synchronizer.gate = gate
        val capturedRevision = fixture.settings.markLibrarySyncPending()

        fixture.controller.requestManualSync()
        runCurrent()
        val latestRevision = fixture.settings.markLibrarySyncPending()
        assertTrue(latestRevision > capturedRevision)

        gate.complete(Unit)
        runCurrent()

        assertTrue(fixture.settings.pendingLibrarySync.value.isPending)
        assertEquals(latestRevision, fixture.settings.pendingLibrarySync.value.revision)
    }

    private fun TestScope.fixture(result: MediaLibrarySyncResult): Fixture {
        val settings = FakeSettingsRepository()
        val synchronizer = GatedSynchronizer(result)
        val snapshot =
            MediaStoreSnapshot(
                mountedVolumeNames = setOf(VOLUME),
                volumeSignatures = mapOf(VOLUME to SIGNATURE),
            )
        val coordinator =
            LibrarySyncCoordinator(
                synchronizer = synchronizer,
                scanSource = MediaLibraryScanSource {
                    CompleteMediaLibraryScan(
                        mountedVolumeNames = snapshot.mountedVolumeNames,
                        candidates = emptyList(),
                        volumeSignatures = snapshot.volumeSignatures,
                    )
                },
                snapshotSource = MediaStoreSnapshotSource { snapshot },
                changeSource = MediaStoreChangeSource { emptyFlow() },
                applicationScope = backgroundScope,
            )
        return Fixture(
            controller = DefaultTracksSyncController(coordinator, settings, backgroundScope),
            coordinator = coordinator,
            settings = settings,
            synchronizer = synchronizer,
        )
    }

    private data class Fixture(
        val controller: DefaultTracksSyncController,
        val coordinator: LibrarySyncCoordinator,
        val settings: FakeSettingsRepository,
        val synchronizer: GatedSynchronizer,
    )

    private class GatedSynchronizer(
        private val result: MediaLibrarySyncResult,
    ) : MediaLibrarySynchronizer {
        var gate: CompletableDeferred<Unit>? = null
        val modes = mutableListOf<MediaLibrarySyncMode>()

        override suspend fun synchronize(
            mode: MediaLibrarySyncMode,
            source: MediaLibraryScanSource,
        ): MediaLibrarySyncResult {
            modes += mode
            gate?.await()
            source.queryMountedAudio()
            return result
        }

        override suspend fun cacheSnapshot() =
            MediaLibraryCacheSnapshot(
                hasSuccessfulScan = true,
                mountedVolumeSignatures = mapOf(VOLUME to SIGNATURE),
            )
    }

    private companion object {
        const val VOLUME = "external_primary"
        const val SIGNATURE = "v1"

        fun successfulReport() =
            SyncReport(
                generation = 1,
                upsertedTrackCount = 0,
                removedTrackCount = 0,
                temporarilyUnavailableVolumeNames = emptySet(),
            )

        fun failedReport() =
            SyncReport(
                generation = 1,
                upsertedTrackCount = 0,
                removedTrackCount = 0,
                temporarilyUnavailableVolumeNames = emptySet(),
                failure = MediaLibrarySyncFailure.QUERY_FAILED,
            )
    }
}
