package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme

@Composable
fun LoadingState(
  modifier: Modifier = Modifier,
  message: String = stringResource(R.string.loading),
) {
  val dimensions = MusicTheme.dimensions
  Column(
    modifier = modifier.fillMaxSize().padding(dimensions.spaceLarge),
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

@Composable
fun EmptyState(
  modifier: Modifier = Modifier,
  title: String = stringResource(R.string.empty_state_title),
  description: String? = null,
) {
  StateMessage(
    modifier = modifier,
    title = title,
    description = description,
  )
}

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
  ) {
    TextButton(
      onClick = onRetry,
      modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
      shape = MusicTheme.shapes.small,
    ) {
      Text(text = retryLabel, style = MusicTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun StateMessage(
  title: String,
  description: String?,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  val dimensions = MusicTheme.dimensions
  Box(modifier = modifier.fillMaxSize().padding(dimensions.spaceLarge), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
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
      action?.invoke()
    }
  }
}
