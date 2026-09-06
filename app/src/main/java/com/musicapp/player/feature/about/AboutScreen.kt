package com.musicapp.player.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun AboutScreenRoute(
    viewModel: AboutViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AboutScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onShowLicenses = viewModel::showLicenses,
        onDismissLicenses = viewModel::dismissLicenses,
        bottomPadding = bottomPadding,
    )
}

@Composable
fun AboutScreen(
    state: AboutUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onShowLicenses: () -> Unit,
    onDismissLicenses: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    val listState = rememberLazyListState()
    val overscrollEffect = rememberBounceOverscrollEffect(listState)
    Box(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().widthIn(max = dimensions.settingsContentMaxWidth)) {
                CategoryHeader(
                    title = stringResource(R.string.navigation_about),
                    policy = policy,
                    navigationAction = CategoryNavigationAction.BACK,
                    onNavigationClick = onBack,
                )
            }
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .widthIn(max = dimensions.settingsContentMaxWidth)
                    .bounceOverscroll(overscrollEffect),
                contentPadding = PaddingValues(
                    bottom = dimensions.spaceMedium + bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
            if (state.loadFailed) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        Text(
                            text = stringResource(R.string.about_load_failed),
                            color = MusicTheme.colors.error,
                            style = MusicTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            state.metadata?.let { metadata ->
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        AboutSection(
                            title =
                                stringResource(
                                    R.string.about_version_format,
                                    metadata.versionName,
                                    metadata.versionCode,
                                ),
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        AboutSection(
                            title = stringResource(R.string.about_developer),
                            body = stringResource(R.string.about_developer_name),
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        AboutSection(
                            title = stringResource(R.string.about_acknowledgements),
                            body = stringResource(R.string.about_acknowledgements_body),
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                    ) {
                        HorizontalDivider()
                        TextButton(onClick = onShowLicenses) {
                            Text(stringResource(R.string.about_open_source_licenses))
                        }
                    }
                }
            }
        }
    }
    }
    val metadata = state.metadata
    if (state.isLicenseVisible && metadata != null) {
        AlertDialog(
            onDismissRequest = onDismissLicenses,
            title = { Text(stringResource(R.string.about_open_source_licenses)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = dimensions.dialogListMaxHeight)) {
                    item { Text(metadata.openSourceLicenseText, style = MusicTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissLicenses) {
                    Text(stringResource(R.string.about_license_close))
                }
            },
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: String? = null,
) {
    val dimensions = MusicTheme.dimensions
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall)) {
        Text(
            text = title,
            style = MusicTheme.typography.titleLarge,
            color = MusicTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        body?.let {
            Text(
                text = it,
                color = MusicTheme.colors.onSurfaceVariant,
                style = MusicTheme.typography.bodyLarge,
            )
        }
    }
}
