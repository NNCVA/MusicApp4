package com.musicapp.player.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.launch

/**
 * Shared application shell placeholder.
 *
 * The shell lays out the navigation slot and business content, then places the
 * application-level player sheet above both.  It deliberately does not apply
 * inset padding: [content] and [playerSheetContent] receive the insets so the
 * concrete screen that owns a list, toolbar, or sheet can consume them once.
 * While Mini is visible, the shell reserves its fixed height for every business
 * destination before placing the player layer above it.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    drawerGesturesEnabled: Boolean = true,
    playerSheetVisible: Boolean = false,
    navigationContent: @Composable (WindowLayoutPolicy, closeDrawer: () -> Unit) -> Unit = { _, _ -> },
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (WindowInsets, WindowLayoutPolicy, openDrawer: () -> Unit) -> Unit,
    playerSheetContent: @Composable (WindowInsets) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val policy = WindowLayoutPolicy.forWidth(maxWidth)
        val dimensions = MusicTheme.dimensions
        val availableWidth = maxWidth

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
                val drawerWidth = availableWidth * policy.drawerFraction
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }
                val isDrawerVisible by
                    remember(drawerState, drawerWidthPx) {
                        derivedStateOf {
                            compactDrawerProgress(
                                drawerOffsetPx = drawerState.currentOffset,
                                drawerWidthPx = drawerWidthPx,
                                fallbackOpen = drawerState.isOpen,
                            ) > 0f
                        }
                    }
                fun openDrawer() {
                    scope.launch { drawerState.open() }
                }
                fun closeDrawer() {
                    scope.launch { drawerState.close() }
                }

                DismissibleNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerGesturesEnabled,
                    drawerContent = {
                        DismissibleDrawerSheet(
                            drawerState = drawerState,
                            modifier = Modifier.width(drawerWidth),
                            drawerShape = RectangleShape,
                            drawerContainerColor = Color.Transparent,
                            drawerTonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            navigationContent(policy, ::closeDrawer)
                        }
                    },
                    content = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ShellContent(
                                contentInsets = contentInsets,
                                contentBottomPadding =
                                    if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                                content = { insets -> content(insets, policy, ::openDrawer) },
                            )
                            CompactPushDrawerScrim(
                                drawerState = drawerState,
                                drawerWidthPx = drawerWidthPx,
                                enabled = isDrawerVisible,
                                onClose = ::closeDrawer,
                            )
                        }
                    },
                )
                BackHandler(enabled = isDrawerVisible, onBack = ::closeDrawer)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.width(policy.sidebarWidth).fillMaxHeight(),
                    ) {
                        navigationContent(policy) {}
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ShellContent(
                            contentInsets = contentInsets,
                            contentBottomPadding =
                                if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                            content = { insets -> content(insets, policy) {} },
                        )
                    }
                }
            }

            // The application player sheet is above both navigation and content,
            // so Mini remains full-window-width while the compact drawer is open.
            Box(
                modifier = Modifier.fillMaxSize().zIndex(1f),
                propagateMinConstraints = true,
            ) {
                playerSheetContent(contentInsets)
            }
        }
    }
}

@Composable
private fun ShellContent(
    contentInsets: WindowInsets,
    contentBottomPadding: Dp,
    content: @Composable (WindowInsets) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding)) {
        content(contentInsets)
    }
}

@Composable
private fun CompactPushDrawerScrim(
    drawerState: DrawerState,
    drawerWidthPx: Float,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha =
                        compactDrawerProgress(
                            drawerOffsetPx = drawerState.currentOffset,
                            drawerWidthPx = drawerWidthPx,
                            fallbackOpen = drawerState.isOpen,
                        )
                }
                .background(Color.Black.copy(alpha = COMPACT_DRAWER_SCRIM_ALPHA))
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClose,
                ),
    )
}

internal fun compactDrawerProgress(
    drawerOffsetPx: Float,
    drawerWidthPx: Float,
    fallbackOpen: Boolean,
): Float {
    if (!drawerOffsetPx.isFinite() || !drawerWidthPx.isFinite() || drawerWidthPx <= 0f) {
        return if (fallbackOpen) 1f else 0f
    }
    return ((drawerOffsetPx + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
}

private const val COMPACT_DRAWER_SCRIM_ALPHA = 0.18f

/** A small named placeholder useful while the real player sheet is pending. */
@Composable
fun PlayerSheetPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) {
        content()
    }
}
