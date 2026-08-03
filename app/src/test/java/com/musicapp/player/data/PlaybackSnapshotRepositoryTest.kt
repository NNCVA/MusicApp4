package com.musicapp.player.data

import com.musicapp.player.core.domain.model.PlaybackInstance
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.repository.RoomPlaybackSnapshotRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackSnapshotRepositoryTest {
    private lateinit var database: com.musicapp.player.data.local.MusicDatabase
    private lateinit var repository: RoomPlaybackSnapshotRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository = RoomPlaybackSnapshotRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun completeSnapshotRoundTripsWithoutLosingQueueItemIdentity() = runTest {
        val first = QueueItem(QueueItemId(101), track(mediaStoreId = 1).id)
        val current = QueueItem(QueueItemId(202), track(volumeName = "card", mediaStoreId = 1).id)
        val snapshot = PlaybackSnapshot(
            queue = PlaybackQueue(
                originalQueue = listOf(first, current),
                stableShuffleSequence = listOf(current.id, first.id),
                currentItemId = current.id,
                shuffleRound = 4,
                shuffleCursor = 0,
            ),
            positionMs = 12_345,
            playbackMode = PlaybackMode.SHUFFLE,
            playbackInstance = PlaybackInstance(
                queueItemId = current.id,
                trackId = current.trackId,
                startedAtMs = 500,
                actualPlayedDurationMs = 6_789,
                historyRecorded = true,
            ),
            updatedAtMs = 20_000,
            playbackResumptionAllowed = false,
        )

        repository.saveSnapshot(snapshot)

        assertEquals(snapshot, repository.getSnapshot())
    }

    @Test
    fun corruptPlaybackInstanceCannotBindToDifferentQueueItemId() = runTest {
        database.playbackSnapshotDao().upsert(
            PlaybackSnapshotEntity(
                originalQueueJson = """[{"queueItemId":1,"volumeName":"external_primary","mediaStoreId":1}]""",
                stableShuffleSequenceJson = "[]",
                currentQueueItemId = 1,
                shuffleRound = 0,
                shuffleCursor = null,
                positionMs = 1,
                playbackMode = PlaybackMode.LIST_REPEAT.name,
                playbackInstanceJson =
                    """{"queueItemId":2,"volumeName":"external_primary","mediaStoreId":1,"startedAtMs":0,"actualPlayedDurationMs":0,"historyRecorded":false}""",
                updatedAtMs = 1,
                playbackResumptionAllowed = true,
            ),
        )

        val failure = runCatching { repository.getSnapshot() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
