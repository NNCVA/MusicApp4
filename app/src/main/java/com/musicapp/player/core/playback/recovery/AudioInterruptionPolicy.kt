package com.musicapp.player.core.playback.recovery

enum class AudioInterruption {
    TRANSIENT_FOCUS_LOSS,
    PERMANENT_FOCUS_LOSS,
    FOCUS_GAIN,
    DUCK,
    PRIVATE_OUTPUT_LOST,
}

data class AudioInterruptionAction(
    val pause: Boolean = false,
    val resume: Boolean = false,
    val cancelPendingFade: Boolean = false,
    val restoreUnityVolume: Boolean = false,
    val allowSystemDuck: Boolean = false,
)

/** Pure policy mirroring Media3 audio-focus and becoming-noisy behavior. */
class AudioInterruptionPolicy {
    private var resumeAfterTransientLoss = false

    fun onInterruption(
        interruption: AudioInterruption,
        wasPlaying: Boolean,
    ): AudioInterruptionAction = when (interruption) {
        AudioInterruption.TRANSIENT_FOCUS_LOSS -> {
            resumeAfterTransientLoss = wasPlaying
            pauseAction()
        }

        AudioInterruption.PERMANENT_FOCUS_LOSS,
        AudioInterruption.PRIVATE_OUTPUT_LOST,
        -> {
            resumeAfterTransientLoss = false
            pauseAction()
        }

        AudioInterruption.FOCUS_GAIN -> AudioInterruptionAction(
            resume = resumeAfterTransientLoss.also { resumeAfterTransientLoss = false },
        )

        AudioInterruption.DUCK -> AudioInterruptionAction(allowSystemDuck = true)
    }

    fun onUserPause() {
        resumeAfterTransientLoss = false
    }

    private fun pauseAction() = AudioInterruptionAction(
        pause = true,
        cancelPendingFade = true,
        restoreUnityVolume = true,
    )
}
