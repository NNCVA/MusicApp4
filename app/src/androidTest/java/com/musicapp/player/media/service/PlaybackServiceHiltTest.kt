package com.musicapp.player.media.service

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
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
        assertNotNull(callbackFactory)
    }
}
