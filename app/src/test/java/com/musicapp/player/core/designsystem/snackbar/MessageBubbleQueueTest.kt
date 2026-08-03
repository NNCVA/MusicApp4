package com.musicapp.player.core.designsystem.snackbar

import com.musicapp.player.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubbleQueueTest {
  @Test
  fun singleRequestIsExposedImmediately() {
    val queue = MessageBubbleQueue()

    val id = queue.enqueue(R.string.loading)

    assertEquals(id, queue.current.value?.id)
    assertEquals(R.string.loading, queue.current.value?.messageResId)
  }

  @Test
  fun newRequestImmediatelyReplacesCurrentRequest() {
    val queue = MessageBubbleQueue()
    val first = queue.enqueue(R.string.loading)
    val second = queue.enqueue(R.string.empty_state_title)
    val third = queue.enqueue(R.string.error_state_title)

    assertEquals(third, queue.current.value?.id)
    assertEquals(R.string.error_state_title, queue.current.value?.messageResId)

    queue.dismiss(first)
    assertEquals(third, queue.current.value?.id)
    queue.dismiss(third)
    assertNull(queue.current.value)
    assertTrue(second < third)
  }

  @Test
  fun dismissAndActionOnlyAffectTheActiveRequest() {
    val actions = mutableListOf<String>()
    val queue = MessageBubbleQueue()
    val first =
      queue.enqueue(
        messageResId = R.string.loading,
        onAction = { actions += "old" },
      )
    val second =
      queue.enqueue(
        messageResId = R.string.error_state_title,
        actionLabelResId = R.string.retry,
        onAction = { actions += "retry" },
      )

    queue.performAction(first)
    assertEquals(second, queue.current.value?.id)
    assertTrue(actions.isEmpty())

    queue.performAction(second)
    assertEquals(listOf("retry"), actions)
    assertNull(queue.current.value)
  }

  @Test
  fun completingOnlyRequestLeavesQueueEmpty() {
    val queue = MessageBubbleQueue()
    val id = queue.enqueue(R.string.loading)

    queue.dismiss(id)

    assertNull(queue.current.value)
    queue.dismiss()
    queue.performAction()
    assertNull(queue.current.value)
  }

  @Test
  fun concurrentProducersLeaveOnlyTheLatestRequestVisible() {
    val queue = MessageBubbleQueue()
    val enqueuedIds = ConcurrentHashMap.newKeySet<Long>()
    val executor = Executors.newFixedThreadPool(4)
    repeat(100) {
      executor.execute { enqueuedIds += queue.enqueue(R.string.loading) }
    }
    executor.shutdown()

    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    assertEquals(enqueuedIds.maxOrNull(), queue.current.value?.id)

    queue.dismiss(queue.current.value?.id)
    assertNull(queue.current.value)
  }
}
