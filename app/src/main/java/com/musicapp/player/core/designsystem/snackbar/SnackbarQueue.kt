package com.musicapp.player.core.designsystem.snackbar

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A resource-backed snackbar request waiting to be presented by the application host. */
data class SnackbarRequest(
  val id: Long,
  @param:StringRes val messageResId: Int,
  val messageFormatArgs: List<Any> = emptyList(),
  @param:StringRes val actionLabelResId: Int? = null,
  val onAction: (() -> Unit)? = null,
)

/**
 * Serializes snackbar requests so that hosts only ever observe one request at a time.
 *
 * All queue transitions are guarded by the same monitor. This keeps concurrent producers ordered
 * by the point at which their [enqueue] call acquires the monitor and makes completing the current
 * request plus selecting its successor one atomic operation.
 */
class SnackbarQueue {
  private val lock = Any()
  private var nextId = 0L
  private val pending = ArrayDeque<SnackbarRequest>()
  private val _current = MutableStateFlow<SnackbarRequest?>(null)

  val current: StateFlow<SnackbarRequest?> = _current.asStateFlow()

  fun enqueue(
    @StringRes messageResId: Int,
    messageFormatArgs: List<Any> = emptyList(),
    @StringRes actionLabelResId: Int? = null,
    onAction: (() -> Unit)? = null,
  ): Long {
    val formatArgs = messageFormatArgs.toList()
    return synchronized(lock) {
      val request =
        SnackbarRequest(
          id = ++nextId,
          messageResId = messageResId,
          messageFormatArgs = formatArgs,
          actionLabelResId = actionLabelResId,
          onAction = onAction,
        )
      if (_current.value == null) {
        _current.value = request
      } else {
        pending.addLast(request)
      }
      request.id
    }
  }

  fun dismiss(requestId: Long? = current.value?.id) {
    if (requestId == null) return
    synchronized(lock) {
      if (_current.value?.id == requestId) advanceLocked()
    }
  }

  fun performAction(requestId: Long? = current.value?.id) {
    if (requestId == null) return
    val action =
      synchronized(lock) {
        val active = _current.value
        if (active?.id != requestId) return
        advanceLocked()
        active.onAction
      }
    action?.invoke()
  }

  private fun advanceLocked() {
    _current.value = pending.removeFirstOrNull()
  }
}
