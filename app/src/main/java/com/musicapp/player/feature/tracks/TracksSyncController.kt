package com.musicapp.player.feature.tracks

import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.LibrarySyncCoordinator
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface TracksSyncController {
    val state: StateFlow<LibrarySyncState>

    fun requestManualSync()

    fun acknowledgeFeedback(eventId: Long)
}

@Singleton
class DefaultTracksSyncController @Inject constructor(
    private val coordinator: LibrarySyncCoordinator,
    private val settingsRepository: SettingsRepository,
    @ApplicationCoroutineScope private val applicationScope: CoroutineScope,
) : TracksSyncController {
    override val state: StateFlow<LibrarySyncState>
        get() = coordinator.state

    override fun requestManualSync() {
        val capturedPending = settingsRepository.pendingLibrarySync.value
        applicationScope.launch {
            val event = coordinator.requestManualSyncAndAwait()
            if (capturedPending.isPending && event is LibrarySyncEvent.Completed) {
                settingsRepository.clearLibrarySyncPending(capturedPending.revision)
            }
        }
    }

    override fun acknowledgeFeedback(eventId: Long) = coordinator.acknowledgeFeedback(eventId)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TracksSyncModule {
    @Binds
    @Singleton
    abstract fun bindTracksSyncController(implementation: DefaultTracksSyncController): TracksSyncController
}
