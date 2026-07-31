package com.musicapp.player.core.designsystem.snackbar

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A resource-backed message bubble request presented by the application host. */
data class MessageBubbleRequest(
  val id: Long,
  @param:StringRes val messageResId: Int,
  val messageFormatArgs: List<Any> = emptyList(),
  @param:StringRes val actionLabelResId: Int? = null,
  val onAction: (() -> Unit)? = null,
)

/**
 * Holds the single message bubble currently visible to the application.
 *
 * A new request replaces the current request immediately. There is no pending queue, so an older
 * bubble can never reappear after a newer message has been shown.
 */
class MessageBubbleQueue {
  private val lock = Any()
  private var nextId = 0L
  private val _current = MutableStateFlow<MessageBubbleRequest?>(null)

  val current: StateFlow<MessageBubbleRequest?> = _current.asStateFlow()

  fun enqueue(
    @StringRes messageResId: Int,
    messageFormatArgs: List<Any> = emptyList(),
    @StringRes actionLabelResId: Int? = null,
    onAction: (() -> Unit)? = null,
  ): Long {
    val formatArgs = messageFormatArgs.toList()
    return synchronized(lock) {
      val request =
        MessageBubbleRequest(
          id = ++nextId,
          messageResId = messageResId,
          messageFormatArgs = formatArgs,
          actionLabelResId = actionLabelResId,
          onAction = onAction,
        )
      _current.value = request
      request.id
    }
  }

  fun dismiss(requestId: Long? = current.value?.id) {
    if (requestId == null) return
    synchronized(lock) {
      if (_current.value?.id == requestId) _current.value = null
    }
  }

  fun performAction(requestId: Long? = current.value?.id) {
    if (requestId == null) return
    val action =
      synchronized(lock) {
        val active = _current.value
        if (active?.id != requestId) return
        _current.value = null
        active.onAction
      }
    action?.invoke()
  }
}
