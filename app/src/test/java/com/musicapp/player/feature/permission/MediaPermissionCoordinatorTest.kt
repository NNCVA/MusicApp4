package com.musicapp.player.feature.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionCoordinatorTest {
    @Test
    fun api26Through32UseReadExternalStorage() {
        assertEquals(
            MediaPermission.READ_EXTERNAL_STORAGE,
            MediaPermissionCoordinator(FakePermissionGateway(apiLevel = 26)).requiredPermission,
        )
        assertEquals(
            MediaPermission.READ_EXTERNAL_STORAGE,
            MediaPermissionCoordinator(FakePermissionGateway(apiLevel = 32)).requiredPermission,
        )
    }

    @Test
    fun api33AndAboveUseReadMediaAudio() {
        assertEquals(
            MediaPermission.READ_MEDIA_AUDIO,
            MediaPermissionCoordinator(FakePermissionGateway(apiLevel = 33)).requiredPermission,
        )
        assertEquals(
            MediaPermission.READ_MEDIA_AUDIO,
            MediaPermissionCoordinator(FakePermissionGateway(apiLevel = 36)).requiredPermission,
        )
    }

    @Test
    fun firstEntryRequiresPurposeExplanationAndCannotQueryMediaStore() {
        val coordinator = MediaPermissionCoordinator(FakePermissionGateway(apiLevel = 36))

        assertEquals(
            MediaPermissionState.PurposeExplanation(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertFalse(coordinator.canQueryMediaStore)
    }

    @Test
    fun confirmingPurposeExplanationStartsPermissionRequest() {
        val gateway = FakePermissionGateway(apiLevel = 36)
        val coordinator = MediaPermissionCoordinator(gateway)

        coordinator.confirmPurposeExplanation()

        assertEquals(
            MediaPermissionState.Requesting(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertEquals(listOf(MediaPermission.READ_MEDIA_AUDIO), gateway.requestHistory)
        assertTrue(MediaPermission.READ_MEDIA_AUDIO in gateway.requestedPermissions)
        assertFalse(coordinator.canQueryMediaStore)
    }

    @Test
    fun retryableDenialCanStartAnotherRequest() {
        val gateway = FakePermissionGateway(apiLevel = 32)
        val coordinator = MediaPermissionCoordinator(gateway)
        coordinator.confirmPurposeExplanation()
        gateway.rationalePermissions += MediaPermission.READ_EXTERNAL_STORAGE

        coordinator.onPermissionResult(granted = false)

        assertEquals(
            MediaPermissionState.DeniedCanRetry(MediaPermission.READ_EXTERNAL_STORAGE),
            coordinator.state.value,
        )
        assertFalse(coordinator.canQueryMediaStore)

        coordinator.retryPermissionRequest()

        assertEquals(
            MediaPermissionState.Requesting(MediaPermission.READ_EXTERNAL_STORAGE),
            coordinator.state.value,
        )
        assertEquals(2, gateway.requestHistory.size)
    }

    @Test
    fun permissionResultReconcilesAfterActivityRecreationDuringSystemRequest() {
        val gateway = FakePermissionGateway(apiLevel = 36).apply {
            requestedPermissions += MediaPermission.READ_MEDIA_AUDIO
        }
        val recreatedCoordinator = MediaPermissionCoordinator(gateway)
        assertEquals(
            MediaPermissionState.PermanentlyDenied(MediaPermission.READ_MEDIA_AUDIO),
            recreatedCoordinator.state.value,
        )
        gateway.grantedPermissions += MediaPermission.READ_MEDIA_AUDIO

        recreatedCoordinator.onPermissionResult(granted = true)

        assertEquals(
            MediaPermissionState.Granted(MediaPermission.READ_MEDIA_AUDIO),
            recreatedCoordinator.state.value,
        )
        assertTrue(recreatedCoordinator.canQueryMediaStore)
    }

    @Test
    fun denialWithoutRationaleIsPermanentAndCanOpenSettings() {
        val gateway = FakePermissionGateway(apiLevel = 33)
        val coordinator = MediaPermissionCoordinator(gateway)
        coordinator.confirmPurposeExplanation()

        coordinator.onPermissionResult(granted = false)

        assertEquals(
            MediaPermissionState.PermanentlyDenied(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertFalse(coordinator.canQueryMediaStore)

        coordinator.openApplicationSettings()

        assertEquals(
            MediaPermissionState.WaitingForSettingsReturn(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertEquals(1, gateway.applicationSettingsOpenCount)
    }

    @Test
    fun returningFromSettingsWithoutGrantRemainsPermanentlyDenied() {
        val gateway = permanentlyDeniedGateway()
        val coordinator = MediaPermissionCoordinator(gateway)

        coordinator.openApplicationSettings()
        coordinator.onApplicationSettingsReturned()

        assertEquals(
            MediaPermissionState.PermanentlyDenied(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertFalse(coordinator.canQueryMediaStore)
    }

    @Test
    fun returningFromSettingsWithGrantAllowsMediaStoreQuery() {
        val gateway = permanentlyDeniedGateway()
        val coordinator = MediaPermissionCoordinator(gateway)
        coordinator.openApplicationSettings()
        gateway.grantedPermissions += MediaPermission.READ_MEDIA_AUDIO

        coordinator.onApplicationSettingsReturned()

        assertEquals(
            MediaPermissionState.Granted(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertTrue(coordinator.canQueryMediaStore)
    }

    @Test
    fun alreadyGrantedPermissionStartsInGrantedState() {
        val gateway = FakePermissionGateway(apiLevel = 32).apply {
            grantedPermissions += MediaPermission.READ_EXTERNAL_STORAGE
        }

        val coordinator = MediaPermissionCoordinator(gateway)

        assertEquals(
            MediaPermissionState.Granted(MediaPermission.READ_EXTERNAL_STORAGE),
            coordinator.state.value,
        )
        assertTrue(coordinator.canQueryMediaStore)
        assertTrue(gateway.requestHistory.isEmpty())
    }

    @Test
    fun refreshRecognizesGrantMadeOutsideRequestFlow() {
        val gateway = FakePermissionGateway(apiLevel = 36)
        val coordinator = MediaPermissionCoordinator(gateway)
        gateway.grantedPermissions += MediaPermission.READ_MEDIA_AUDIO

        coordinator.refreshPermission()

        assertEquals(
            MediaPermissionState.Granted(MediaPermission.READ_MEDIA_AUDIO),
            coordinator.state.value,
        )
        assertTrue(coordinator.canQueryMediaStore)
    }

    private fun permanentlyDeniedGateway(): FakePermissionGateway =
        FakePermissionGateway(apiLevel = 33).apply {
            requestedPermissions += MediaPermission.READ_MEDIA_AUDIO
        }
}
