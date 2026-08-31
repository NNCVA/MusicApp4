package com.musicapp.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.musicapp.player.core.aero.platform.AeroSignalSource
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.feature.permission.AndroidPermissionGateway
import com.musicapp.player.feature.permission.MediaPermissionCoordinator
import com.musicapp.player.feature.permission.MediaPermissionState
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.musicapp.player.feature.tracks.TracksViewModel
import com.musicapp.player.feature.tracks.TracksSyncController
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.media.service.MusicPlaybackService
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicDimensions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  @Inject lateinit var librarySyncCoordinator: LibrarySyncCoordinator
  @Inject lateinit var tracksSyncController: TracksSyncController
  @Inject lateinit var playbackController: PlaybackControllerFacade
  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var aeroSignalSource: AeroSignalSource

  private val tracksViewModel: TracksViewModel by viewModels()
  private lateinit var mediaPermissionCoordinator: MediaPermissionCoordinator
  private var isActivityStarted = false
  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (::mediaPermissionCoordinator.isInitialized) {
        mediaPermissionCoordinator.onPermissionResult(granted)
        reconcileMediaPermission()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      tracksViewModel.uiState.collect {}
    }

    val startTime = SystemClock.elapsedRealtime()
    splashScreen.setKeepOnScreenCondition {
      !tracksViewModel.isInitialDataReady.value && (SystemClock.elapsedRealtime() - startTime < 3000)
    }

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
      val appSettings by settingsRepository.settings.collectAsStateWithLifecycle()
      val librarySyncState by tracksSyncController.state.collectAsStateWithLifecycle()
      val aeroSignals by aeroSignalSource.signals.collectAsStateWithLifecycle()
      LaunchedEffect(appSettings.appLanguage) {
        applyAppLanguage(appSettings.appLanguage)
      }
      BoxWithConstraints {
        val windowWidthTier = MusicDimensions.tierForWidth(maxWidth)
        MusicAppTheme(
          presetTheme = appSettings.presetTheme,
          colorSource = appSettings.colorSource,
          themeMode = appSettings.themeMode,
          windowWidthTier = windowWidthTier,
        ) {
          MainNavigation(
            tracksViewModel = tracksViewModel,
            aeroMode = appSettings.aeroMode,
            aeroSignals = aeroSignals,
            themeMode = appSettings.themeMode,
            librarySyncState = librarySyncState,
            onFullExit = ::fullyExitApplication,
            onReturnToDesktop = { moveTaskToBack(true) },
            onThemeModeChange = { mode ->
              runCatching { settingsRepository.setThemeMode(mode) }.isSuccess
            },
            onScanMusic = tracksSyncController::requestManualSync,
            onAcknowledgeSyncFeedback = tracksSyncController::acknowledgeFeedback,
            permissionState = permissionState,
            onConfirmPermission = mediaPermissionCoordinator::confirmPurposeExplanation,
            onRetryPermission = mediaPermissionCoordinator::retryPermissionRequest,
            onOpenPermissionSettings = mediaPermissionCoordinator::openApplicationSettings,
            onOpenApplicationSettings = ::openApplicationSettings,
          )
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
    reconcileMediaPermission()
  }

  override fun onStart() {
    super.onStart()
    isActivityStarted = true
    playbackController.connect()
    if (::mediaPermissionCoordinator.isInitialized && mediaPermissionCoordinator.canQueryMediaStore) {
      librarySyncCoordinator.startForeground()
    }
  }

  override fun onStop() {
    isActivityStarted = false
    librarySyncCoordinator.stopForeground()
    playbackController.disconnect()
    super.onStop()
  }

  private fun reconcileMediaPermission() {
    val isGranted = mediaPermissionCoordinator.canQueryMediaStore
    if (isActivityStarted) {
      if (isGranted) librarySyncCoordinator.startForeground() else librarySyncCoordinator.stopForeground()
    }
  }

  private fun openApplicationSettings() {
    startActivity(
      Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
      ),
    )
  }

  private fun applyAppLanguage(language: AppLanguage) {
    if (ProcessLanguageState.appliedLanguage == language) return
    ProcessLanguageState.appliedLanguage = language
    AppCompatDelegate.setApplicationLocales(
      LocaleListCompat.forLanguageTags(language.languageTags()),
    )
  }

  private fun fullyExitApplication() {
    lifecycleScope.launch {
      val stoppedThroughSession =
        withTimeoutOrNull(FULL_EXIT_TIMEOUT_MS) {
          playbackController.requestFullExit()
        } == true
      if (!stoppedThroughSession) {
        stopService(Intent(this@MainActivity, MusicPlaybackService::class.java))
      }
      finishAndRemoveTask()
    }
  }

  private companion object {
    const val FULL_EXIT_TIMEOUT_MS = 3_000L
  }
}

internal fun AppLanguage.languageTags(): String =
  when (this) {
    AppLanguage.SYSTEM -> ""
    AppLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
    AppLanguage.ENGLISH -> "en"
  }

private object ProcessSyncLifecycle {
  var coldStartDispatched: Boolean = false
}

private object ProcessLanguageState {
  var appliedLanguage: AppLanguage? = null
}
