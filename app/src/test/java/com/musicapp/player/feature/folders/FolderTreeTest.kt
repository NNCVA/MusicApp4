package com.musicapp.player.feature.folders

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FolderTreeTest {
    @Test
    fun `same directory path on different volumes remains separate`() {
        val roots =
            FolderTree.build(
                listOf(
                    track("external", 1, "Music/Live"),
                    track("sdcard", 1, "Music/Live"),
                ),
            )

        assertEquals(listOf("external", "sdcard"), roots.map { it.id.volumeName })
        assertNotNull(FolderTree.find(roots, FolderId("external", "Music/Live")))
        assertNotNull(FolderTree.find(roots, FolderId("sdcard", "Music/Live")))
    }

    @Test
    fun `recursive tracks include direct and descendant folders only`() {
        val roots =
            FolderTree.build(
                listOf(
                    track("external", 1, "Music"),
                    track("external", 2, "Music/Live"),
                    track("external", 3, "Podcasts"),
                ),
            )

        val music = checkNotNull(FolderTree.find(roots, FolderId("external", "Music")))

        assertEquals(listOf(1L), music.directTracks.map { it.id.mediaStoreId })
        assertEquals(setOf(1L, 2L), music.recursiveTracks.map { it.id.mediaStoreId }.toSet())
        assertEquals(listOf("Live"), music.children.map(FolderNode::displayName))
    }

    @Test
    fun `root direct descendants and similarly prefixed sibling stay in exact folders`() {
        val roots = FolderTree.build(
            listOf(
                track("external", 1, ""),
                track("external", 2, "Music"),
                track("external", 3, "Music/Live"),
                track("external", 4, "Music Videos"),
                track("card", 5, "Music"),
            ),
        )
        val externalRoot = checkNotNull(FolderTree.find(roots, FolderId("external", "")))
        val music = checkNotNull(FolderTree.find(roots, FolderId("external", "Music")))

        assertEquals(listOf(1L), externalRoot.directTracks.map { it.id.mediaStoreId })
        assertEquals(listOf(2L), music.directTracks.map { it.id.mediaStoreId })
        assertEquals(setOf(2L, 3L), music.recursiveTracks.map { it.id.mediaStoreId }.toSet())
        assertEquals(setOf(1L, 2L, 3L, 4L), externalRoot.recursiveTracks.map { it.id.mediaStoreId }.toSet())
    }

    @Test
    fun `stable recursive tree order becomes folder playback context order`() {
        val tracks = listOf(
            track("external", 1, "Music", title = "Zulu"),
            track("external", 2, "Music/Live", title = "Alpha"),
            track(
                "external",
                3,
                "Music/Live",
                title = "Bravo",
                availability = Availability.TEMPORARILY_UNAVAILABLE,
            ),
        )
        val music = checkNotNull(FolderTree.find(FolderTree.build(tracks), FolderId("external", "Music")))

        val context = CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.FOLDER,
            sourceId = music.id.sourceId,
            tracks = music.recursiveTracks,
        )

        assertEquals(listOf(2L, 3L, 1L), music.recursiveTracks.map { it.id.mediaStoreId })
        assertEquals(listOf(tracks[1].id, tracks[0].id), context?.orderedTrackIds)
        assertEquals("external|Music", context?.sourceId)
    }

    private fun track(
        volume: String,
        id: Long,
        path: String,
        title: String = "Track $id",
        availability: Availability = Availability.AVAILABLE,
    ) =
        Track(
            id = TrackId(volume, id),
            title = title,
            artistName = "Artist",
            durationMs = 1_000,
            dateAddedMs = id,
            dateModifiedMs = id,
            relativePath = path,
            displayName = "$id.mp3",
            availability = availability,
        )
}
