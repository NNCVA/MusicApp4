package com.musicapp.player

import com.musicapp.player.navigation.AboutRoute
import com.musicapp.player.navigation.AlbumsRoute
import com.musicapp.player.navigation.ArtistDetailRoute
import com.musicapp.player.navigation.EqualizerRoute
import com.musicapp.player.navigation.ScanMusicRoute
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TracksRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerTransitionTest {

    @Test
    fun isEqualizerRoute_correctlyIdentifiesEqualizerRoutes() {
        assertTrue(isEqualizerRoute(EqualizerRoute))

        assertFalse(isEqualizerRoute(TracksRoute))
        assertFalse(isEqualizerRoute(AlbumsRoute))
        assertFalse(isEqualizerRoute(SettingsRoute))
        assertFalse(isEqualizerRoute(ScanMusicRoute))
        assertFalse(isEqualizerRoute(AboutRoute))
        assertFalse(isEqualizerRoute(ArtistDetailRoute(artistName = "Artist")))
        assertFalse(isEqualizerRoute(null))
        assertFalse(isEqualizerRoute("unknown"))
    }

    @Test
    fun equalizerFadeTransition_createsValidContentTransform() {
        val transition = equalizerFadeTransition()
        assertNotNull(transition)
        assertNotNull(transition.targetContentEnter)
        assertNotNull(transition.initialContentExit)
    }

    @Test
    fun equalizerTransitionMetadata_containsAllRequiredTransitionSpecs() {
        val metadata = equalizerTransitionMetadata
        assertNotNull(metadata)
        assertEquals("Equalizer metadata should contain transitionSpec, popTransitionSpec, and predictivePopTransitionSpec", 3, metadata.size)
    }
}
