package com.musicapp.player.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme

enum class MusicStatePresentation {
  FullScreen,
  Inline,
}

@Composable
fun MusicLoadingState(
  modifier: Modifier = Modifier,
  presentation: MusicStatePresentation = MusicStatePresentation.FullScreen,
  @StringRes messageRes: Int = R.string.state_loading,
) {
  val message = stringResource(messageRes)
  if (presentation == MusicStatePresentation.Inline) {
    LinearProgressIndicator(
      modifier = modifier.fillMaxWidth().semantics { contentDescription = message },
    )
    return
  }
  MusicStatePane(modifier = modifier, titleRes = messageRes) {
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = message })
  }
}

@Composable
fun MusicEmptyState(
  @StringRes titleRes: Int,
  modifier: Modifier = Modifier,
  @StringRes messageRes: Int? = null,
) {
  MusicStatePane(modifier = modifier, titleRes = titleRes, messageRes = messageRes)
}

@Composable
fun MusicErrorState(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  @StringRes titleRes: Int = R.string.state_error_title,
  @StringRes messageRes: Int? = null,
  presentation: MusicStatePresentation = MusicStatePresentation.FullScreen,
) {
  MusicStatePane(
    modifier = modifier,
    titleRes = titleRes,
    messageRes = messageRes,
    fillAvailableSpace = presentation == MusicStatePresentation.FullScreen,
    action = {
      Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    },
  )
}

@Composable
private fun MusicStatePane(
  @StringRes titleRes: Int,
  modifier: Modifier = Modifier,
  @StringRes messageRes: Int? = null,
  fillAvailableSpace: Boolean = true,
  action: (@Composable () -> Unit)? = null,
  indicator: (@Composable () -> Unit)? = null,
) {
  val dimensions = MusicTheme.dimensions
  val typography = MusicTheme.typography
  Column(
    modifier =
      modifier
        .then(if (fillAvailableSpace) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
        .padding(dimensions.horizontalPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    indicator?.invoke()
    if (indicator != null) Spacer(Modifier.height(dimensions.cardSpacing))
    Text(
      text = stringResource(titleRes),
      color = MaterialTheme.colorScheme.onSurface,
      style = typography.stateTitle,
    )
    messageRes?.let {
      Spacer(Modifier.height(dimensions.componentGrid))
      Text(
        text = stringResource(it),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = typography.stateBody,
      )
    }
    action?.let {
      Spacer(Modifier.height(dimensions.cardSpacing))
      it()
    }
  }
}

@Composable
fun MusicScaffold(
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  miniPlayer: @Composable () -> Unit = {},
  content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { MusicSnackbarHost(snackbarHostState) },
    bottomBar = miniPlayer,
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) { content(innerPadding) }
  }
}

@Composable
fun MusicSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(hostState = hostState, modifier = modifier)
}
