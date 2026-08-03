package com.musicapp.player.ui.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.musicapp.player.theme.MusicDimensions

/**
 * The navigation shell used for a window width.  This type intentionally has no
 * Compose dependencies beyond [Dp], so callers can resolve it before building
 * their UI (and tests can exercise the breakpoints without a device).
 */
@Immutable
enum class WindowLayoutPolicy(
    val drawerFraction: Float = 0f,
    val sidebarWidth: Dp = Dp.Unspecified,
) {
    COMPACT_DRAWER(drawerFraction = 0.5f),
    MEDIUM_SIDEBAR(sidebarWidth = MusicDimensions.Medium.mediumSidebarWidth),
    EXPANDED_SIDEBAR(sidebarWidth = MusicDimensions.Expanded.permanentSidebarWidth),

    ;

    companion object {
        /** Resolves the shell policy at the exact Material window breakpoints. */
        fun forWidth(width: Dp): WindowLayoutPolicy =
            when {
                width < MusicDimensions.Compact.compactWidthBreakpoint -> COMPACT_DRAWER
                width < MusicDimensions.Compact.expandedWidthBreakpoint -> MEDIUM_SIDEBAR
                else -> EXPANDED_SIDEBAR
            }

        /** Alias that reads naturally at call sites that resolve a window policy. */
        fun resolve(width: Dp): WindowLayoutPolicy = forWidth(width)
    }
}
