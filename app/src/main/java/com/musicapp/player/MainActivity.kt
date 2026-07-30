package com.musicapp.player

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.musicapp.player.core.aero.platform.AeroSignalSource
import com.musicapp.player.core.domain.model.AppLanguage
import com.musicapp.player.feature.permission.AndroidPermissionGateway
import com.musicapp.player.feature.permission.MediaPermissionCoordinator
import com.musicapp.player.feature.permission.MediaPermissionState
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicDimensions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  @Inject lateinit var librarySyncCoordinator: LibrarySyncCoordinator
  @Inject lateinit var playbackController: PlaybackControllerFacade
  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var aeroSignalSource: AeroSignalSource

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
      val appSettings by settingsRepository.settings.collectAsStateWithLifecycle()
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
            aeroMode = appSettings.aeroMode,
            aeroSignals = aeroSignals,
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

  private fun applyAppLanguage(language: AppLanguage) {
    if (ProcessLanguageState.appliedLanguage == language) return
    ProcessLanguageState.appliedLanguage = language
    AppCompatDelegate.setApplicationLocales(
      LocaleListCompat.forLanguageTags(language.languageTags()),
    )
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
