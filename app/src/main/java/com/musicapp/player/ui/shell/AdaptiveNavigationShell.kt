package com.musicapp.player.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.musicapp.player.AboutRoute
import com.musicapp.player.AlbumsRoute
import com.musicapp.player.ArtistsRoute
import com.musicapp.player.FoldersRoute
import com.musicapp.player.HistoryRoute
import com.musicapp.player.MusicTopLevelRoutes
import com.musicapp.player.PlaylistsRoute
import com.musicapp.player.R
import com.musicapp.player.SettingsRoute
import com.musicapp.player.TopLevelRoute
import com.musicapp.player.TracksRoute
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.launch

data class NavigationItem(
  val route: TopLevelRoute,
  @param:StringRes val labelRes: Int,
  @param:StringRes val shortLabelRes: Int,
)

val MusicNavigationItems: List<NavigationItem> =
  listOf(
    NavigationItem(TracksRoute, R.string.nav_tracks, R.string.nav_tracks_short),
    NavigationItem(AlbumsRoute, R.string.nav_albums, R.string.nav_albums_short),
    NavigationItem(ArtistsRoute, R.string.nav_artists, R.string.nav_artists_short),
    NavigationItem(PlaylistsRoute, R.string.nav_playlists, R.string.nav_playlists_short),
    NavigationItem(HistoryRoute, R.string.nav_history, R.string.nav_history_short),
    NavigationItem(FoldersRoute, R.string.nav_folders, R.string.nav_folders_short),
    NavigationItem(SettingsRoute, R.string.nav_settings, R.string.nav_settings_short),
    NavigationItem(AboutRoute, R.string.nav_about, R.string.nav_about_short),
  ).also { items -> check(items.map(NavigationItem::route) == MusicTopLevelRoutes) }

@Composable
fun AdaptiveNavigationShell(
  windowSizeClass: MusicWindowSizeClass,
  selectedRoute: TopLevelRoute,
  onSelectRoute: (TopLevelRoute) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable (openNavigation: () -> Unit) -> Unit,
) {
  when (windowSizeClass) {
    MusicWindowSizeClass.Compact -> {
      val drawerState = rememberDrawerState(DrawerValue.Closed)
      val coroutineScope = rememberCoroutineScope()
      ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(
            modifier = Modifier.fillMaxWidth(fraction = 0.5f).testTag(COMPACT_NAVIGATION_TAG),
          ) {
            DrawerItems(
              selectedRoute = selectedRoute,
              onSelectRoute = { route ->
                onSelectRoute(route)
                coroutineScope.launch { drawerState.close() }
              },
            )
          }
        },
      ) {
        content { coroutineScope.launch { drawerState.open() } }
      }
    }
    MusicWindowSizeClass.Medium -> {
      PermanentNavigationDrawer(
        modifier = modifier,
        drawerContent = {
          NavigationRail(modifier = Modifier.fillMaxHeight().testTag(MEDIUM_NAVIGATION_TAG)) {
            MusicNavigationItems.forEach { item ->
              NavigationRailItem(
                selected = item.route == selectedRoute,
                onClick = { onSelectRoute(item.route) },
                modifier = Modifier.testTag("nav_${item.route.stableId}"),
                icon = { Text(stringResource(item.shortLabelRes)) },
                label = { Text(stringResource(item.labelRes)) },
                alwaysShowLabel = true,
              )
            }
          }
        },
      ) {
        content {}
      }
    }
    MusicWindowSizeClass.Expanded -> {
      PermanentNavigationDrawer(
        modifier = modifier,
        drawerContent = {
          PermanentDrawerSheet(
            modifier =
              Modifier.width(MusicTheme.dimensions.permanentNavigationWidth)
                .testTag(EXPANDED_NAVIGATION_TAG),
          ) {
            DrawerItems(selectedRoute = selectedRoute, onSelectRoute = onSelectRoute)
          }
        },
      ) {
        content {}
      }
    }
  }
}

const val COMPACT_NAVIGATION_TAG = "compact_navigation"
const val MEDIUM_NAVIGATION_TAG = "medium_navigation"
const val EXPANDED_NAVIGATION_TAG = "expanded_navigation"
const val OPEN_NAVIGATION_TAG = "open_navigation"

@Composable
private fun DrawerItems(
  selectedRoute: TopLevelRoute,
  onSelectRoute: (TopLevelRoute) -> Unit,
) {
  Column(
    modifier = Modifier.padding(vertical = MusicTheme.dimensions.cardSpacing),
  ) {
    MusicNavigationItems.forEach { item ->
      NavigationDrawerItem(
        label = { Text(stringResource(item.labelRes)) },
        selected = item.route == selectedRoute,
        onClick = { onSelectRoute(item.route) },
        modifier =
          Modifier.padding(horizontal = MusicTheme.dimensions.componentGrid)
            .testTag("nav_${item.route.stableId}"),
      )
    }
  }
}
