package com.musicapp.player.core.playback.fade

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A validated total fade-through duration. The fade-out and fade-in each use half of it. */
@JvmInline
value class FadeThroughDuration private constructor(
    val milliseconds: Long,
) {
    companion object {
        const val MIN_MILLISECONDS: Long = 0
        const val MAX_MILLISECONDS: Long = 2_000
        const val STEP_MILLISECONDS: Long = 250

        fun of(milliseconds: Long): FadeThroughDuration {
            require(milliseconds in MIN_MILLISECONDS..MAX_MILLISECONDS) {
                "Fade-through duration must be between 0 and 2000 ms"
            }
            require(milliseconds % STEP_MILLISECONDS == 0L) {
                "Fade-through duration must use 250 ms steps"
            }
            return FadeThroughDuration(milliseconds)
        }
    }
}

enum class FadeSwitchReason {
    NATURAL_END,
    MANUAL_PREVIOUS,
    MANUAL_NEXT,
}

enum class FadePlaybackEvent {
    PAUSE,
    RESUME,
    SEEK,
    AUDIO_FOCUS_LOSS,
    AUDIO_FOCUS_GAIN,
    PRIVATE_OUTPUT_LOST,
}

/** Platform bridge owned by the single player service. */
interface FadeThroughOutput<T : Any> {
    fun setVolume(volume: Float)

    /** Returns true only after [target] has become the active player item. */
    fun switchTo(target: T): Boolean

    fun requestPause()
}

fun interface FadeScheduler {
    suspend fun wait(milliseconds: Long)
}

object CoroutineFadeScheduler : FadeScheduler {
    override suspend fun wait(milliseconds: Long) {
        if (milliseconds > 0) delay(milliseconds)
    }
}

/**
 * Coordinates a fade-out, one target switch, and a fade-in for a single player.
 *
 * A target received during an active ramp replaces the pending target. If it arrives after the
 * current switch, it starts one follow-up transition after the current fade-in completes.
 */
