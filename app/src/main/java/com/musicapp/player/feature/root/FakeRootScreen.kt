package com.musicapp.player.feature.root

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.musicapp.player.AboutRoute
import com.musicapp.player.AlbumsRoute
import com.musicapp.player.ArtistsRoute
import com.musicapp.player.FoldersRoute
import com.musicapp.player.HistoryRoute
import com.musicapp.player.PlaylistsRoute
import com.musicapp.player.R
import com.musicapp.player.SettingsRoute
import com.musicapp.player.TopLevelRoute
import com.musicapp.player.TracksRoute
import com.musicapp.player.ui.components.MusicEmptyState
import com.musicapp.player.ui.components.MusicErrorState
import com.musicapp.player.ui.components.MusicLoadingState
import com.musicapp.player.theme.MusicTheme

enum class FakeRootState {
  Loading,
  Empty,
  Content,
  Error,
}

@Immutable
data class RootScreenSpec(
  @param:StringRes val titleRes: Int,
  @param:StringRes val emptyTitleRes: Int,
)

fun rootScreenSpec(route: TopLevelRoute): RootScreenSpec =
  when (route) {
    TracksRoute -> RootScreenSpec(R.string.nav_tracks, R.string.empty_tracks)
    AlbumsRoute -> RootScreenSpec(R.string.nav_albums, R.string.empty_albums)
    ArtistsRoute -> RootScreenSpec(R.string.nav_artists, R.string.empty_artists)
    PlaylistsRoute -> RootScreenSpec(R.string.nav_playlists, R.string.empty_playlists)
    HistoryRoute -> RootScreenSpec(R.string.nav_history, R.string.empty_history)
    FoldersRoute -> RootScreenSpec(R.string.nav_folders, R.string.empty_folders)
    SettingsRoute -> RootScreenSpec(R.string.nav_settings, R.string.empty_settings)
    AboutRoute -> RootScreenSpec(R.string.nav_about, R.string.empty_about)
  }

@Composable
fun FakeRootScreen(
  route: TopLevelRoute,
  state: FakeRootState,
  contentPadding: PaddingValues,
  onOpenNavigation: (() -> Unit)?,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spec = rootScreenSpec(route)
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .padding(horizontal = MusicTheme.dimensions.horizontalPadding),
  ) {
    Column {
      onOpenNavigation?.let { openNavigation ->
        TextButton(
          onClick = openNavigation,
          modifier = Modifier.testTag("open_navigation"),
        ) {
          Text(stringResource(R.string.action_open_navigation))
        }
      }
      Text(
        text = stringResource(spec.titleRes),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
      )
    }
    Spacer(Modifier.height(MusicTheme.dimensions.cardSpacing))
    when (state) {
      FakeRootState.Loading -> MusicLoadingState(modifier = Modifier.testTag(ROOT_STATE_TAG))
      FakeRootState.Empty ->
        MusicEmptyState(
          titleRes = spec.emptyTitleRes,
          messageRes = R.string.fake_page_explanation,
          modifier = Modifier.testTag(ROOT_STATE_TAG),
        )
      FakeRootState.Content -> FakeContent(modifier = Modifier.testTag(ROOT_STATE_TAG))
      FakeRootState.Error ->
        MusicErrorState(
          messageRes = R.string.fake_error_explanation,
          onRetry = onRetry,
          modifier = Modifier.testTag(ROOT_STATE_TAG),
        )
    }
  }
}

@Composable
private fun FakeContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = stringResource(R.string.fake_content_title),
      color = MaterialTheme.colorScheme.onSurface,
      style = MusicTheme.typography.stateTitle,
    )
    Spacer(Modifier.height(MusicTheme.dimensions.componentGrid))
    Text(
      text = stringResource(R.string.fake_page_explanation),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MusicTheme.typography.stateBody,
    )
  }
}

@Composable
fun DetailPlaceholderScreen(
  contentPadding: PaddingValues,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .padding(MusicTheme.dimensions.horizontalPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(stringResource(R.string.detail_placeholder), style = MusicTheme.typography.stateTitle)
    Spacer(Modifier.height(MusicTheme.dimensions.cardSpacing))
    Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
  }
}

const val ROOT_STATE_TAG = "root_state"
