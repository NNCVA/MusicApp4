package com.musicapp.player.media.audiofx

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEffectController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var activeSessionId: Int = 0
    private var systemSessionBroadcasted: Boolean = false

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    @Synchronized
    fun attachAudioSession(audioSessionId: Int, settings: EqualizerSettings) {
        if (audioSessionId == 0) return
        if (activeSessionId != audioSessionId) {
            detachAudioSession()
            activeSessionId = audioSessionId
        }
        applySettings(settings)
    }

    @Synchronized
    fun applySettings(settings: EqualizerSettings) {
        if (activeSessionId == 0) return

        // 1. System Equalizer session broadcast
        if (settings.systemEnabled) {
            if (!systemSessionBroadcasted) {
                broadcastSystemSession(activeSessionId, open = true)
                systemSessionBroadcasted = true
            }
        } else {
            if (systemSessionBroadcasted) {
                broadcastSystemSession(activeSessionId, open = false)
                systemSessionBroadcasted = false
            }
        }

        // 2. Custom Equalizer audiofx suite
        if (settings.customEnabled) {
            ensureEffectsInitialized(activeSessionId)
            applyCustomEqualizerSettings(settings)
        } else {
            disableCustomEffects()
        }
    }

    @Synchronized
    fun detachAudioSession() {
        if (activeSessionId == 0) return
        if (systemSessionBroadcasted) {
            broadcastSystemSession(activeSessionId, open = false)
            systemSessionBroadcasted = false
        }
        releaseEffects()
        activeSessionId = 0
    }

    private fun broadcastSystemSession(audioSessionId: Int, open: Boolean) {
        try {
            val action = if (open) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
            } else {
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            }
            val intent = Intent(action).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                if (open) {
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
            // Ignore broadcast failures on platforms without standard audio effect handling
        }
    }

    private fun ensureEffectsInitialized(sessionId: Int) {
        if (equalizer == null) {
            try {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
            } catch (_: Exception) {
                equalizer = null
            }
        }
        if (bassBoost == null) {
            try {
                bassBoost = BassBoost(0, sessionId)
            } catch (_: Exception) {
                bassBoost = null
            }
        }
        if (virtualizer == null) {
            try {
                virtualizer = Virtualizer(0, sessionId)
            } catch (_: Exception) {
                virtualizer = null
            }
        }
    }

    private fun applyCustomEqualizerSettings(settings: EqualizerSettings) {
        equalizer?.let { eq ->
            try {
                if (!eq.enabled) eq.enabled = true
                settings.bandLevels.forEach { (bandIndex, levelMb) ->
                    if (bandIndex in 0 until eq.numberOfBands.toInt()) {
                        eq.setBandLevel(bandIndex.toShort(), levelMb.toShort())
                    }
                }
            } catch (_: Exception) {}
        }
        bassBoost?.let { bb ->
            try {
                if (bb.strengthSupported) {
                    bb.enabled = settings.bassBoostEnabled
                    if (settings.bassBoostEnabled) {
                        bb.setStrength(settings.bassBoostStrength.toShort())
                    }
                }
            } catch (_: Exception) {}
        }
        virtualizer?.let { virt ->
            try {
                if (virt.strengthSupported) {
                    virt.enabled = settings.virtualizerEnabled
                    if (settings.virtualizerEnabled) {
                        virt.setStrength(settings.virtualizerStrength.toShort())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun disableCustomEffects() {
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bassBoost?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    companion object {
        fun queryHardwareCapabilities(defaultLevels: Map<Int, Int> = emptyMap()): Pair<List<EqualizerBand>, List<EqualizerPreset>> {
            return try {
                val probe = Equalizer(0, 0)
                try {
                    val numBands = probe.numberOfBands.toInt()
                    val bands = (0 until numBands).map { bandIdx ->
                        val freqHz = probe.getCenterFreq(bandIdx.toShort()) / 1000
                        val currentLevel = defaultLevels[bandIdx] ?: probe.getBandLevel(bandIdx.toShort()).toInt()
                        EqualizerBand(index = bandIdx, centerFreqHz = freqHz, levelMb = currentLevel)
                    }
                    val numPresets = probe.numberOfPresets.toInt()
                    val presets = (0 until numPresets).map { presetIdx ->
                        val name = probe.getPresetName(presetIdx.toShort())
                        probe.usePreset(presetIdx.toShort())
                        val levels = (0 until numBands).map { bandIdx ->
                            probe.getBandLevel(bandIdx.toShort()).toInt()
                        }
                        EqualizerPreset(index = presetIdx, name = name, bandLevels = levels)
                    }
                    Pair(bands, presets)
                } finally {
                    probe.release()
                }
            } catch (_: Exception) {
                val defaultBands = EqualizerSettings.DEFAULT_5_BAND_FREQS_HZ.mapIndexed { idx, freq ->
                    EqualizerBand(index = idx, centerFreqHz = freq, levelMb = defaultLevels[idx] ?: 0)
                }
                val fallbackPresets = listOf(
                    EqualizerPreset(0, "Normal", listOf(0, 0, 0, 0, 0)),
                    EqualizerPreset(1, "Classical", listOf(400, 300, -200, 400, 300)),
                    EqualizerPreset(2, "Dance", listOf(600, 0, 200, 400, 100)),
                    EqualizerPreset(3, "Flat", listOf(0, 0, 0, 0, 0)),
                    EqualizerPreset(4, "Folk", listOf(300, 0, 0, 200, -100)),
                    EqualizerPreset(5, "Heavy Metal", listOf(400, 100, 900, 300, 0)),
                    EqualizerPreset(6, "Hip Hop", listOf(500, 300, 0, 100, 300)),
                    EqualizerPreset(7, "Jazz", listOf(400, 200, -200, 200, 500)),
                    EqualizerPreset(8, "Pop", listOf(-100, 200, 500, 100, -200)),
                    EqualizerPreset(9, "Rock", listOf(500, 300, -100, 300, 500)),
                )
                Pair(defaultBands, fallbackPresets)
            }
        }
    }
}
