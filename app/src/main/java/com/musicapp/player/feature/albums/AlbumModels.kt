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
import java.util.Locale

val UNKNOWN_ALBUM_ID = AlbumId(volumeName = "virtual", mediaStoreId = Long.MAX_VALUE)
const val UNKNOWN_ALBUM_SENTINEL = "<unknown_album>"

@Composable
fun String.localizedAlbumTitle(): String =
    if (this == UNKNOWN_ALBUM_SENTINEL) stringResource(R.string.album_unknown_title) else this

enum class AlbumSortField { TITLE, ARTIST, TRACK_COUNT, DATE_ADDED }

data class AlbumSort(
    val field: AlbumSortField = AlbumSortField.TITLE,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

data class AlbumSummary(
    val id: AlbumId,
    val title: String,
    val artistName: String,
    val trackCount: Int,
    val latestDateAddedMs: Long,
    val representativeTrack: Track,
)

object AlbumGrouping {
    fun group(tracks: List<Track>): List<AlbumSummary> {
        val (noAlbumTracks, hasAlbumTracks) = tracks.partition {
            it.albumId == null || it.albumTitle.isNullOrBlank()
        }

        val normalAlbums = hasAlbumTracks.asSequence()
            .filter { it.albumId != null }
            .groupBy { checkNotNull(it.albumId) }
            .map { (id, albumTracks) ->
                val stableTracks = albumTracks.sortedWith(trackIdentityComparator)
                AlbumSummary(
                    id = id,
                    title = stableTracks.firstNotNullOfOrNull(Track::albumTitle) ?: stableTracks.first().title,
                    artistName = stableTracks.first().artistName,
                    trackCount = stableTracks.size,
                    latestDateAddedMs = stableTracks.maxOf(Track::dateAddedMs),
                    representativeTrack = stableTracks.first(),
                )
            }
            .toList()

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
            )
        } else {
            null
        }

        return if (unknownAlbum != null) {
            listOf(unknownAlbum) + normalAlbums
        } else {
            normalAlbums
        }
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

const val ALBUM_TRACK_NO_NUMBER_PLACEHOLDER = "–"

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
        val result = mutableListOf<AlbumArtistCredit>()
        val seenArtists = mutableSetOf<String>()

        for (track in orderedTracks) {
            val artistName = track.artistName
            if (seenArtists.add(artistName)) {
                val count = orderedTracks.count { it.artistName == artistName }
                result.add(
                    AlbumArtistCredit(
                        artistName = artistName,
                        artistMediaStoreId = track.artistMediaStoreId,
                        trackCount = count,
                        representativeTrack = track,
                    ),
                )
            }
        }
        return result
    }
}
