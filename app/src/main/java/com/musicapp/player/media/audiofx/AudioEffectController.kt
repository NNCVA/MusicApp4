package com.musicapp.player.media.audiofx

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.core.domain.model.EqualizerPreset
import com.musicapp.player.core.domain.model.EqualizerSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEffectController @Inject constructor() {
    private var activeSessionId: Int = 0

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

        if (settings.enabled) {
            ensureEffectsInitialized(activeSessionId)
            applyEqualizerSettings(settings)
        } else {
            disableEffects()
        }
    }

    @Synchronized
    fun detachAudioSession() {
        if (activeSessionId == 0) return
        releaseEffects()
        activeSessionId = 0
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

    private fun applyEqualizerSettings(settings: EqualizerSettings) {
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

    private fun disableEffects() {
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
