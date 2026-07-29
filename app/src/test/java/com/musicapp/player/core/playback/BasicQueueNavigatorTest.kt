package com.musicapp.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasicQueueNavigatorTest {
    @Test
    fun emptyQueueHasNoNextOrPreviousItem() {
        assertNull(BasicQueueNavigator.nextIndex(currentIndex = 0, queueSize = 0))
        assertNull(BasicQueueNavigator.previousIndex(currentIndex = 0, queueSize = 0))
    }

    @Test
    fun singleItemQueueRepeatsTheOnlyItem() {
        assertEquals(0, BasicQueueNavigator.nextIndex(currentIndex = 0, queueSize = 1))
        assertEquals(0, BasicQueueNavigator.previousIndex(currentIndex = 0, queueSize = 1))
    }

    @Test
    fun nextWrapsFromQueueEndToStart() {
        assertEquals(2, BasicQueueNavigator.nextIndex(currentIndex = 1, queueSize = 3))
        assertEquals(0, BasicQueueNavigator.nextIndex(currentIndex = 2, queueSize = 3))
    }

    @Test
    fun previousWrapsFromQueueStartToEnd() {
        assertEquals(0, BasicQueueNavigator.previousIndex(currentIndex = 1, queueSize = 3))
        assertEquals(2, BasicQueueNavigator.previousIndex(currentIndex = 0, queueSize = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnIndexOutsideTheQueue() {
        BasicQueueNavigator.nextIndex(currentIndex = 3, queueSize = 3)
    }
}
