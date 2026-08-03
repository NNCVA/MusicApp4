package com.musicapp.player.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.domain.policy.PathRuleMatcher
import com.musicapp.player.core.media.AdmissionResult
import com.musicapp.player.core.media.AudioAdmissionPolicy
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.CompleteMediaLibraryScan
import com.musicapp.player.data.sync.MediaLibraryScanSkipReason
import com.musicapp.player.data.sync.MediaLibraryScanSource
import com.musicapp.player.data.sync.MediaLibraryScanSummary
import com.musicapp.player.data.sync.MediaStoreChangeSource
import com.musicapp.player.data.sync.MediaStoreSnapshot
import com.musicapp.player.data.sync.MediaStoreSnapshotSource
import com.musicapp.player.data.sync.SkippedMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

@Singleton
class AndroidMediaLibraryScanSource @Inject constructor(
    private val queryAdapter: MediaStoreQueryAdapter,
    private val snapshotSource: MediaStoreSnapshotSource,
    private val settingsRepository: SettingsRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
) : MediaLibraryScanSource {
    override suspend fun queryMountedAudio(): CompleteMediaLibraryScan {
        val settingsSnapshot = settingsRepository.currentSettings()
        val pathRulesSnapshot = mediaLibraryRepository.observePathRules().first()
        val platformSnapshot = snapshotSource.currentSnapshot()
        val queryResult = queryAdapter.queryAudioWithReport()
        val accepted = mutableListOf<MediaAudioCandidate>()
        val skipped = queryResult.unreadableDisplayNames.mapTo(mutableListOf()) {
            SkippedMediaItem(it, MediaLibraryScanSkipReason.UNREADABLE_ITEM)
        }
        val seenIdentities = mutableSetOf<TrackId>()

        queryResult.candidates.forEach { candidate ->
            val admission = AudioAdmissionPolicy.evaluate(candidate)
            val skipReason = when {
                admission != AdmissionResult.ACCEPTED -> admission.toSkipReason()
                !PathRuleMatcher.matches(
                    candidate.volumeName,
                    candidate.relativeDirectory,
                    settingsSnapshot.scanMode,
                    pathRulesSnapshot,
                ) -> pathSkipReason(candidate.volumeName, candidate.relativeDirectory, pathRulesSnapshot)
                !seenIdentities.add(candidate.id) -> MediaLibraryScanSkipReason.DUPLICATE_IDENTITY
                else -> null
            }
            if (skipReason == null) {
                accepted += candidate
            } else {
                skipped += SkippedMediaItem(candidate.displayName, skipReason)
            }
        }

        val mountedVolumes = platformSnapshot.mountedVolumeNames + accepted.map { it.volumeName }
        val signatures = mountedVolumes.associateWith { platformSnapshot.volumeSignatures[it] }
        return CompleteMediaLibraryScan(
            mountedVolumeNames = mountedVolumes,
            candidates = accepted,
            volumeSignatures = signatures,
            summary = MediaLibraryScanSummary(
                queriedCandidateCount = queryResult.candidates.size + queryResult.unreadableDisplayNames.size,
                acceptedCandidates = accepted.toList(),
                skippedItems = skipped.toList(),
            ),
        )
    }

    private fun pathSkipReason(
        volumeName: String,
        relativeDirectory: String,
        rules: List<PathRule>,
    ): MediaLibraryScanSkipReason {
        val excluded = rules.filter { it.kind == PathRuleKind.EXCLUDE }
        return if (
            !PathRuleMatcher.matches(
                volumeName,
                relativeDirectory,
                ScanMode.ALL,
                excluded,
            )
        ) {
            MediaLibraryScanSkipReason.EXCLUDED_PATH
        } else {
            MediaLibraryScanSkipReason.OUTSIDE_INCLUDED_PATHS
        }
    }
}

private fun AdmissionResult.toSkipReason(): MediaLibraryScanSkipReason = when (this) {
    AdmissionResult.ACCEPTED -> error("accepted items do not have a skip reason")
    AdmissionResult.REJECTED_UNSUPPORTED_FORMAT -> MediaLibraryScanSkipReason.UNSUPPORTED_FORMAT
    AdmissionResult.REJECTED_NON_POSITIVE_DURATION -> MediaLibraryScanSkipReason.NON_POSITIVE_DURATION
    AdmissionResult.REJECTED_SYSTEM_AUDIO -> MediaLibraryScanSkipReason.SYSTEM_AUDIO
}

@Singleton
class AndroidMediaStoreSnapshotSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaStoreSnapshotSource {
    override fun currentSnapshot(): MediaStoreSnapshot {
        val volumes = mountedExternalVolumes(context)
        val globalVersion = MediaStore.getVersion(context)
        val signatures = volumes.associateWith { volumeName ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.getVersion(context, volumeName)
            } else {
                globalVersion
            }
        }
        return MediaStoreSnapshot(volumes, signatures)
    }
}

@Singleton
class AndroidMediaStoreChangeSource @Inject constructor(
    private val observerRegistry: MediaStoreObserverRegistry,
    private val snapshotSource: MediaStoreSnapshotSource,
) : MediaStoreChangeSource {
    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val volumes = runCatching { snapshotSource.currentSnapshot().mountedVolumeNames }
            .getOrDefault(emptySet())
        val observedUris = buildSet {
            add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            volumes.forEach { volumeName ->
                add(MediaStore.Audio.Media.getContentUri(volumeName))
            }
        }
        observedUris.forEach { uri -> observerRegistry.register(uri, observer) }
        awaitClose { observerRegistry.unregister(observer) }
    }
}

interface MediaStoreObserverRegistry {
    fun register(uri: Uri, observer: ContentObserver)

    fun unregister(observer: ContentObserver)
}

@Singleton
class ContentResolverMediaStoreObserverRegistry @Inject constructor(
    private val contentResolver: ContentResolver,
) : MediaStoreObserverRegistry {
    override fun register(uri: Uri, observer: ContentObserver) {
        contentResolver.registerContentObserver(uri, true, observer)
    }

    override fun unregister(observer: ContentObserver) {
        contentResolver.unregisterContentObserver(observer)
    }
}

private fun mountedExternalVolumes(context: Context): Set<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.getExternalVolumeNames(context)
    } else {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val mounted = storageManager.storageVolumes.any { volume ->
            volume.state == android.os.Environment.MEDIA_MOUNTED ||
                volume.state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY
        }
        if (mounted) setOf(LEGACY_EXTERNAL_VOLUME_NAME) else emptySet()
    }

private const val LEGACY_EXTERNAL_VOLUME_NAME = "external"
