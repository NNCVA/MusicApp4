package com.musicapp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

@Stable
class MusicNavigator internal constructor(
  val backStacks: Map<TopLevelRoute, NavBackStack<NavKey>>,
  private val selectedRouteId: MutableState<String>,
) {
  var selectedTopLevel: TopLevelRoute
    get() = topLevelRoute(selectedRouteId.value)
    private set(value) {
      selectedRouteId.value = value.stableId
    }

  val currentBackStack: NavBackStack<NavKey>
    get() = backStacks.getValue(selectedTopLevel)

  val canHandleBack: Boolean
    get() = currentBackStack.size > 1 || selectedTopLevel != TracksRoute

  fun selectTopLevel(route: TopLevelRoute) {
    if (route == selectedTopLevel) {
      val stack = backStacks.getValue(route)
      while (stack.size > 1) stack.removeLastOrNull()
    } else {
      selectedTopLevel = route
    }
  }

  fun navigate(destination: MusicNavKey) {
    require(destination !is TopLevelRoute) { "Use selectTopLevel for top-level destinations" }
    if (currentBackStack.lastOrNull() != destination) currentBackStack.add(destination)
  }

  fun goBack(): Boolean {
    if (currentBackStack.size > 1) {
      currentBackStack.removeLastOrNull()
      return true
    }
    if (selectedTopLevel != TracksRoute) {
      selectedTopLevel = TracksRoute
      return true
    }
    return false
  }
}

@Composable
fun rememberMusicNavigator(initialTopLevel: TopLevelRoute = TracksRoute): MusicNavigator {
  val selectedRouteId =
    rememberSaveable(initialTopLevel.stableId) { mutableStateOf(initialTopLevel.stableId) }
  val backStacks =
    MusicTopLevelRoutes.associateWith { route ->
      rememberNavBackStack(route)
    }
  return remember(backStacks, selectedRouteId) {
    MusicNavigator(backStacks = backStacks, selectedRouteId = selectedRouteId)
  }
}
