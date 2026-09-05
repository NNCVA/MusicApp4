package com.musicapp.player.feature.albums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.VARIOUS_ARTISTS_SENTINEL
import com.musicapp.player.core.designsystem.component.sortedBySectionText
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategorySortDirection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

val UNKNOWN_ALBUM_ID = AlbumGroupingRules.UNKNOWN_ALBUM_ID
const val UNKNOWN_ALBUM_SENTINEL = AlbumGroupingRules.UNKNOWN_ALBUM_SENTINEL

private val ALBUM_WHITESPACE_REGEX = Regex("\\s+")

private fun normalizeAlbumTitleValue(rawTitle: String?): String {
    if (rawTitle.isNullOrBlank()) return ""
    return Normalizer.normalize(rawTitle, Normalizer.Form.NFKC)
        .trim()
        .replace(ALBUM_WHITESPACE_REGEX, " ")
        .lowercase(Locale.ROOT)
}

private fun normalizeArtistTokenValue(rawArtist: String): String =
    Normalizer.normalize(rawArtist, Normalizer.Form.NFKC)
        .trim()
        .replace(ALBUM_WHITESPACE_REGEX, " ")
        .lowercase(Locale.ROOT)

@Composable
fun String.localizedAlbumTitle(): String =
    if (this == UNKNOWN_ALBUM_SENTINEL) stringResource(R.string.album_unknown_title) else this

enum class AlbumSortField { TITLE, ARTIST, TRACK_COUNT, DATE_ADDED }

data class AlbumSort(
    val field: AlbumSortField = AlbumSortField.TITLE,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

data class AlbumGroupKey(
    val volumeName: String,
    val normalizedTitle: String,
    val artistSignature: String = "",
    val versionKeywords: Set<String> = emptySet(),
    val releaseYears: Set<Int> = emptySet(),
) {
    fun encode(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeUTF(volumeName)
            output.writeUTF(normalizedTitle)
            output.writeUTF(artistSignature)
            output.writeInt(versionKeywords.size)
            versionKeywords.toList().sorted().forEach(output::writeUTF)
            output.writeInt(releaseYears.size)
            releaseYears.toList().sorted().forEach(output::writeInt)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
    }

    companion object {
        fun legacy(id: AlbumId, title: String, artistName: String): AlbumGroupKey =
            AlbumGroupKey(
                volumeName = id.volumeName,
                normalizedTitle = normalizeAlbumTitleValue(title),
                artistSignature = normalizeArtistTokenValue(artistName),
            )

        fun decode(encoded: String): AlbumGroupKey? {
            if (encoded.isBlank()) return null
            return runCatching {
                val input = DataInputStream(ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)))
                input.use { stream ->
                    require(stream.readInt() == 1) { "unsupported album group key version" }
                    val volumeName = stream.readUTF()
                    val normalizedTitle = stream.readUTF()
                    val artistSignature = stream.readUTF()
                    val versionCount = stream.readInt().also { require(it in 0..64) }
                    val versionKeywords = List(versionCount) { stream.readUTF() }.toSet()
                    val releaseYearCount = stream.readInt().also { require(it in 0..64) }
                    val releaseYears = List(releaseYearCount) { stream.readInt() }.toSet()
                    require(stream.available() == 0) { "album group key has trailing data" }
                    AlbumGroupKey(
                        volumeName = volumeName,
                        normalizedTitle = normalizedTitle,
                        artistSignature = artistSignature,
                        versionKeywords = versionKeywords,
                        releaseYears = releaseYears,
                    )
                }
            }.getOrNull()
        }
    }
}

val UNKNOWN_ALBUM_GROUP_KEY = AlbumGroupKey(
    volumeName = UNKNOWN_ALBUM_ID.volumeName,
    normalizedTitle = UNKNOWN_ALBUM_SENTINEL,
)

