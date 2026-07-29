package com.musicapp.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme

@Immutable
data class MiniPlayerState(
  val title: String,
  val artist: String,
  val isPlaying: Boolean,
  val controlsEnabled: Boolean = true,
)

@Composable
fun MiniPlayerPlaceholder(
  state: MiniPlayerState,
  onOpenPlayer: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  artwork: @Composable () -> Unit = {},
) {
  val dimensions = MusicTheme.dimensions
  val typography = MusicTheme.typography
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .height(dimensions.miniPlayerHeight)
        .clickable(onClick = onOpenPlayer),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.componentGrid * 2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(dimensions.miniPlayerHeight - dimensions.componentGrid * 4)
            .clip(MusicTheme.shapes.artwork),
      ) {
        artwork()
      }
      Spacer(Modifier.width(dimensions.componentGrid * 2))
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .padding(vertical = dimensions.componentGrid * 2),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = state.title,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface,
          style = typography.trackTitle,
        )
        Text(
          text = state.artist,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = typography.trackArtist,
        )
      }
      val playPauseLabel = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play)
      val nextLabel = stringResource(R.string.action_next)
      TextButton(
        onClick = onPlayPause,
        modifier =
          Modifier.height(dimensions.minimumTouchTarget)
            .widthIn(min = dimensions.minimumTouchTarget)
            .semantics { contentDescription = playPauseLabel },
        enabled = state.controlsEnabled,
      ) {
        Text(playPauseLabel)
      }
      TextButton(
        onClick = onNext,
        modifier =
          Modifier.height(dimensions.minimumTouchTarget)
            .widthIn(min = dimensions.minimumTouchTarget)
            .semantics { contentDescription = nextLabel },
        enabled = state.controlsEnabled,
      ) {
        Text(nextLabel)
      }
    }
  }
}
