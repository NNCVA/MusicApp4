package com.musicapp.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentBottomPaddingPolicyTest {

    private val navBarBottom = 24.dp
    private val miniPlayerHeight = 64.dp
    private val spaceMedium = 16.dp
    private val bubbleLift = 8.dp

    @Test
    fun contentBottomPadding_withoutKeyboard_reservesMiniPlayerAndNavBar() {
        val result = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 0.dp,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
        )
        assertEquals(88.dp, result)
    }

    @Test
    fun contentBottomPadding_withSmallKeyboard_stillPreservesMiniPlayerAndNavBar() {
        val result = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 50.dp,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
        )
        // 88.dp > 50.dp, so it preserves 88.dp
        assertEquals(88.dp, result)
    }

    @Test
    fun contentBottomPadding_withKeyboardAboveMiniPlayer_matchesKeyboardWithZeroDeadGap() {
        val keyboardHeight = 320.dp
        val result = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = keyboardHeight,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
        )
        // When keyboard is 320.dp, list avoids keyboard with exact 320.dp (no 64dp extra dead gap)
        assertEquals(keyboardHeight, result)
    }

    @Test
    fun contentBottomPadding_whenPlayerNotVisible_reservesOnlyNavBarOrKeyboard() {
        val withoutIme = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 0.dp,
            isPlayerVisible = false,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
        )
        assertEquals(navBarBottom, withoutIme)

        val withIme = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 280.dp,
            isPlayerVisible = false,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
        )
        assertEquals(280.dp, withIme)
    }

    @Test
    fun contentBottomPadding_whenSidebarFree_ignoresMiniPlayer() {
        val result = resolveContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 0.dp,
            isPlayerVisible = true,
            isSidebarFree = true,
            miniPlayerHeight = miniPlayerHeight,
        )
        assertEquals(navBarBottom, result)
    }

    @Test
    fun bubbleBottomPadding_floatsAboveMiniPlayerWhenKeyboardClosed() {
        val result = resolveBubbleBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 0.dp,
            isPlayerVisible = true,
            miniPlayerHeight = miniPlayerHeight,
            spaceMedium = spaceMedium,
            messageBubbleBottomLift = bubbleLift,
        )
        // (64 + 24) + 8 = 96.dp
        assertEquals(96.dp, result)
    }

    @Test
    fun bubbleBottomPadding_floatsAboveKeyboardWhenKeyboardOpen() {
        val keyboardHeight = 300.dp
        val result = resolveBubbleBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = keyboardHeight,
            isPlayerVisible = true,
            miniPlayerHeight = miniPlayerHeight,
            spaceMedium = spaceMedium,
            messageBubbleBottomLift = bubbleLift,
        )
        // max(88, 300) + 8 = 308.dp
        assertEquals(308.dp, result)
    }

    @Test
    fun dynamicContentBottomPadding_inSelectionMode_withoutKeyboard_reservesSelectionBarAndMiniPlayer() {
        val selectionBarHeight = 48.dp
        val result = resolveDynamicContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = 0.dp,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
            isSelectionMode = true,
            selectionBarHeight = selectionBarHeight,
        )
        // persistentBottom (24 + 64 = 88) + selectionBarHeight (48) = 136.dp
        assertEquals(136.dp, result)
    }

    @Test
    fun dynamicContentBottomPadding_inSelectionMode_withKeyboard_ignoresKeyboardAndStaysAnchored() {
        val selectionBarHeight = 48.dp
        val keyboardHeight = 320.dp
        val result = resolveDynamicContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = keyboardHeight,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
            isSelectionMode = true,
            selectionBarHeight = selectionBarHeight,
        )
        // In selection mode, list content padding is anchored to selection bar and completely ignores IME keyboard
        assertEquals(136.dp, result)
    }

    @Test
    fun dynamicContentBottomPadding_notInSelectionMode_followsAdr0026MaxRule() {
        val selectionBarHeight = 48.dp
        val keyboardHeight = 320.dp
        val result = resolveDynamicContentBottomPadding(
            systemBottomInset = navBarBottom,
            imeBottomInset = keyboardHeight,
            isPlayerVisible = true,
            isSidebarFree = false,
            miniPlayerHeight = miniPlayerHeight,
            isSelectionMode = false,
            selectionBarHeight = selectionBarHeight,
        )
        // Not in selection mode -> max(88.dp, 320.dp) = 320.dp
        assertEquals(320.dp, result)
    }
}
