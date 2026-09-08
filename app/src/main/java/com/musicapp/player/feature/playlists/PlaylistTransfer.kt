package com.musicapp.player.feature.playlists

import android.content.Context
import android.net.Uri
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PlaylistExportData(
    val playlistName: String,
    val content: String,
    val exportedCount: Int,
    val skippedCount: Int,
)

data class PlaylistImportData(
    val playlistId: PlaylistId,
    val playlistName: String,
    val matchedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
)

class PlaylistTransferUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val clock: Clock,
) {
    suspend fun export(playlistId: PlaylistId): PlaylistExportData {
        val playlist = requireNotNull(playlistRepository.observePlaylist(playlistId).first()) {
            "playlist does not exist"
        }
        val tracksById = mediaLibraryRepository.observeTracks(includeHidden = true)
            .first()
            .associateBy { it.id }
        var skippedCount = 0
        val paths = playlist.trackIds.mapNotNull { trackId ->
            val track = tracksById[trackId]
            if (track == null) {
                skippedCount += 1
                null
            } else {
                PlaylistTextCodec.pathFor(track)
            }
        }
        return PlaylistExportData(
            playlistName = playlist.displayName,
            content = PlaylistTextCodec.encode(playlist.displayName, paths),
            exportedCount = paths.size,
            skippedCount = skippedCount,
        )
    }

    suspend fun import(content: String): PlaylistImportData {
        val document = PlaylistTextCodec.decode(content)
        val existingPlaylists = playlistRepository.observePlaylists().first()
        val name = uniqueImportedName(document.displayName, existingPlaylists.map { it.normalizedName })
        val tracksByPath = mediaLibraryRepository.observeTracks(includeHidden = true)
            .first()
            .groupBy(PlaylistTextCodec::pathFor)

        val seenPaths = linkedSetOf<String>()
        var skippedCount = 0
        var duplicateCount = 0
        val matchedTrackIds = buildList {
            document.paths.forEach { rawPath ->
                val path = runCatching { PlaylistTextCodec.canonicalPath(rawPath) }.getOrNull()
                if (path == null) {
                    skippedCount += 1
                    return@forEach
                }
                if (!seenPaths.add(path)) {
                    duplicateCount += 1
                    return@forEach
                }
                val matches = tracksByPath[path].orEmpty()
                val trackId = matches.singleOrNull()?.id
                if (trackId == null) {
                    skippedCount += 1
                } else {
                    add(trackId)
                }
            }
        }

        val playlistId = playlistRepository.createPlaylistWithTracks(
            displayName = name.displayName,
            normalizedName = name.normalizedName,
            trackIds = matchedTrackIds,
            createdAtMs = clock.currentTimeMillis(),
        )
        return PlaylistImportData(
            playlistId = playlistId,
            playlistName = name.displayName,
            matchedCount = matchedTrackIds.size,
            skippedCount = skippedCount,
            duplicateCount = duplicateCount,
        )
    }

    private fun uniqueImportedName(rawName: String, existingNormalizedNames: List<String>): PlaylistName {
        val baseName = PlaylistNameNormalizer.normalize(rawName)
        val base = baseName.displayName
        val occupied = existingNormalizedNames.toSet()
        if (baseName.normalizedName !in occupied) return baseName

        var suffix = 2
        while (true) {
            val suffixText = " ($suffix)"
            val baseLimit = MAX_PLAYLIST_NAME_CODE_POINTS - suffixText.codePointCount(0, suffixText.length)
            val truncatedBase = base.takeCodePoints(baseLimit).trimEnd()
            val candidate = PlaylistNameNormalizer.normalize(truncatedBase + suffixText)
            if (candidate.normalizedName !in occupied) return candidate
            suffix += 1
        }
    }
}

private fun String.takeCodePoints(maxCodePoints: Int): String {
    if (maxCodePoints <= 0) return ""
    val end = offsetByCodePoints(0, minOf(maxCodePoints, codePointCount(0, length)))
    return substring(0, end)
}

interface PlaylistFileGateway {
    suspend fun read(uri: Uri): String
    suspend fun write(uri: Uri, content: String)
}

@Singleton
class AndroidPlaylistFileGateway @Inject constructor(
    @ApplicationContext context: Context,
) : PlaylistFileGateway {
    private val contentResolver = context.contentResolver

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
            reader?.readText() ?: throw IOException("unable to open playlist file")
        }
    }

    override suspend fun write(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer?.write(content) ?: throw IOException("unable to write playlist file")
        }
    }
}

internal object UnsupportedPlaylistFileGateway : PlaylistFileGateway {
    override suspend fun read(uri: Uri): String = error("file gateway is unavailable in this test")

    override suspend fun write(uri: Uri, content: String) = error("file gateway is unavailable in this test")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaylistTransferModule {
    @Binds
    @Singleton
    abstract fun bindPlaylistFileGateway(
        implementation: AndroidPlaylistFileGateway,
    ): PlaylistFileGateway
}
