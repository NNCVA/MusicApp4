package com.musicapp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicNavigationReducerTest {
  @Test
  fun eachTopLevelRouteKeepsItsOwnBackStack() {
    var state = MusicNavigationState()
    state = MusicNavigationReducer.selectTopLevel(state, AlbumsRoute)
    state = MusicNavigationReducer.navigate(state, AlbumDetailRoute("external", 7L))
    state = MusicNavigationReducer.selectTopLevel(state, PlaylistsRoute)
    state = MusicNavigationReducer.navigate(state, PlaylistDetailRoute("playlist-1"))

    state = MusicNavigationReducer.selectTopLevel(state, AlbumsRoute)

    assertEquals(listOf(AlbumsRoute, AlbumDetailRoute("external", 7L)), state.currentBackStack)
    assertEquals(
      listOf(PlaylistsRoute, PlaylistDetailRoute("playlist-1")),
      state.backStacks.getValue(PlaylistsRoute),
    )
  }

  @Test
  fun selectingCurrentTopLevelAgainReturnsToItsRoot() {
    var state = MusicNavigationReducer.selectTopLevel(MusicNavigationState(), AlbumsRoute)
    state = MusicNavigationReducer.navigate(state, AlbumDetailRoute("external", 7L))

    state = MusicNavigationReducer.selectTopLevel(state, AlbumsRoute)

    assertEquals(listOf(AlbumsRoute), state.currentBackStack)
  }

  @Test
  fun backPopsDetailThenReturnsToTracksRoot() {
    var state = MusicNavigationReducer.selectTopLevel(MusicNavigationState(), AlbumsRoute)
    state = MusicNavigationReducer.navigate(state, AlbumDetailRoute("external", 7L))

    val fromDetail = MusicNavigationReducer.goBack(state)
    val fromAlbumsRoot = MusicNavigationReducer.goBack(fromDetail.state)

    assertTrue(fromDetail.consumed)
    assertEquals(AlbumsRoute, fromDetail.state.selectedTopLevel)
    assertEquals(listOf(AlbumsRoute), fromDetail.state.currentBackStack)
    assertTrue(fromAlbumsRoot.consumed)
    assertEquals(TracksRoute, fromAlbumsRoot.state.selectedTopLevel)
  }

  @Test
  fun backAtTracksRootIsNotConsumed() {
    val state = MusicNavigationState()

    val result = MusicNavigationReducer.goBack(state)

    assertFalse(result.consumed)
    assertSame(state, result.state)
  }

  @Test
  fun fullPlayerIsARecoverableDestinationAboveCurrentStack() {
    var state = MusicNavigationReducer.selectTopLevel(MusicNavigationState(), HistoryRoute)
    state = MusicNavigationReducer.navigate(state, FullPlayerRoute)

    val result = MusicNavigationReducer.goBack(state)

    assertEquals(listOf(HistoryRoute, FullPlayerRoute), state.currentBackStack)
    assertEquals(listOf(HistoryRoute), result.state.currentBackStack)
  }

  @Test(expected = IllegalArgumentException::class)
  fun navigateRejectsTopLevelDestinations() {
    MusicNavigationReducer.navigate(MusicNavigationState(), AboutRoute)
  }

  @Test(expected = IllegalArgumentException::class)
  fun stateRejectsMissingRootStack() {
    MusicNavigationState(backStacks = initialMusicBackStacks() - AboutRoute)
  }
}