data class AlbumSummary(
    val id: AlbumId,
    val title: String,
    val artistName: String,
    val trackCount: Int,
    val latestDateAddedMs: Long,
    val representativeTrack: Track,
    val memberAlbumIds: Set<AlbumId> = setOf(id),
    val trackIds: Set<com.musicapp.player.core.domain.model.TrackId> = emptySet(),
    val groupKey: AlbumGroupKey = AlbumGroupKey.legacy(id, title, artistName),
) {
    val key: String = groupKey.encode()
}

object AlbumGrouping {
    private val VERSION_KEYWORD_REGEX = AlbumGroupingRules.VERSION_KEYWORD_REGEX

    fun normalizeAlbumTitle(rawTitle: String?): String {
        return normalizeAlbumTitleValue(rawTitle)
    }

    fun splitArtists(artistName: String?): List<String> =
        com.musicapp.player.feature.artists.ArtistGrouping.splitArtistNames(artistName)

    fun extractVersionKeywords(title: String?): Set<String> {
        if (title.isNullOrBlank()) return emptySet()
        return VERSION_KEYWORD_REGEX.findAll(title).map { it.value.lowercase(Locale.ROOT) }.toSet()
    }

    private data class CanonicalTitle(
        val normalized: String,
        val display: String,
    )

    private fun canonicalAlbumTitle(tracks: List<Track>, fallback: String): CanonicalTitle {
        val candidates = tracks.mapNotNull { track ->
            track.albumTitle
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        val winner = candidates
            .groupBy(::normalizeAlbumTitle)
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<String>>> { it.value.size }
                    .thenBy { it.key },
            )
            .firstOrNull()
        val normalized = winner?.key ?: normalizeAlbumTitle(fallback)
        val display = winner?.value
            ?.minWithOrNull(compareBy<String>({ normalizeAlbumTitle(it) }, { it.lowercase(Locale.ROOT) }, { it }))
            ?: fallback
        return CanonicalTitle(normalized = normalized, display = display)
    }

    private fun canonicalArtistSignature(
        tracks: List<Track>,
        preferredArtists: Set<String> = emptySet(),
    ): String {
        val artists = if (preferredArtists.isNotEmpty()) {
            preferredArtists
        } else {
            tracks
                .flatMap { splitArtists(it.artistName) }
                .map(::normalizeArtistTokenValue)
                .toSet()
        }
        return artists
            .filter(String::isNotEmpty)
            .toSet()
            .sorted()
            .joinToString("\u001f")
    }

    private class PhysicalAlbumBundle(
        val albumId: AlbumId,
        val tracks: List<Track>,
    ) {
        val normalizedTitle: String
        val displayTitle: String
        val releaseYears: Set<Int>
        val versionKeywords: Set<String>
        val allArtists: Set<String>
        val coreArtists: Set<String>

        init {
            val canonicalTitle = canonicalAlbumTitle(tracks, tracks.first().title)
            displayTitle = canonicalTitle.display
            normalizedTitle = canonicalTitle.normalized
            releaseYears = tracks.mapNotNull { it.releaseYear }.toSet()
            versionKeywords = extractVersionKeywords(displayTitle)

            val trackArtistSets = tracks.map { track ->
                splitArtists(track.artistName).map(::normalizeArtistTokenValue).toSet()
            }.filter { it.isNotEmpty() }

            allArtists = trackArtistSets.flatten().toSet()
            coreArtists = if (trackArtistSets.isNotEmpty()) {
                trackArtistSets.reduce { acc, set -> acc.intersect(set) }
            } else {
                emptySet()
            }
        }
    }

