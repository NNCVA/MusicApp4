package com.musicapp.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme

/** Displays a centered loading indicator and message. */
@Composable
fun LoadingState(
  modifier: Modifier = Modifier,
  message: String = stringResource(R.string.loading),
) {
  val dimensions = MusicTheme.dimensions
  Column(
    modifier =
      modifier.fillMaxSize().padding(dimensions.spaceLarge).semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
      },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium, Alignment.CenterVertically),
  ) {
    CircularProgressIndicator(color = MusicTheme.colors.primary)
    Text(
      text = message,
      color = MusicTheme.colors.onSurfaceVariant,
      style = MusicTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )
  }
}

/** Displays an empty-content message, or a centered action button when an action is present. */
@Composable
fun EmptyState(
  modifier: Modifier = Modifier,
  title: String = stringResource(R.string.empty_state_title),
  description: String? = null,
  actionLabel: String? = null,
  @DrawableRes actionIconRes: Int? = null,
  onAction: (() -> Unit)? = null,
) {
  val dimensions = MusicTheme.dimensions
  if (actionLabel != null && onAction != null) {
    Box(
      modifier =
        modifier.fillMaxSize().padding(dimensions.spaceLarge).semantics {
          liveRegion = LiveRegionMode.Polite
        },
      contentAlignment = Alignment.Center,
    ) {
      OutlinedButton(
        onClick = onAction,
        modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
        shape = MusicTheme.shapes.pill,
      ) {
        if (actionIconRes != null) {
          Icon(
            painter = painterResource(actionIconRes),
            contentDescription = null,
            modifier = Modifier.size(dimensions.spaceLarge),
          )
          Spacer(modifier = Modifier.width(dimensions.spaceSmallMedium))
        }
        Text(text = actionLabel, style = MusicTheme.typography.labelLarge)
      }
    }
  } else {
    StateMessage(
      modifier = modifier,
      title = title,
      description = description,
    )
  }
}

/** Displays an error message with an action to retry the failed operation. */
@Composable
fun ErrorState(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = stringResource(R.string.error_state_title),
  description: String? = null,
  retryLabel: String = stringResource(R.string.retry),
) {
  val dimensions = MusicTheme.dimensions
  StateMessage(
    modifier = modifier,
    title = title,
    description = description,
    trailingAction = {
      TextButton(
        onClick = onRetry,
        modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
        shape = MusicTheme.shapes.small,
      ) {
        Text(text = retryLabel, style = MusicTheme.typography.labelLarge)
      }
    },
  )
}

@Composable
private fun StateMessage(
  title: String,
  description: String?,
  modifier: Modifier = Modifier,
  leadingAction: (@Composable () -> Unit)? = null,
  trailingAction: (@Composable () -> Unit)? = null,
) {
  val dimensions = MusicTheme.dimensions
  Box(modifier = modifier.fillMaxSize().padding(dimensions.spaceLarge), contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.semantics(mergeDescendants = leadingAction == null && trailingAction == null) {
        liveRegion = LiveRegionMode.Polite
      },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
      leadingAction?.invoke()
      Text(
        text = title,
        color = MusicTheme.colors.onSurface,
        style = MusicTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      if (description != null) {
        Text(
          text = description,
          color = MusicTheme.colors.onSurfaceVariant,
          style = MusicTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
      }
      trailingAction?.invoke()
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
  MusicAppTheme { LoadingState() }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
  MusicAppTheme { EmptyState() }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStateWithActionPreview() {
  MusicAppTheme {
    EmptyState(
      title = "No tracks found",
      description = "Scan again after adding supported audio files.",
      actionLabel = "Scan music",
      actionIconRes = R.drawable.ic_sidebar_scan,
      onAction = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
  MusicAppTheme { ErrorState(onRetry = {}) }
}
