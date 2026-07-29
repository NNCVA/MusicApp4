package com.musicapp.player

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musicapp.player.data.settings.AppLanguageController
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.ui.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  @Inject lateinit var settingsRepository: SettingsRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    observeApplicationLanguage()
    setContent {
      val settings = settingsRepository.settings.collectAsStateWithLifecycle().value
      MainScreen(settings = settings)
    }
  }

  private fun observeApplicationLanguage() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        settingsRepository.settings
          .map { settings -> settings.appLanguage }
          .distinctUntilChanged()
          .collect(AppLanguageController::apply)
      }
    }
  }
}