    private class AlbumCluster(
        val bundles: MutableList<PhysicalAlbumBundle> = mutableListOf(),
    ) {
        val allTracks: List<Track> get() = bundles.flatMap { it.tracks }
        val memberAlbumIds: Set<AlbumId> get() = bundles.map { it.albumId }.toSet()

        fun stableAlbumId(): AlbumId =
            memberAlbumIds.minWithOrNull(compareBy<AlbumId>({ it.volumeName }, { it.mediaStoreId }))
                ?: error("album cluster must contain at least one physical album")

        fun sharedCoreArtists(): Set<String> {
            val bundleCores = bundles.map { it.coreArtists }.filter { it.isNotEmpty() }
            return if (bundleCores.isNotEmpty()) {
                bundleCores.reduce { acc, set -> acc.intersect(set) }
            } else {
                emptySet()
            }
        }

        fun canMerge(candidate: PhysicalAlbumBundle): Boolean {
            // 1. Release year conflict check
            val clusterYears = bundles.flatMap { it.releaseYears }.toSet()
            if (clusterYears.isNotEmpty() && candidate.releaseYears.isNotEmpty()) {
                if (clusterYears.intersect(candidate.releaseYears).isEmpty()) {
                    return false
                }
            }

            // 2. Version keyword modifier conflict check
            val clusterVersions = bundles.flatMap { it.versionKeywords }.toSet()
            if (clusterVersions != candidate.versionKeywords) {
                return false
            }

            // 3. Core primary artist check (prevents A -> A/B -> B transitive merge)
            val currentCore = sharedCoreArtists()
            if (currentCore.isNotEmpty() && candidate.allArtists.isNotEmpty()) {
                if (currentCore.intersect(candidate.allArtists).isEmpty()) {
                    return false
                }
            } else if (currentCore.isEmpty()) {
                val clusterArtists = bundles.flatMap { it.allArtists }.toSet()
                if (clusterArtists.isNotEmpty() && candidate.allArtists.isNotEmpty() && clusterArtists.intersect(candidate.allArtists).isEmpty()) {
                    return false
                }
            }

            return true
        }
    }

