package com.musicapp.player.core.designsystem.snackbar

import com.musicapp.player.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnackbarQueueTest {
  @Test
  fun singleRequestIsExposedImmediately() {
    val queue = SnackbarQueue()

    val id = queue.enqueue(R.string.loading)

    assertEquals(id, queue.current.value?.id)
    assertEquals(R.string.loading, queue.current.value?.messageResId)
  }

  @Test
  fun requestsArePresentedInFirstInFirstOutOrder() {
    val queue = SnackbarQueue()
    val first = queue.enqueue(R.string.loading)
    val second = queue.enqueue(R.string.empty_state_title)
    val third = queue.enqueue(R.string.error_state_title)

    assertEquals(first, queue.current.value?.id)
    queue.dismiss(first)
    assertEquals(second, queue.current.value?.id)
    queue.dismiss(second)
    assertEquals(third, queue.current.value?.id)
  }

  @Test
  fun dismissAndActionAdvanceQueue() {
    val actions = mutableListOf<String>()
    val queue = SnackbarQueue()
    val first = queue.enqueue(R.string.loading)
    val second =
      queue.enqueue(
        messageResId = R.string.error_state_title,
        actionLabelResId = R.string.retry,
        onAction = { actions += "retry" },
      )
    val third = queue.enqueue(R.string.empty_state_title)

    queue.dismiss(first)
    assertEquals(second, queue.current.value?.id)
    queue.performAction(second)

    assertEquals(listOf("retry"), actions)
    assertEquals(third, queue.current.value?.id)
  }

  @Test
  fun completingOnlyRequestLeavesQueueEmpty() {
    val queue = SnackbarQueue()
    val id = queue.enqueue(R.string.loading)

    queue.dismiss(id)

    assertNull(queue.current.value)
    queue.dismiss()
    queue.performAction()
    assertNull(queue.current.value)
  }

  @Test
  fun concurrentProducersDoNotLoseRequests() {
    val queue = SnackbarQueue()
    val enqueuedIds = ConcurrentHashMap.newKeySet<Long>()
    val executor = Executors.newFixedThreadPool(4)
    repeat(100) {
      executor.execute { enqueuedIds += queue.enqueue(R.string.loading) }
    }
    executor.shutdown()

    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    val displayedIds = mutableSetOf<Long>()
    while (queue.current.value != null) {
      val currentId = checkNotNull(queue.current.value?.id)
      displayedIds += currentId
      queue.dismiss(currentId)
    }

    assertEquals(100, enqueuedIds.size)
    assertEquals(enqueuedIds, displayedIds)
  }
}
