package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme

/** Displays a message dialog with a single confirmation action. */
@Composable
fun MessageDialog(
  message: String,
  confirmLabel: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dimensions = MusicTheme.dimensions
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = modifier,
    shape = MusicTheme.shapes.extraLarge,
    text = {
      Text(
        text = message,
        style = MusicTheme.typography.bodyLarge,
      )
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = dimensions.minimumTouchTarget),
        shape = MusicTheme.shapes.small,
      ) {
        Text(text = confirmLabel, style = MusicTheme.typography.labelLarge)
      }
    },
  )
}

@Preview(showBackground = true)
@Composable
private fun MessageDialogPreview() {
  MusicAppTheme {
    MessageDialog(
      message = stringResource(R.string.batch_result_failed),
      confirmLabel = stringResource(R.string.selection_close),
      onDismiss = {},
    )
  }
}