    fun group(tracks: List<Track>): List<AlbumSummary> {
        val (noAlbumTracks, hasAlbumTracks) = tracks.partition {
            it.albumId == null || it.albumTitle.isNullOrBlank()
        }

        val bundlesByVolume = hasAlbumTracks
            .filter { it.albumId != null }
            .groupBy { checkNotNull(it.albumId).volumeName }
            .mapValues { (_, volumeTracks) ->
                volumeTracks.groupBy { checkNotNull(it.albumId) }
                    .map { (albumId, albumTracks) -> PhysicalAlbumBundle(albumId, albumTracks) }
            }

        val normalAlbums = mutableListOf<AlbumSummary>()

        for ((volumeName, volumeBundles) in bundlesByVolume) {
            val bundlesByTitle = volumeBundles.groupBy { it.normalizedTitle }

            for ((_, titleBundles) in bundlesByTitle) {
                val clusters = mutableListOf<AlbumCluster>()

                for (bundle in titleBundles.sortedWith(compareBy({ it.albumId.volumeName }, { it.albumId.mediaStoreId }))) {
                    val matchingCluster = clusters
                        .asSequence()
                        .filter { it.canMerge(bundle) }
                        .sortedWith(
                            compareByDescending<AlbumCluster> {
                                it.sharedCoreArtists().intersect(bundle.allArtists).size
                            }
                                .thenBy { it.stableAlbumId().volumeName }
                                .thenBy { it.stableAlbumId().mediaStoreId },
                        )
                        .firstOrNull()
                    if (matchingCluster != null) {
                        matchingCluster.bundles.add(bundle)
                    } else {
                        clusters.add(AlbumCluster(mutableListOf(bundle)))
                    }
                }

                for (cluster in clusters) {
                    val allClusterTracks = cluster.allTracks.distinctBy { it.id }
                    val stableTracks = allClusterTracks.sortedWith(trackIdentityComparator)
                    val memberAlbumIds = cluster.memberAlbumIds
                    val representativeTrack = stableTracks.first()
                    val repAlbumId = representativeTrack.albumId ?: memberAlbumIds.first()
                    val trackIds = stableTracks.map { it.id }.toSet()
                    val canonicalTitle = canonicalAlbumTitle(stableTracks, stableTracks.first().title)
                    val groupKey = AlbumGroupKey(
                        volumeName = volumeName,
                        normalizedTitle = canonicalTitle.normalized,
                        artistSignature = canonicalArtistSignature(stableTracks, cluster.sharedCoreArtists()),
                        versionKeywords = cluster.bundles.flatMap { it.versionKeywords }.toSet(),
                        releaseYears = cluster.bundles.flatMap { it.releaseYears }.toSet(),
                    )

                    val artistCounts = mutableMapOf<String, Int>()
                    val displayArtists = mutableMapOf<String, String>()
                    stableTracks.forEach { track ->
                        val artists = splitArtists(track.artistName)
                        artists.forEach { raw ->
                            val norm = normalizeArtistTokenValue(raw)
                            artistCounts[norm] = (artistCounts[norm] ?: 0) + 1
                            displayArtists.putIfAbsent(norm, raw)
                        }
                    }

                    val primaryArtistNorm = artistCounts.maxByOrNull { it.value }?.key
                    val allRawArtists = stableTracks.map { it.artistName }.distinct()
                    val finalArtistName = when {
                        allRawArtists.size == 1 -> allRawArtists.first()
                        primaryArtistNorm != null && (artistCounts[primaryArtistNorm] ?: 0) > 0 -> {
                            val maxCount = artistCounts[primaryArtistNorm] ?: 0
                            val primaryArtistInEveryCredit = allRawArtists.all {
                                primaryArtistNorm in splitArtists(it).map(::normalizeArtistTokenValue)
                            }
                            if (maxCount * 2 > stableTracks.size || primaryArtistInEveryCredit) {
                                displayArtists[primaryArtistNorm] ?: stableTracks.first().artistName
                            } else {
                                VARIOUS_ARTISTS_SENTINEL
                            }
                        }
                        else -> VARIOUS_ARTISTS_SENTINEL
                    }

                    normalAlbums.add(
                        AlbumSummary(
                            id = repAlbumId,
                            title = canonicalTitle.display,
                            artistName = finalArtistName,
                            trackCount = stableTracks.size,
                            latestDateAddedMs = stableTracks.maxOf(Track::dateAddedMs),
                            representativeTrack = representativeTrack,
                            memberAlbumIds = memberAlbumIds,
                            trackIds = trackIds,
                            groupKey = groupKey,
                        ),
                    )
                }
            }
        }

        // Sort normal albums deterministically
        val sortedNormalAlbums = normalAlbums.sortedWith(
            compareBy<AlbumSummary>(
                { it.title.lowercase(Locale.ROOT) },
                { it.id.volumeName.lowercase(Locale.ROOT) },
                { it.id.mediaStoreId },
            ),
        )

        val unknownAlbum = if (noAlbumTracks.isNotEmpty()) {
            val stableNoAlbumTracks = noAlbumTracks.sortedWith(trackIdentityComparator)
            val distinctArtists = stableNoAlbumTracks.map { it.artistName }.distinct()
            val artistName = if (distinctArtists.size == 1) {
                distinctArtists.first()
            } else {
                VARIOUS_ARTISTS_SENTINEL
            }
            AlbumSummary(
                id = UNKNOWN_ALBUM_ID,
                title = UNKNOWN_ALBUM_SENTINEL,
                artistName = artistName,
                trackCount = stableNoAlbumTracks.size,
                latestDateAddedMs = stableNoAlbumTracks.maxOf(Track::dateAddedMs),
                representativeTrack = stableNoAlbumTracks.first(),
                memberAlbumIds = setOf(UNKNOWN_ALBUM_ID),
                trackIds = stableNoAlbumTracks.map { it.id }.toSet(),
                groupKey = UNKNOWN_ALBUM_GROUP_KEY,
            )
        } else {
            null
        }

        return if (unknownAlbum != null) {
            listOf(unknownAlbum) + sortedNormalAlbums
        } else {
            sortedNormalAlbums
        }
    }

    fun findTracksForAlbum(tracks: List<Track>, targetAlbumId: AlbumId): List<Track> {
        if (targetAlbumId == UNKNOWN_ALBUM_ID) {
            return tracks.filter { it.albumId == null || it.albumTitle.isNullOrBlank() }
        }
        val allAlbums = group(tracks)
        val matchedAlbum = allAlbums.firstOrNull { targetAlbumId in it.memberAlbumIds || it.id == targetAlbumId }
        return if (matchedAlbum != null) {
            findTracksForAlbum(tracks, matchedAlbum)
        } else {
            tracks.filter { it.albumId == targetAlbumId }
        }
    }

