package com.musicapp.player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.musicapp.player.data.local.MusicDatabase
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MusicApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var database: Lazy<MusicDatabase>

    override fun onCreate() {
        super.onCreate()
        if (!isUnitTestRuntime()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    database.get().openHelper.readableDatabase
                }
            }
        }
    }

    private fun isUnitTestRuntime(): Boolean =
        System.getProperty("robolectric.version") != null ||
            runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
