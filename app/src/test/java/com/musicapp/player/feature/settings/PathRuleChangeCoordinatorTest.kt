package com.musicapp.player.feature.settings

import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.MediaLibrarySyncFailure
import com.musicapp.player.data.sync.MediaLibrarySyncFeedback
import com.musicapp.player.data.sync.MediaLibrarySyncTrigger
import com.musicapp.player.data.sync.SyncReport
import com.musicapp.player.feature.settings.data.PathRuleChangeCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PathRuleChangeCoordinatorTest {
    @Test
    fun `cancel keeps pending revision across coordinator recreation`() = runTest {
        val settings = FakeSettingsRepository()
        val media = FakeMediaLibraryRepository()
        val sync = RecordingSettingsSyncController()
        val coordinator = PathRuleChangeCoordinator(media, settings, sync, backgroundScope)
        runCurrent()

        coordinator.addPathRule("external", "Music/Live/..", PathRuleKind.INCLUDE)
        val revision = coordinator.state.value.revision
        assertTrue(coordinator.state.value.pendingLibrarySync)
        assertTrue(coordinator.state.value.rescanPromptVisible)
        assertTrue(settings.pendingLibrarySync.value.isPending)
        assertEquals("Music", media.observePathRules().first().single().directory)

        coordinator.cancelRescan()
        assertTrue(coordinator.state.value.pendingLibrarySync)
        assertFalse(coordinator.state.value.rescanPromptVisible)

        val restored = PathRuleChangeCoordinator(media, settings, sync, backgroundScope)
        runCurrent()
        assertTrue(restored.state.value.pendingLibrarySync)
        assertEquals(revision, restored.state.value.revision)
        assertFalse(restored.state.value.rescanPromptVisible)
    }

    @Test
    fun `incremental and old full events cannot clear the confirmed revision`() = runTest {
        val settings = FakeSettingsRepository()
        val media = FakeMediaLibraryRepository()
        val sync = RecordingSettingsSyncController()
        val coordinator = PathRuleChangeCoordinator(media, settings, sync, backgroundScope)
        runCurrent()

        coordinator.markScanPolicyChanged()
        val revision = coordinator.state.value.revision
        coordinator.confirmRescan()
        runCurrent()
        assertEquals(1, sync.manualSyncRequests)

        sync.emit(completed(MediaLibrarySyncTrigger.CONTENT_CHANGE))
        sync.emit(completed(MediaLibrarySyncTrigger.MANUAL))
        runCurrent()
        assertTrue(coordinator.state.value.pendingLibrarySync)
        assertEquals(revision, coordinator.state.value.revision)

        sync.completeAwaited(completed(MediaLibrarySyncTrigger.MANUAL))
        runCurrent()
        assertFalse(coordinator.state.value.pendingLibrarySync)
        assertFalse(settings.pendingLibrarySync.value.isPending)
    }

    @Test
    fun `a rule change during full scan advances revision and rejects old completion`() = runTest {
        val settings = FakeSettingsRepository()
        val media = FakeMediaLibraryRepository()
        val sync = RecordingSettingsSyncController()
        val coordinator = PathRuleChangeCoordinator(media, settings, sync, backgroundScope)
        runCurrent()

        coordinator.markScanPolicyChanged()
        val firstRevision = coordinator.state.value.revision
        coordinator.confirmRescan()
        runCurrent()

        coordinator.addPathRule("external", "Music/New", PathRuleKind.INCLUDE)
        val secondRevision = coordinator.state.value.revision
        assertTrue(secondRevision > firstRevision)
        sync.completeAwaited(completed(MediaLibrarySyncTrigger.MANUAL))
        runCurrent()
        assertTrue(coordinator.state.value.pendingLibrarySync)
        assertEquals(secondRevision, coordinator.state.value.revision)

        coordinator.confirmRescan()
        runCurrent()
        sync.completeAwaited(completed(MediaLibrarySyncTrigger.MANUAL))
        runCurrent()
        assertFalse(coordinator.state.value.pendingLibrarySync)
    }

    @Test
    fun `failed confirmed full scan retains pending revision`() = runTest {
        val settings = FakeSettingsRepository()
        val coordinator = PathRuleChangeCoordinator(
            FakeMediaLibraryRepository(),
            settings,
            RecordingSettingsSyncController().also { sync ->
                sync.awaitedResult =
                    LibrarySyncEvent.Failed(
                        trigger = MediaLibrarySyncTrigger.MANUAL,
                        feedback = MediaLibrarySyncFeedback.RESULT_DIALOG,
                        failure = MediaLibrarySyncFailure.QUERY_FAILED,
                    )
            },
            backgroundScope,
        )
        runCurrent()

        coordinator.markScanPolicyChanged()
        val revision = coordinator.state.value.revision
        coordinator.confirmRescan()
        runCurrent()
        assertTrue(coordinator.state.value.pendingLibrarySync)
        assertEquals(revision, coordinator.state.value.revision)
        assertTrue(coordinator.state.value.rescanPromptVisible)
        assertTrue(settings.pendingLibrarySync.value.isPending)
    }

    private fun completed(trigger: MediaLibrarySyncTrigger) =
        LibrarySyncEvent.Completed(
            trigger = trigger,
            feedback =
                if (trigger == MediaLibrarySyncTrigger.CONTENT_CHANGE) {
                    MediaLibrarySyncFeedback.SILENT
                } else {
                    MediaLibrarySyncFeedback.RESULT_DIALOG
                },
            result = SyncReport(1, 1, 0, emptySet()),
        )
}