    fun findTracksForAlbum(tracks: List<Track>, matchedAlbum: AlbumSummary): List<Track> =
        tracks.filter { it.id in matchedAlbum.trackIds }

    fun findAlbumByGroupKey(albums: List<AlbumSummary>, targetGroupKey: AlbumGroupKey): AlbumSummary? {
        albums.firstOrNull { it.groupKey == targetGroupKey }?.let { return it }
        if (targetGroupKey == UNKNOWN_ALBUM_GROUP_KEY) return null

        val targetArtists = targetGroupKey.artistSignature
            .split('\u001f')
            .filter(String::isNotEmpty)
            .toSet()
        return albums
            .asSequence()
            .filter { album ->
                val candidateKey = album.groupKey
                val candidateArtists = candidateKey.artistSignature
                    .split('\u001f')
                    .filter(String::isNotEmpty)
                    .toSet()
                val artistCompatible = targetArtists.isEmpty() || candidateArtists.isEmpty() ||
                    targetArtists.containsAll(candidateArtists) || candidateArtists.containsAll(targetArtists)
                val yearCompatible = targetGroupKey.releaseYears.isEmpty() || candidateKey.releaseYears.isEmpty() ||
                    targetGroupKey.releaseYears.intersect(candidateKey.releaseYears).isNotEmpty()
                candidateKey.volumeName == targetGroupKey.volumeName &&
                    candidateKey.normalizedTitle == targetGroupKey.normalizedTitle &&
                    candidateKey.versionKeywords == targetGroupKey.versionKeywords &&
                    artistCompatible && yearCompatible
            }
            .sortedWith(
                compareByDescending<AlbumSummary> { album ->
                    targetArtists.intersect(album.groupKey.artistSignature.split('\u001f').filter(String::isNotEmpty).toSet()).size
                }
                    .thenBy { it.id.volumeName }
                    .thenBy { it.id.mediaStoreId },
            )
            .firstOrNull()
    }

    fun findTracksForAlbum(tracks: List<Track>, targetGroupKey: AlbumGroupKey): List<Track> {
        if (targetGroupKey == UNKNOWN_ALBUM_GROUP_KEY) {
            return tracks.filter { it.albumId == null || it.albumTitle.isNullOrBlank() }
        }
        val matchedAlbum = findAlbumByGroupKey(group(tracks), targetGroupKey)
        return matchedAlbum?.let { findTracksForAlbum(tracks, it) } ?: emptyList()
    }


