package com.musicapp.player

data class MusicNavigationState(
  val selectedTopLevel: TopLevelRoute = TracksRoute,
  val backStacks: Map<TopLevelRoute, List<MusicNavKey>> = initialMusicBackStacks(),
) {
  init {
    require(MusicTopLevelRoutes.all { route -> backStacks[route]?.firstOrNull() == route }) {
      "Every top-level back stack must start with its own root route"
    }
  }

  val currentBackStack: List<MusicNavKey>
    get() = backStacks.getValue(selectedTopLevel)
}

data class NavigationBackResult(
  val state: MusicNavigationState,
  val consumed: Boolean,
)

fun initialMusicBackStacks(): Map<TopLevelRoute, List<MusicNavKey>> =
  MusicTopLevelRoutes.associateWith { route -> listOf(route) }

object MusicNavigationReducer {
  fun selectTopLevel(state: MusicNavigationState, route: TopLevelRoute): MusicNavigationState {
    if (route != state.selectedTopLevel) return state.copy(selectedTopLevel = route)
    return state.withStack(route, listOf(route))
  }

  fun navigate(state: MusicNavigationState, destination: MusicNavKey): MusicNavigationState {
    require(destination !is TopLevelRoute) { "Use selectTopLevel for top-level destinations" }
    val stack = state.currentBackStack
    if (stack.lastOrNull() == destination) return state
    return state.withStack(state.selectedTopLevel, stack + destination)
  }

  fun goBack(state: MusicNavigationState): NavigationBackResult {
    val stack = state.currentBackStack
    if (stack.size > 1) {
      return NavigationBackResult(
        state = state.withStack(state.selectedTopLevel, stack.dropLast(1)),
        consumed = true,
      )
    }
    if (state.selectedTopLevel != TracksRoute) {
      return NavigationBackResult(state = state.copy(selectedTopLevel = TracksRoute), consumed = true)
    }
    return NavigationBackResult(state = state, consumed = false)
  }
}

private fun MusicNavigationState.withStack(
  route: TopLevelRoute,
  stack: List<MusicNavKey>,
): MusicNavigationState = copy(backStacks = backStacks + (route to stack))
