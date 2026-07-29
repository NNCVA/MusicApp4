package com.musicapp.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.feature.root.DetailPlaceholderScreen
import com.musicapp.player.feature.root.FakeRootScreen
import com.musicapp.player.feature.root.FakeRootState
import com.musicapp.player.ui.components.MiniPlayerPlaceholder
import com.musicapp.player.ui.components.MiniPlayerState
import com.musicapp.player.ui.components.MusicScaffold
import com.musicapp.player.ui.shell.AdaptiveNavigationShell
import com.musicapp.player.theme.MusicTheme

@Composable
fun MainNavigation(
  windowSizeClass: MusicWindowSizeClass,
  initialTopLevel: TopLevelRoute = TracksRoute,
  rootStates: Map<TopLevelRoute, FakeRootState> = defaultFakeRootStates(),
) {
  val navigator = rememberMusicNavigator(initialTopLevel = initialTopLevel)
  val currentDestination = navigator.currentBackStack.lastOrNull()

  BackHandler(enabled = navigator.canHandleBack) { navigator.goBack() }

  if (currentDestination == FullPlayerRoute) {
    FullPlayerPlaceholder(onClose = { navigator.goBack() })
    return
  }

  AdaptiveNavigationShell(
    windowSizeClass = windowSizeClass,
    selectedRoute = navigator.selectedTopLevel,
    onSelectRoute = navigator::selectTopLevel,
  ) { openNavigation ->
    val snackbarHostState = remember { SnackbarHostState() }
    MusicScaffold(
      snackbarHostState = snackbarHostState,
      miniPlayer = {
        MiniPlayerPlaceholder(
          state =
            MiniPlayerState(
              title = stringResource(R.string.fake_track_title),
              artist = stringResource(R.string.fake_track_artist),
              isPlaying = false,
            ),
          onOpenPlayer = { navigator.navigate(FullPlayerRoute) },
          onPlayPause = {},
          onNext = {},
        )
      },
    ) { contentPadding ->
      NavDisplay(
        backStack = navigator.currentBackStack,
        onBack = { navigator.goBack() },
        entryProvider =
          entryProvider {
            MusicTopLevelRoutes.forEach { route ->
              entry(route) {
                FakeRootScreen(
                  route = route,
                  state = rootStates[route] ?: FakeRootState.Content,
                  contentPadding = contentPadding,
                  onOpenNavigation = openNavigation.takeIf { windowSizeClass == MusicWindowSizeClass.Compact },
                  onRetry = {},
                  modifier = Modifier.testTag("root_${route.stableId}"),
                )
              }
            }
            entry<AlbumDetailRoute> {
              DetailPlaceholderScreen(contentPadding = contentPadding, onBack = navigator::goBack)
            }
            entry<ArtistDetailRoute> {
              DetailPlaceholderScreen(contentPadding = contentPadding, onBack = navigator::goBack)
            }
            entry<PlaylistDetailRoute> {
              DetailPlaceholderScreen(contentPadding = contentPadding, onBack = navigator::goBack)
            }
            entry<FolderDetailRoute> {
              DetailPlaceholderScreen(contentPadding = contentPadding, onBack = navigator::goBack)
            }
          },
      )
    }
  }
}

fun defaultFakeRootStates(): Map<TopLevelRoute, FakeRootState> =
  MusicTopLevelRoutes.associateWith { FakeRootState.Content }

@Composable
private fun FullPlayerPlaceholder(onClose: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(MusicTheme.dimensions.horizontalPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = stringResource(R.string.full_player_placeholder),
      color = MaterialTheme.colorScheme.onSurface,
      style = MusicTheme.typography.stateTitle,
    )
    Button(onClick = onClose) { Text(stringResource(R.string.action_close_player)) }
  }
}
