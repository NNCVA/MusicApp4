package com.musicapp.player.data.mediastore

import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import com.musicapp.player.data.sync.MediaStoreSnapshot
import com.musicapp.player.data.sync.MediaStoreSnapshotSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMediaStoreChangeSourceTest {
    @Test
    fun currentVolumesAndAggregateUriAreRegisteredOnceEach() = runTest {
        val registry = RecordingObserverRegistry()
        val source = source(registry, setOf("external_primary", "card", "external"))

        val collection = backgroundScope.launch { source.changes().collect() }
        runCurrent()

        assertEquals(
            setOf(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.getContentUri("external_primary"),
                MediaStore.Audio.Media.getContentUri("card"),
            ),
            registry.registrations.map { it.first }.toSet(),
        )
        assertEquals(3, registry.registrations.size)
        collection.cancelAndJoin()
    }

    @Test
    fun emptyVolumeSnapshotStillRegistersAggregateUri() = runTest {
        val registry = RecordingObserverRegistry()
        val source = source(registry, emptySet())

        val collection = backgroundScope.launch { source.changes().collect() }
        runCurrent()

        assertEquals(
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
            registry.registrations.map { it.first },
        )
        collection.cancelAndJoin()
    }

    @Test
    fun cancellingFlowUnregistersSharedObserverExactlyOnce() = runTest {
        val registry = RecordingObserverRegistry()
        val source = source(registry, setOf("external_primary", "card"))
        val collection = backgroundScope.launch { source.changes().collect() }
        runCurrent()
        val registeredObserver = registry.registrations.first().second
        registry.registrations.forEach { assertSame(registeredObserver, it.second) }

        collection.cancelAndJoin()
        runCurrent()

        assertEquals(listOf(registeredObserver), registry.unregistrations)
    }

    private fun source(
        registry: RecordingObserverRegistry,
        volumes: Set<String>,
    ) = AndroidMediaStoreChangeSource(
        observerRegistry = registry,
        snapshotSource = MediaStoreSnapshotSource {
            MediaStoreSnapshot(volumes, volumes.associateWith { "version-$it" })
        },
    )

    private class RecordingObserverRegistry : MediaStoreObserverRegistry {
        val registrations = mutableListOf<Pair<Uri, ContentObserver>>()
        val unregistrations = mutableListOf<ContentObserver>()

        override fun register(uri: Uri, observer: ContentObserver) {
            registrations += uri to observer
        }

        override fun unregister(observer: ContentObserver) {
            unregistrations += observer
        }
    }
}
