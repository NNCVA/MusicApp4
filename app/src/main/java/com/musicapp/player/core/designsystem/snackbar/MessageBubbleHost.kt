package com.musicapp.player.core.designsystem.snackbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.delay

private const val MESSAGE_BUBBLE_DURATION_MS = 4_000L
private const val MESSAGE_BUBBLE_FADE_DURATION_MS = 200

private data class MessageBubblePresentation(
  val request: MessageBubbleRequest,
  val message: String,
)

/** Renders the application's one white, centered, capsule-shaped message bubble. */
@Composable
fun MessageBubbleHost(
  request: MessageBubbleRequest?,
  message: String?,
  modifier: Modifier = Modifier,
  onDismiss: (Long) -> Unit,
  onAction: (Long) -> Unit,
) {
  val requestId = request?.id
  LaunchedEffect(requestId) {
    if (requestId == null) return@LaunchedEffect
    delay(MESSAGE_BUBBLE_DURATION_MS)
    onDismiss(requestId)
  }

  val dimensions = MusicTheme.dimensions
  val presentation =
    if (request != null && message != null) {
      MessageBubblePresentation(request, message)
    } else {
      null
    }
  AnimatedContent(
    targetState = presentation,
    transitionSpec = {
      fadeIn(animationSpec = tween(MESSAGE_BUBBLE_FADE_DURATION_MS)) togetherWith
        fadeOut(animationSpec = tween(MESSAGE_BUBBLE_FADE_DURATION_MS))
    },
    modifier =
      modifier
        .padding(horizontal = dimensions.spaceMedium)
        .widthIn(max = dimensions.messageBubbleMaxWidth),
    label = "messageBubble",
  ) {
    it?.let { bubble ->
      Surface(
        shape = MusicTheme.shapes.pill,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = dimensions.messageBubbleElevation,
      ) {
        Column(
          modifier =
            Modifier.padding(
              horizontal = dimensions.messageBubbleHorizontalPadding,
              vertical = dimensions.messageBubbleVerticalPadding,
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
          Text(
            text = bubble.message,
            textAlign = TextAlign.Center,
            style = MusicTheme.typography.bodyMedium,
          )
          bubble.request.actionLabelResId?.let { actionLabelResId ->
            TextButton(onClick = { onAction(bubble.request.id) }) {
              Text(stringResource(actionLabelResId))
            }
          }
        }
      }
    }
  }
}
