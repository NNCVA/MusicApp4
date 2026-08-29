package com.musicapp.player.data.mediastore

import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.data.sync.MediaLibraryScanSkipReason
import com.musicapp.player.data.sync.MediaStoreSnapshot
import com.musicapp.player.data.sync.MediaStoreSnapshotSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidMediaLibraryScanSourceTest {
    @Test
    fun scanUsesOneSettingsAndRuleSnapshotAndReportsEverySkipReason() = runTest {
        val rules = listOf(
            PathRule(PathRuleId(1), PRIMARY, "Music/Blocked", PathRuleKind.EXCLUDE),
            PathRule(PathRuleId(2), PRIMARY, "Music", PathRuleKind.INCLUDE),
        )
        val candidates = listOf(
            candidate(1, "Music/Kept"),
            candidate(2, "Music/Kept", mimeType = "audio/aiff"),
            candidate(3, "Music/Kept", durationMs = 0),
            candidate(4, "Music/Kept", isRingtone = true),
            candidate(5, "Music/Blocked"),
            candidate(6, "Podcasts"),
            candidate(1, "Music/Kept"),
        )
        val source = source(
            queryResult = MediaStoreQueryResult(candidates, listOf("broken.mp3")),
            settings = AppSettings(scanMode = ScanMode.SELECTED_DIRECTORIES),
            rules = rules,
        )

        val scan = source.queryMountedAudio()

        assertEquals(listOf(1L), scan.candidates.map { it.mediaStoreId })
        assertEquals(8, scan.summary.queriedCandidateCount)
        assertEquals(
            setOf(
                MediaLibraryScanSkipReason.UNSUPPORTED_FORMAT,
                MediaLibraryScanSkipReason.NON_POSITIVE_DURATION,
                MediaLibraryScanSkipReason.SYSTEM_AUDIO,
                MediaLibraryScanSkipReason.EXCLUDED_PATH,
                MediaLibraryScanSkipReason.OUTSIDE_INCLUDED_PATHS,
                MediaLibraryScanSkipReason.DUPLICATE_IDENTITY,
                MediaLibraryScanSkipReason.UNREADABLE_ITEM,
            ),
            scan.summary.skippedItems.map { it.reason }.toSet(),
        )
    }

    @Test
    fun scanAllStillAppliesExclusionsAndCarriesMountedEmptyVolumesAndSignatures() = runTest {
        val source = source(
            queryResult = MediaStoreQueryResult(
                listOf(candidate(1, "Music"), candidate(2, "Blocked")),
                emptyList(),
            ),
            settings = AppSettings(scanMode = ScanMode.ALL),
            rules = listOf(PathRule(PathRuleId(1), PRIMARY, "Blocked", PathRuleKind.EXCLUDE)),
            snapshot = MediaStoreSnapshot(
                mountedVolumeNames = setOf(PRIMARY, CARD),
                volumeSignatures = mapOf(PRIMARY to "v1", CARD to "v2"),
            ),
        )

        val scan = source.queryMountedAudio()

        assertEquals(setOf(PRIMARY, CARD), scan.mountedVolumeNames)
        assertEquals(mapOf(PRIMARY to "v1", CARD to "v2"), scan.volumeSignatures)
        assertEquals(listOf(1L), scan.candidates.map { it.mediaStoreId })
    }

    @Test
    fun scanUsesCurrentSettingsSnapshotInsteadOfStateFlowInitialValue() = runTest {
        val queryResult = MediaStoreQueryResult(
            listOf(candidate(1, "Music"), candidate(2, "Podcasts")),
            emptyList(),
        )
        val repository = FakeSettingsRepository(
            observedInitial = AppSettings(scanMode = ScanMode.ALL),
            current = AppSettings(scanMode = ScanMode.SELECTED_DIRECTORIES),
        )
        val source = AndroidMediaLibraryScanSource(
            queryAdapter = object : MediaStoreQueryAdapter {
                override fun queryAudio(): List<MediaAudioCandidate> = queryResult.candidates
                override fun queryAudioWithReport(): MediaStoreQueryResult = queryResult
            },
            snapshotSource = MediaStoreSnapshotSource {
                MediaStoreSnapshot(setOf(PRIMARY), mapOf(PRIMARY to "v1"))
            },
            settingsRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(
                initialRules = listOf(
                    PathRule(PathRuleId(1), PRIMARY, "Music", PathRuleKind.INCLUDE),
                ),
            ),
        )

        val scan = source.queryMountedAudio()

        assertEquals(ScanMode.ALL, repository.settings.value.scanMode)
        assertEquals(listOf(1L), scan.candidates.map { it.mediaStoreId })
        assertEquals(
            MediaLibraryScanSkipReason.OUTSIDE_INCLUDED_PATHS,
            scan.summary.skippedItems.single().reason,
        )
    }

    private fun source(
        queryResult: MediaStoreQueryResult,
        settings: AppSettings,
        rules: List<PathRule>,
        snapshot: MediaStoreSnapshot = MediaStoreSnapshot(
            mountedVolumeNames = setOf(PRIMARY),
            volumeSignatures = mapOf(PRIMARY to "v1"),
        ),
    ) = AndroidMediaLibraryScanSource(
        queryAdapter = object : MediaStoreQueryAdapter {
            override fun queryAudio(): List<MediaAudioCandidate> = queryResult.candidates
            override fun queryAudioWithReport(): MediaStoreQueryResult = queryResult
        },
        snapshotSource = MediaStoreSnapshotSource { snapshot },
        settingsRepository = FakeSettingsRepository(settings, settings),
        mediaLibraryRepository = FakeMediaLibraryRepository(initialRules = rules),
    )

    private class FakeSettingsRepository(
        observedInitial: AppSettings,
        private val current: AppSettings,
    ) : SettingsRepository {
        override val settings = MutableStateFlow(observedInitial)
        override suspend fun currentSettings(): AppSettings = current
        override suspend fun setColorSource(value: com.musicapp.player.core.domain.model.ColorSource) = Unit
        override suspend fun setPresetTheme(value: com.musicapp.player.core.domain.model.PresetTheme) = Unit
        override suspend fun setThemeMode(value: com.musicapp.player.core.domain.model.ThemeMode) = Unit
        override suspend fun setAppLanguage(value: com.musicapp.player.core.domain.model.AppLanguage) = Unit
        override suspend fun setAeroMode(value: com.musicapp.player.core.domain.model.AeroMode) = Unit
        override suspend fun setFadeThroughDurationMs(value: Long) = Unit
        override suspend fun setScanMode(value: ScanMode) = Unit
        override suspend fun setSkipShortAudio(value: Boolean) = Unit
        override suspend fun setAlbumGridColumns(value: Int) = Unit
        override suspend fun reset() = Unit
    }

    private fun candidate(
        id: Long,
        directory: String,
        mimeType: String = "audio/mpeg",
        durationMs: Long = 60_000,
        isRingtone: Boolean = false,
    ) = MediaAudioCandidate(
        volumeName = PRIMARY,
        mediaStoreId = id,
        displayName = "track-$id.mp3",
        mimeType = mimeType,
        durationMs = durationMs,
        relativeDirectory = directory,
        isRingtone = isRingtone,
    )

    private companion object {
        const val PRIMARY = "external_primary"
        const val CARD = "card"
    }
}
