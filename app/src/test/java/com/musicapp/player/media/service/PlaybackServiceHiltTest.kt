package com.musicapp.player.media.service

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class PlaybackServiceHiltTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var callbackFactory: MusicLibrarySessionCallbackFactory

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun serviceCallbackFactoryIsAvailableFromTheRealHiltGraph() {
        assertNotNull(callbackFactory.create())
    }
}
