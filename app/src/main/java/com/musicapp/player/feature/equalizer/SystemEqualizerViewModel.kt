package com.musicapp.player.feature.equalizer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.data.equalizer.EqualizerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemEqualizerUiState(
    val isEnabled: Boolean = false,
    val isSystemPanelSupported: Boolean = false,
)

@HiltViewModel
class SystemEqualizerViewModel @Inject constructor(
    private val equalizerRepository: EqualizerRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val isPanelSupported: Boolean = checkSystemPanelSupported()

    val uiState: StateFlow<SystemEqualizerUiState> = equalizerRepository.settings
        .map { settings ->
            SystemEqualizerUiState(
                isEnabled = settings.systemEnabled,
                isSystemPanelSupported = isPanelSupported,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SystemEqualizerUiState(
                isSystemPanelSupported = isPanelSupported,
            ),
        )

    fun setSystemEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerRepository.setSystemEnabled(enabled)
        }
    }

    fun openSystemPanel(): Boolean {
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkSystemPanelSupported(): Boolean {
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
