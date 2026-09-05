package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.albums.AlbumGroupKey
import com.musicapp.player.feature.albums.AlbumGrouping

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

data class ArtistSummary(
    val id: ArtistId,
    val displayName: String,
    val trackCount: Int,
    val artworkCandidates: List<Track>,
)

data class ArtistAlbumSummary(
    val albumId: AlbumId,
    val title: String,
    val artistName: String,
    val artistTrackCount: Int,
    val representativeTrack: Track,
    val groupKey: AlbumGroupKey,
)

/** Stable, URL-safe route token for a complete artist label. */
object ArtistRouteKey {
    private const val PREFIX = "a_"

    fun encode(artistName: String): String {
        val normalized = artistName.trim()
        require(normalized.isNotEmpty()) { "artistName must not be blank" }
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(routeKey: String): String? {
        if (!routeKey.startsWith(PREFIX)) return null
        return runCatching {
            Base64.getUrlDecoder().decode(routeKey.removePrefix(PREFIX))
                .toString(StandardCharsets.UTF_8)
                .trim()
                .takeIf(String::isNotEmpty)
        }.getOrNull()
    }
}

/** Groups artist labels by splitting delimiters while protecting known entities. */
object ArtistGrouping {
    fun splitArtistNames(artistName: String?): List<String> {
        if (artistName.isNullOrBlank()) return emptyList()
        var sanitized = artistName.trim()
        val replacements = mutableMapOf<String, String>()
        for ((index, protectedName) in ArtistSplittingRules.PROTECTED_WHITELIST.withIndex()) {
            val placeholder = "__PROTECTED_${index}__"
            val pattern = Regex("(?i)(?<=^|[/、\\\\,;，；&\\s])" + Regex.escape(protectedName) + "(?=[/、\\\\,;，；&\\s]|$)")
            sanitized = pattern.replace(sanitized) { matchResult ->
                val originalText = matchResult.value
                replacements[placeholder] = originalText
                placeholder
            }
        }
        return sanitized.split(ArtistSplittingRules.DELIMITER_REGEX)
            .map { token ->
                var restored = token.trim()
                for ((placeholder, originalText) in replacements) {
                    restored = restored.replace(placeholder, originalText)
                }
                restored.trim()
            }
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .ifEmpty { listOf(artistName.trim()) }
    }

    fun normalizedKey(artistName: String?): String? =
        artistName?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)

    fun matches(artistName: String?, artistId: ArtistId): Boolean {
        val targetKey = normalizedKey(artistId.name) ?: return false
        return splitArtistNames(artistName).any { normalizedKey(it) == targetKey }
    }

    fun groupAlbumsForArtist(
        allTracks: List<Track>,
        artistTracks: List<Track>,
    ): List<ArtistAlbumSummary> {
        if (artistTracks.isEmpty()) return emptyList()
        val artistTrackIds = artistTracks.mapTo(hashSetOf(), Track::id)
        return AlbumGrouping.group(allTracks)
            .mapNotNull { album ->
                val matchingIds = album.trackIds.intersect(artistTrackIds)
                if (matchingIds.isEmpty()) {
                    null
                } else {
                    ArtistAlbumSummary(
                        albumId = album.id,
                        title = album.title,
                        artistName = album.artistName,
                        artistTrackCount = matchingIds.size,
                        representativeTrack = album.representativeTrack,
                        groupKey = album.groupKey,
                    )
                }
            }
    }

    fun group(tracks: List<Track>): List<ArtistSummary> {
        val tracksByNormalizedArtist = linkedMapOf<String, MutableList<Track>>()
        val displayNamesByNormalized = mutableMapOf<String, String>()

        for (track in tracks) {
            val names = splitArtistNames(track.artistName)
            for (name in names) {
                val normalized = normalizedKey(name) ?: continue
                tracksByNormalizedArtist.getOrPut(normalized) { mutableListOf() }.add(track)
                displayNamesByNormalized.putIfAbsent(normalized, name)
            }
        }

        return tracksByNormalizedArtist.map { (normalizedKey, artistTracks) ->
            val displayName = displayNamesByNormalized.getValue(normalizedKey)
            val stableTracks = artistTracks.distinctBy { it.id }.sortedWith(trackIdentityComparator)
            ArtistSummary(
                id = ArtistId(normalizedKey),
                displayName = displayName,
                trackCount = stableTracks.size,
                artworkCandidates = stableTracks,
            )
        }.let(::sortArtistsByIndexedName)
    }

    private val trackIdentityComparator =
        compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
}

typealias ArtistArtworkSignature = List<ArtistArtworkCandidateSignature>

data class ArtistArtworkCandidateSignature(
    val trackId: TrackId,
    val dateModifiedMs: Long,
) {
    init {
        require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
    }
}

internal fun ArtistSummary.artworkSignature(): ArtistArtworkSignature =
    artworkCandidates
        .sortedWith(trackIdentityComparator)
        .map { track ->
            ArtistArtworkCandidateSignature(
                trackId = track.id,
                dateModifiedMs = track.dateModifiedMs,
            )
        }

internal fun List<Track>.artworkSignature(): ArtistArtworkSignature =
    sortedWith(trackIdentityComparator)
        .map { track ->
            ArtistArtworkCandidateSignature(
                trackId = track.id,
                dateModifiedMs = track.dateModifiedMs,
            )
        }

internal fun ArtistSummary.sortedArtworkCandidates(): List<Track> =
    artworkCandidates.sortedWith(trackIdentityComparator)

private val trackIdentityComparator =
    compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
