package com.musicapp.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationStartupIntegrationTest {
    @Test
    fun applicationResourcesAreAvailableInAndroidRuntime() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("MusicApp", context.getString(R.string.app_name))
    }
}
