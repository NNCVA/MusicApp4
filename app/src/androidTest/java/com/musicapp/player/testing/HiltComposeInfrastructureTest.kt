package com.musicapp.player.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.musicapp.player.core.system.AppClock
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HiltComposeInfrastructureTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val composeRule = createAndroidComposeRule<HiltTestActivity>()

  @Inject lateinit var clock: AppClock

  @Before
  fun inject() {
    hiltRule.inject()
  }

  @Test
  fun testApplication_injectsFakeAndHostsCompose() {
    assertEquals(42L, clock.currentTimeMillis())

    composeRule.setContent { Box(Modifier.testTag("wave0-compose-root")) }
    composeRule.onNodeWithTag("wave0-compose-root").assertExists()
  }
}