class FadeThroughCoordinator<T : Any>(
    private val scope: CoroutineScope,
    private val output: FadeThroughOutput<T>,
    private val durationProvider: suspend () -> FadeThroughDuration,
    private val scheduler: FadeScheduler = CoroutineFadeScheduler,
) {
    private val lock = Any()
    private var generation: Long = 0
    private var pendingTarget: PendingTarget<T>? = null
    private var transitionJob: Job? = null
    private var mutedAfterFailure: Boolean = false
    private var disposed: Boolean = false

    fun requestSwitch(target: T, reason: FadeSwitchReason) {
        val jobToStart = synchronized(lock) {
            if (disposed || !scope.isActive) return@synchronized null
            pendingTarget = PendingTarget(target, reason)
            startTransitionLocked()
        }
        jobToStart?.start()
    }

    /** Resume, seek and focus gain do not start or modify a transition. */
    fun onPlaybackEvent(event: FadePlaybackEvent) {
        when (event) {
            FadePlaybackEvent.PAUSE,
            FadePlaybackEvent.AUDIO_FOCUS_LOSS,
            FadePlaybackEvent.PRIVATE_OUTPUT_LOST,
            -> interruptAndPause()

            FadePlaybackEvent.RESUME,
            FadePlaybackEvent.SEEK,
            FadePlaybackEvent.AUDIO_FOCUS_GAIN,
            -> Unit
        }
    }

    /** Marks a decode failure after a switch so recovery can stay silent until a good target is active. */
    fun onTargetFailure() {
        val job = synchronized(lock) {
            if (disposed) return
            generation += 1
            pendingTarget = null
            mutedAfterFailure = true
            transitionJob.also { transitionJob = null }
        }
        job?.cancel()
        synchronized(lock) {
            if (!disposed) output.setVolume(MUTED_VOLUME)
        }
    }

    /** Cancels owned work and restores unity volume without issuing a playback command. */
    fun dispose() {
        val job = synchronized(lock) {
            if (disposed) return
            disposed = true
            generation += 1
            pendingTarget = null
            mutedAfterFailure = false
            transitionJob.also { transitionJob = null }
        }
        job?.cancel()
        synchronized(lock) { output.setVolume(UNITY_VOLUME) }
    }

    private fun interruptAndPause() {
        val job = synchronized(lock) {
            if (disposed) return
            generation += 1
            pendingTarget = null
            mutedAfterFailure = false
            transitionJob.also { transitionJob = null }
        }
        job?.cancel()
        synchronized(lock) {
            if (!disposed) {
                output.setVolume(UNITY_VOLUME)
                output.requestPause()
            }
        }
    }

    private fun startTransitionLocked(): Job? {
        if (transitionJob?.isActive == true || pendingTarget == null) return null
        val transitionGeneration = ++generation
        val startsMuted = mutedAfterFailure
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runTransition(transitionGeneration, startsMuted)
        }
        transitionJob = job
        return job
    }

    private suspend fun runTransition(
        transitionGeneration: Long,
        startsMuted: Boolean,
    ) {
        var leaveMuted = false
        var cancelled = false
        try {
            // Settings are sampled exactly once for this transition.
            val duration = durationProvider()
            if (duration.milliseconds == 0L) {
                val switched = switchLatestTarget(transitionGeneration)
                leaveMuted = switched == false
                if (switched == false) {
                    setVolumeIfCurrent(transitionGeneration, MUTED_VOLUME)
                } else if (switched == true) {
                    setVolumeIfCurrent(transitionGeneration, UNITY_VOLUME)
                }
                return
            }

            val halfDurationMs = duration.milliseconds / 2
            if (startsMuted) {
                if (!setVolumeIfCurrent(transitionGeneration, MUTED_VOLUME)) return
            } else if (!rampVolume(transitionGeneration, UNITY_VOLUME, MUTED_VOLUME, halfDurationMs)) {
                return
            }

            val switched = switchLatestTarget(transitionGeneration)
            if (switched != true) {
                if (switched == false) {
                    leaveMuted = true
                    setVolumeIfCurrent(transitionGeneration, MUTED_VOLUME)
                }
                return
            }
            rampVolume(transitionGeneration, MUTED_VOLUME, UNITY_VOLUME, halfDurationMs)
        } catch (exception: CancellationException) {
            cancelled = true
            throw exception
        } finally {
            finishTransition(transitionGeneration, leaveMuted, cancelled)
        }
    }

    private suspend fun rampVolume(
        transitionGeneration: Long,
        from: Float,
        to: Float,
        durationMs: Long,
    ): Boolean {
        var previousElapsedMs = 0L
        for (step in 1..RAMP_STEPS) {
            val elapsedMs = durationMs * step / RAMP_STEPS
            scheduler.wait(elapsedMs - previousElapsedMs)
            previousElapsedMs = elapsedMs
            val progress = step.toFloat() / RAMP_STEPS
            val volume = from + ((to - from) * progress)
            if (!setVolumeIfCurrent(transitionGeneration, volume)) return false
        }
        return true
    }

    /** Null means the transition was invalidated before a target could be switched. */
    private fun switchLatestTarget(transitionGeneration: Long): Boolean? = synchronized(lock) {
        if (!isCurrentLocked(transitionGeneration)) return@synchronized null
        val target = pendingTarget?.value ?: return@synchronized null
        pendingTarget = null
        try {
            output.switchTo(target)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun setVolumeIfCurrent(transitionGeneration: Long, volume: Float): Boolean =
        synchronized(lock) {
            if (!isCurrentLocked(transitionGeneration)) return@synchronized false
            output.setVolume(volume.coerceIn(MUTED_VOLUME, UNITY_VOLUME))
            true
        }

    private fun finishTransition(
        transitionGeneration: Long,
        leaveMuted: Boolean,
        cancelled: Boolean,
    ) {
        var jobToStart: Job? = null
        var restoreVolume = false
        synchronized(lock) {
            if (!isCurrentLocked(transitionGeneration)) return
            transitionJob = null
            if (cancelled) {
                pendingTarget = null
                mutedAfterFailure = false
                restoreVolume = true
            } else {
                mutedAfterFailure = leaveMuted
                jobToStart = startTransitionLocked()
            }
        }
        if (restoreVolume) {
            synchronized(lock) {
                if (!disposed && generation == transitionGeneration) output.setVolume(UNITY_VOLUME)
            }
        }
        jobToStart?.start()
    }

    private fun isCurrentLocked(transitionGeneration: Long): Boolean =
        !disposed && generation == transitionGeneration

    private data class PendingTarget<T : Any>(
        val value: T,
        val reason: FadeSwitchReason,
    )

    private companion object {
        const val RAMP_STEPS: Int = 10
        const val MUTED_VOLUME: Float = 0f
        const val UNITY_VOLUME: Float = 1f
    }
}
