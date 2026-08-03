package com.musicapp.player.core.playback.fade

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FadeThroughCoordinatorTest {
    @Test
    fun `duration accepts zero through two seconds in 250 millisecond steps`() {
        assertEquals(0L, FadeThroughDuration.of(0).milliseconds)
        assertEquals(2_000L, FadeThroughDuration.of(2_000).milliseconds)
        assertThrows(IllegalArgumentException::class.java) { FadeThroughDuration.of(-250) }
        assertThrows(IllegalArgumentException::class.java) { FadeThroughDuration.of(2_250) }
        assertThrows(IllegalArgumentException::class.java) { FadeThroughDuration.of(125) }
    }

    @Test
    fun `transition fades symmetrically around exactly one target switch`() = runTest {
        val output = RecordingOutput()
        val scheduler = RecordingScheduler()
        val coordinator = coordinator(output, scheduler, FadeThroughDuration.of(500))

        coordinator.requestSwitch("next", FadeSwitchReason.MANUAL_NEXT)
        advanceUntilIdle()

        assertEquals(listOf("next"), output.switchedTargets)
        assertEquals(20, output.volumes.size)
        assertEquals(0f, output.volumes[9], FLOAT_TOLERANCE)
        assertEquals(0.1f, output.volumes[10], FLOAT_TOLERANCE)
        assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
        assertEquals(500L, scheduler.totalWaitMs)
        assertTrue(output.actions.indexOf("switch:next") > output.actions.indexOf("volume:0.0"))
    }

    @Test
    fun `zero duration switches immediately without scheduling a ramp`() = runTest {
        val output = RecordingOutput()
        val scheduler = RecordingScheduler()
        val coordinator = coordinator(output, scheduler, FadeThroughDuration.of(0))

        coordinator.requestSwitch("next", FadeSwitchReason.NATURAL_END)
        runCurrent()

        assertEquals(listOf("next"), output.switchedTargets)
        assertEquals(0L, scheduler.totalWaitMs)
        assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
    }

    @Test
    fun `natural end previous and next are accepted switch reasons`() = runTest {
        val output = RecordingOutput()
        val coordinator = coordinator(output, RecordingScheduler(), FadeThroughDuration.of(0))

        FadeSwitchReason.entries.forEach { reason ->
            coordinator.requestSwitch(reason.name, reason)
            advanceUntilIdle()
        }

        assertEquals(FadeSwitchReason.entries.map { it.name }, output.switchedTargets)
    }

    @Test
    fun `resume seek and focus gain never start a transition`() = runTest {
        val output = RecordingOutput()
        val coordinator = coordinator(output, RecordingScheduler(), FadeThroughDuration.of(500))

        coordinator.onPlaybackEvent(FadePlaybackEvent.RESUME)
        coordinator.onPlaybackEvent(FadePlaybackEvent.SEEK)
        coordinator.onPlaybackEvent(FadePlaybackEvent.AUDIO_FOCUS_GAIN)
        advanceUntilIdle()

        assertTrue(output.actions.isEmpty())
    }

    @Test
    fun `latest target wins during fade out without restarting the ramp`() = runTest {
        val output = RecordingOutput()
        val scheduler = RecordingScheduler()
        val coordinator = coordinator(output, scheduler, FadeThroughDuration.of(250))

        coordinator.requestSwitch("first", FadeSwitchReason.MANUAL_NEXT)
        runCurrent()
        coordinator.requestSwitch("latest", FadeSwitchReason.MANUAL_NEXT)
        advanceUntilIdle()

        assertEquals(listOf("latest"), output.switchedTargets)
        assertEquals(20, output.volumes.size)
        assertEquals(250L, scheduler.totalWaitMs)
    }

    @Test
    fun `pause focus loss and private output loss cancel pending targets and pause`() = runTest {
        listOf(
            FadePlaybackEvent.PAUSE,
            FadePlaybackEvent.AUDIO_FOCUS_LOSS,
            FadePlaybackEvent.PRIVATE_OUTPUT_LOST,
        ).forEach { interruption ->
            val output = RecordingOutput()
            val coordinator = coordinator(output, RecordingScheduler(), FadeThroughDuration.of(500))
            coordinator.requestSwitch("cancelled", FadeSwitchReason.MANUAL_NEXT)
            runCurrent()

            coordinator.onPlaybackEvent(interruption)
            advanceUntilIdle()

            assertTrue(output.switchedTargets.isEmpty())
            assertEquals(1, output.pauseRequests)
            assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
        }
    }

    @Test
    fun `duration changes apply only to the next transition`() = runTest {
        val output = RecordingOutput()
        val scheduler = RecordingScheduler()
        var duration = FadeThroughDuration.of(500)
        var reads = 0
        val coordinator = FadeThroughCoordinator(
            scope = this,
            output = output,
            durationProvider = {
                reads += 1
                duration
            },
            scheduler = scheduler,
        )

        coordinator.requestSwitch("first", FadeSwitchReason.MANUAL_NEXT)
        runCurrent()
        duration = FadeThroughDuration.of(2_000)
        advanceUntilIdle()
        assertEquals(500L, scheduler.totalWaitMs)

        coordinator.requestSwitch("second", FadeSwitchReason.MANUAL_NEXT)
        advanceUntilIdle()
        assertEquals(2_500L, scheduler.totalWaitMs)
        assertEquals(2, reads)
    }

    @Test
    fun `failed target remains muted and successful recovery only fades in`() = runTest {
        val output = RecordingOutput { target -> target != "broken" }
        val scheduler = RecordingScheduler()
        val coordinator = coordinator(output, scheduler, FadeThroughDuration.of(250))

        coordinator.requestSwitch("broken", FadeSwitchReason.NATURAL_END)
        advanceUntilIdle()
        assertEquals(0f, output.volumes.last(), FLOAT_TOLERANCE)

        val volumeCountAfterFailure = output.volumes.size
        coordinator.requestSwitch("recovery", FadeSwitchReason.MANUAL_NEXT)
        advanceUntilIdle()

        assertEquals(listOf("broken", "recovery"), output.switchedTargets)
        assertEquals(0f, output.volumes[volumeCountAfterFailure], FLOAT_TOLERANCE)
        assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
        assertEquals(10, output.volumes.size - volumeCountAfterFailure - 1)
    }

    @Test
    fun `decode failure after a switch cancels stale fade in and keeps recovery muted`() = runTest {
        val output = RecordingOutput()
        val coordinator = coordinator(output, RecordingScheduler(), FadeThroughDuration.of(500))
        coordinator.requestSwitch("decoded-bad", FadeSwitchReason.NATURAL_END)
        runCurrent()

        coordinator.onTargetFailure()
        runCurrent()
        coordinator.requestSwitch("recovered", FadeSwitchReason.NATURAL_END)
        advanceUntilIdle()

        assertEquals(listOf("recovered"), output.switchedTargets)
        assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
    }

    @Test
    fun `scope cancellation restores volume and prevents stale callbacks`() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val output = RecordingOutput()
        val coordinator = FadeThroughCoordinator(
            scope = owner,
            output = output,
            durationProvider = { FadeThroughDuration.of(500) },
            scheduler = RecordingScheduler(),
        )
        coordinator.requestSwitch("cancelled", FadeSwitchReason.MANUAL_NEXT)
        runCurrent()

        owner.cancel()
        runCurrent()
        val actionsAfterCancellation = output.actions.toList()
        advanceUntilIdle()

        assertTrue(output.switchedTargets.isEmpty())
        assertEquals(1f, output.volumes.last(), FLOAT_TOLERANCE)
        assertEquals(actionsAfterCancellation, output.actions)
        coordinator.requestSwitch("stale", FadeSwitchReason.MANUAL_NEXT)
        advanceUntilIdle()
        assertTrue(output.switchedTargets.isEmpty())
    }

    private fun TestScope.coordinator(
        output: RecordingOutput,
        scheduler: RecordingScheduler,
        duration: FadeThroughDuration,
    ): FadeThroughCoordinator<String> = FadeThroughCoordinator(
        scope = this,
        output = output,
        durationProvider = { duration },
        scheduler = scheduler,
    )

    private companion object {
        const val FLOAT_TOLERANCE = 0.0001f
    }
}

private class RecordingScheduler : FadeScheduler {
    var totalWaitMs: Long = 0
        private set

    override suspend fun wait(milliseconds: Long) {
        totalWaitMs += milliseconds
        delay(milliseconds)
    }
}

private class RecordingOutput(
    private val switchResult: (String) -> Boolean = { true },
) : FadeThroughOutput<String> {
    val actions = mutableListOf<String>()
    val volumes = mutableListOf<Float>()
    val switchedTargets = mutableListOf<String>()
    var pauseRequests: Int = 0
        private set

    override fun setVolume(volume: Float) {
        volumes += volume
        actions += "volume:$volume"
    }

    override fun switchTo(target: String): Boolean {
        switchedTargets += target
        actions += "switch:$target"
        return switchResult(target)
    }

    override fun requestPause() {
        pauseRequests += 1
        actions += "pause"
    }
}
