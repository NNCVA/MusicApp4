package com.musicapp.player.testing

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemSourcesTest {
  @Test
  fun fakeClock_exposesControlledWallAndElapsedTime() {
    val clock = FakeAppClock(wallTimeMillis = 1_000L, elapsedTimeMillis = 250L)

    assertEquals(1_000L, clock.currentTimeMillis())
    assertEquals(250L, clock.elapsedRealtimeMillis())
  }

  @Test
  fun fakeRandomSource_returnsScriptedValues() {
    val random = FakeRandomSource(listOf(2, 0))

    assertEquals(2, random.nextInt(3))
    assertEquals(0, random.nextInt(1))
  }

  @Test
  fun turbine_observesStateFlowUpdatesWithVirtualTime() = runTest {
    val state = MutableStateFlow("ready")

    state.test {
      assertEquals("ready", awaitItem())
      state.value = "running"
      assertEquals("running", awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