    fun sorted(albums: List<AlbumSummary>, sort: AlbumSort): List<AlbumSummary> {
        val unknownAlbum = albums.firstOrNull { it.id == UNKNOWN_ALBUM_ID }
        val targetAlbums = if (unknownAlbum != null) albums.filterNot { it.id == UNKNOWN_ALBUM_ID } else albums

        val textTieBreaker =
            compareBy<AlbumSummary>(
                { it.title.lowercase(Locale.ROOT) },
                { it.id.volumeName.lowercase(Locale.ROOT) },
                { it.id.mediaStoreId },
            )
        val sectionOrder =
            when (sort.direction) {
                CategorySortDirection.ASCENDING -> SectionSortOrder.ASCENDING
                CategorySortDirection.DESCENDING -> SectionSortOrder.DESCENDING
            }
        val sortedNormal = when (sort.field) {
            AlbumSortField.TITLE ->
                targetAlbums.sortedBySectionText(
                    order = sectionOrder,
                    textSelector = AlbumSummary::title,
                    tieBreaker = textTieBreaker,
                )
            AlbumSortField.ARTIST ->
                targetAlbums.sortedBySectionText(
                    order = sectionOrder,
                    textSelector = AlbumSummary::artistName,
                    tieBreaker = textTieBreaker,
                )
            AlbumSortField.TRACK_COUNT -> {
                val primary = compareBy(AlbumSummary::trackCount)
                targetAlbums.sortedWith(
                    (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                        .then(textTieBreaker),
                )
            }
            AlbumSortField.DATE_ADDED -> {
                val primary = compareBy(AlbumSummary::latestDateAddedMs)
                targetAlbums.sortedWith(
                    (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                        .then(textTieBreaker),
                )
            }
        }

        return if (unknownAlbum != null) {
            listOf(unknownAlbum) + sortedNormal
        } else {
            sortedNormal
        }
    }

    private val trackIdentityComparator =
        compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
}

internal fun AlbumSort.next(field: AlbumSortField): AlbumSort =
    if (this.field == field) {
        copy(
            direction =
                if (direction == CategorySortDirection.ASCENDING) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    } else {
        AlbumSort(
            field = field,
            direction =
                if (field == AlbumSortField.DATE_ADDED || field == AlbumSortField.TRACK_COUNT) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    }

data class AlbumTrackPresentation(
    val track: Track,
    val trackNumberText: String,
    val isPlayable: Boolean,
    val isCurrentPlaying: Boolean = false,
    val hasConflict: Boolean = false,
)

data class AlbumStats(
    val trackCount: Int,
    val totalDurationMs: Long,
    val releaseYear: Int?,
)

data class AlbumTechnicalSummary(
    val bitDepth: Int?,
    val sampleRateHz: Int?,
)

data class AlbumArtistCredit(
    val artistName: String,
    val artistMediaStoreId: Long?,
    val trackCount: Int,
    val representativeTrack: Track,
)

const val ALBUM_TRACK_NO_NUMBER_PLACEHOLDER = AlbumGroupingRules.TRACK_NO_NUMBER_PLACEHOLDER

object AlbumTrackOrdering {
    val defaultSongComparator: Comparator<Track> =
        compareBy<Track>(
            { it.title.lowercase(Locale.ROOT) },
            { it.id.volumeName.lowercase(Locale.ROOT) },
            { it.id.mediaStoreId },
        )

    val discTrackComparator: Comparator<Track> =
        compareBy<Track>(
            { it.discNumber ?: 1 },
            { it.trackNumber ?: 0 },
        ).then(defaultSongComparator)

    fun resolveOrder(
        tracks: List<Track>,
        currentPlayingTrackId: com.musicapp.player.core.domain.model.TrackId? = null,
    ): List<AlbumTrackPresentation> {
        if (tracks.isEmpty()) return emptyList()

        val hasAnyTrackNumber = tracks.any { (it.trackNumber ?: 0) > 0 }
        if (!hasAnyTrackNumber) {
            val sorted = tracks.sortedWith(defaultSongComparator)
            return sorted.map { track ->
                AlbumTrackPresentation(
                    track = track,
                    trackNumberText = ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                    isPlayable = track.availability == com.musicapp.player.core.domain.model.Availability.AVAILABLE,
                    isCurrentPlaying = track.id == currentPlayingTrackId,
                    hasConflict = false,
                )
            }
        }

        val validTrackMap = mutableMapOf<Pair<Int, Int>, MutableList<Track>>()
        val invalidOrConflictTracks = mutableListOf<Track>()

        for (track in tracks) {
            val trackNum = track.trackNumber
            if (trackNum != null && trackNum > 0) {
                val discNum = track.discNumber ?: 1
                val key = Pair(discNum, trackNum)
                validTrackMap.getOrPut(key) { mutableListOf() }.add(track)
            } else {
                invalidOrConflictTracks.add(track)
            }
        }

        val validTracks = mutableListOf<Track>()
        for ((_, list) in validTrackMap) {
            if (list.size == 1) {
                validTracks.add(list[0])
            } else {
                invalidOrConflictTracks.addAll(list)
            }
        }

        val sortedValid = validTracks.sortedWith(discTrackComparator)
        val sortedInvalid = invalidOrConflictTracks.sortedWith(defaultSongComparator)

        val isMultiDisc = tracks.any { (it.discNumber ?: 1) > 1 }

        val presentations = mutableListOf<AlbumTrackPresentation>()
        sortedValid.forEach { track ->
            val disc = track.discNumber ?: 1
            val num = checkNotNull(track.trackNumber)
            val numberText = if (isMultiDisc) {
                val formattedTrack = if (num < 10) "0$num" else num.toString()
                "$disc-$formattedTrack"
            } else {
                num.toString()
            }
            presentations.add(
                AlbumTrackPresentation(
                    track = track,
                    trackNumberText = numberText,
                    isPlayable = track.availability == com.musicapp.player.core.domain.model.Availability.AVAILABLE,
                    isCurrentPlaying = track.id == currentPlayingTrackId,
                    hasConflict = false,
                ),
            )
        }

        sortedInvalid.forEach { track ->
            presentations.add(
                AlbumTrackPresentation(
                    track = track,
                    trackNumberText = ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                    isPlayable = track.availability == com.musicapp.player.core.domain.model.Availability.AVAILABLE,
                    isCurrentPlaying = track.id == currentPlayingTrackId,
                    hasConflict = (track.trackNumber ?: 0) > 0,
                ),
            )
        }

        return presentations
    }
}

object AlbumDetailAggregator {
    fun aggregateStats(tracks: List<Track>): AlbumStats {
        if (tracks.isEmpty()) return AlbumStats(0, 0L, null)
        val years = tracks.mapNotNull { it.releaseYear }.distinct()
        val uniformYear = if (years.size == 1 && tracks.all { it.releaseYear != null }) {
            years.first()
        } else {
            null
        }
        return AlbumStats(
            trackCount = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs },
            releaseYear = uniformYear,
        )
    }

    fun aggregateTechnicalSummary(
        tracks: List<Track>,
        metadataMap: Map<com.musicapp.player.core.domain.model.TrackId, com.musicapp.player.core.metadata.AdvancedTrackMetadata?>,
    ): AlbumTechnicalSummary {
        if (tracks.isEmpty()) return AlbumTechnicalSummary(null, null)
        val metadatas = tracks.map { metadataMap[it.id] }
        val bitDepths = metadatas.mapNotNull { it?.bitDepth }.distinct()
        val sampleRates = metadatas.mapNotNull { it?.sampleRateHz }.distinct()

        val uniformBitDepth = if (bitDepths.size == 1 && metadatas.all { it?.bitDepth != null }) {
            bitDepths.first()
        } else {
            null
        }

        val uniformSampleRate = if (sampleRates.size == 1 && metadatas.all { it?.sampleRateHz != null }) {
            sampleRates.first()
        } else {
            null
        }

        return AlbumTechnicalSummary(bitDepth = uniformBitDepth, sampleRateHz = uniformSampleRate)
    }

    fun aggregateArtists(orderedTracks: List<Track>): List<AlbumArtistCredit> {
        val creditsByNormalized = linkedMapOf<String, MutableList<Track>>()
        val displayNames = mutableMapOf<String, String>()

        for (track in orderedTracks) {
            val names = com.musicapp.player.feature.artists.ArtistGrouping.splitArtistNames(track.artistName)
            for (name in names) {
                val key = com.musicapp.player.feature.artists.ArtistGrouping.normalizedKey(name) ?: continue
                creditsByNormalized.getOrPut(key) { mutableListOf() }.add(track)
                displayNames.putIfAbsent(key, name)
            }
        }

        return creditsByNormalized.mapNotNull { (key, tracks) ->
            val displayName = displayNames[key] ?: return@mapNotNull null
            val distinctTracks = tracks.distinctBy { it.id }
            val repTrack = distinctTracks.firstOrNull() ?: return@mapNotNull null
            AlbumArtistCredit(
                artistName = displayName,
                artistMediaStoreId = repTrack.artistMediaStoreId,
                trackCount = distinctTracks.size,
                representativeTrack = repTrack,
            )
        }
    }
}
