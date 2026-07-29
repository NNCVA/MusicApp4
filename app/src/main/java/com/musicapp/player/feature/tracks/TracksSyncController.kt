package com.musicapp.player.feature.tracks

import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.sync.LibrarySyncState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

interface TracksSyncController {
    val state: StateFlow<LibrarySyncState>

    fun requestManualSync()

    fun acknowledgeFeedback(eventId: Long)
}

@Singleton
class DefaultTracksSyncController @Inject constructor(
    private val coordinator: LibrarySyncCoordinator,
) : TracksSyncController {
    override val state: StateFlow<LibrarySyncState>
        get() = coordinator.state

    override fun requestManualSync() = coordinator.requestManualSync()

    override fun acknowledgeFeedback(eventId: Long) = coordinator.acknowledgeFeedback(eventId)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TracksSyncModule {
    @Binds
    @Singleton
    abstract fun bindTracksSyncController(implementation: DefaultTracksSyncController): TracksSyncController
}
