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
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicDimensions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private lateinit var mediaPermissionCoordinator: MediaPermissionCoordinator
  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (::mediaPermissionCoordinator.isInitialized) {
        mediaPermissionCoordinator.onPermissionResult(granted)
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
    when (mediaPermissionCoordinator.state.value) {
      is MediaPermissionState.Requesting -> Unit
      is MediaPermissionState.WaitingForSettingsReturn ->
        mediaPermissionCoordinator.onApplicationSettingsReturned()
      else -> mediaPermissionCoordinator.refreshPermission()
    }
  }
}
