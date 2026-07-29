package com.musicapp.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import com.musicapp.player.feature.permission.AndroidPermissionGateway
import com.musicapp.player.feature.permission.MediaPermissionCoordinator
import com.musicapp.player.feature.permission.MediaPermissionState
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicDimensions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var librarySyncCoordinator: LibrarySyncCoordinator

  private lateinit var mediaPermissionCoordinator: MediaPermissionCoordinator
  private var isActivityStarted = false
  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (::mediaPermissionCoordinator.isInitialized) {
        val wasGranted = mediaPermissionCoordinator.canQueryMediaStore
        mediaPermissionCoordinator.onPermissionResult(granted)
        reconcileMediaPermission(wasGranted)
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    mediaPermissionCoordinator =
      MediaPermissionCoordinator(
        AndroidPermissionGateway(
          activity = this,
          launchPermissionRequest = permissionLauncher::launch,
        ),
      )
    if (mediaPermissionCoordinator.canQueryMediaStore && !ProcessSyncLifecycle.coldStartDispatched) {
      ProcessSyncLifecycle.coldStartDispatched = true
      librarySyncCoordinator.onColdStart()
    }
    enableEdgeToEdge()
    setContent {
      val permissionState by mediaPermissionCoordinator.state.collectAsStateWithLifecycle()
      BoxWithConstraints {
        val windowWidthTier = MusicDimensions.tierForWidth(maxWidth)
        MusicAppTheme(windowWidthTier = windowWidthTier) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation(
              onExit = ::finish,
              permissionState = permissionState,
              onConfirmPermission = mediaPermissionCoordinator::confirmPurposeExplanation,
              onRetryPermission = mediaPermissionCoordinator::retryPermissionRequest,
              onOpenPermissionSettings = mediaPermissionCoordinator::openApplicationSettings,
            )
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (!::mediaPermissionCoordinator.isInitialized) return
    val wasGranted = mediaPermissionCoordinator.canQueryMediaStore
    when (mediaPermissionCoordinator.state.value) {
      is MediaPermissionState.Requesting -> Unit
      is MediaPermissionState.WaitingForSettingsReturn ->
        mediaPermissionCoordinator.onApplicationSettingsReturned()
      else -> mediaPermissionCoordinator.refreshPermission()
    }
    reconcileMediaPermission(wasGranted)
  }

  override fun onStart() {
    super.onStart()
    isActivityStarted = true
    if (::mediaPermissionCoordinator.isInitialized && mediaPermissionCoordinator.canQueryMediaStore) {
      librarySyncCoordinator.startForeground()
    }
  }

  override fun onStop() {
    isActivityStarted = false
    librarySyncCoordinator.stopForeground()
    super.onStop()
  }

  private fun reconcileMediaPermission(wasGranted: Boolean) {
    val isGranted = mediaPermissionCoordinator.canQueryMediaStore
    if (!wasGranted && isGranted) {
      ProcessSyncLifecycle.coldStartDispatched = true
      librarySyncCoordinator.requestPermissionGrantedSync()
    }
    if (isActivityStarted) {
      if (isGranted) librarySyncCoordinator.startForeground() else librarySyncCoordinator.stopForeground()
    }
  }
}

private object ProcessSyncLifecycle {
  var coldStartDispatched: Boolean = false
}
