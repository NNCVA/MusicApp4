package com.musicapp.player.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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

        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            fun openDrawer() {
                scope.launch { drawerState.open() }
            }
            fun closeDrawer() {
                scope.launch { drawerState.close() }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerGesturesEnabled,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(maxWidth * policy.drawerFraction),
                    ) {
                        navigationContent(policy, ::closeDrawer)
                    }
                },
                content = {
                    ShellBody(
                        contentInsets = contentInsets,
                        contentBottomPadding = if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                        content = { insets -> content(insets, policy, ::openDrawer) },
                        playerSheetContent = playerSheetContent,
                    )
                },
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (policy == WindowLayoutPolicy.MEDIUM_RAIL) {
                    Box(modifier = Modifier.fillMaxHeight()) {
                        navigationContent(policy) {}
                    }
                } else {
                    Box(
                        modifier =
                            Modifier.width(dimensions.permanentSidebarWidth).fillMaxHeight(),
                    ) {
                        navigationContent(policy) {}
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ShellBody(
                        contentInsets = contentInsets,
                        contentBottomPadding = if (playerSheetVisible) dimensions.miniPlayerHeight else 0.dp,
                        content = { insets -> content(insets, policy) {} },
                        playerSheetContent = playerSheetContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellBody(
    contentInsets: WindowInsets,
    contentBottomPadding: Dp,
    content: @Composable (WindowInsets) -> Unit,
    playerSheetContent: @Composable (WindowInsets) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = contentBottomPadding)) {
            content(contentInsets)
        }

        // The player sheet belongs to the app shell rather than a navigation
        // destination.  No inset modifier is applied here; the slot owns it.
        Box(
            modifier = Modifier.fillMaxSize().zIndex(1f),
            propagateMinConstraints = true,
        ) {
            playerSheetContent(contentInsets)
        }
    }
}

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
