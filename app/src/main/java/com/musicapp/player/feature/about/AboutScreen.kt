package com.musicapp.player.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AboutScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onShowLicenses = viewModel::showLicenses,
        onDismissLicenses = viewModel::dismissLicenses,
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
) {
    val dimensions = MusicTheme.dimensions
    Box(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(contentInsets)
                .padding(horizontal = dimensions.contentHorizontalPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = dimensions.settingsContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        ) {
            item {
                CategoryHeader(
                    title = stringResource(R.string.navigation_about),
                    policy = policy,
                    navigationAction = CategoryNavigationAction.BACK,
                    onNavigationClick = onBack,
                )
            }
            if (state.loadFailed) {
                item {
                    Text(
                        text = stringResource(R.string.about_load_failed),
                        color = MusicTheme.colors.error,
                        style = MusicTheme.typography.bodyLarge,
                    )
                }
            }
            state.metadata?.let { metadata ->
                item {
                    AboutSection(
                        title =
                            stringResource(
                                R.string.about_version_format,
                                metadata.versionName,
                                metadata.versionCode,
                            ),
                    )
                }
                item {
                    AboutSection(
                        title = stringResource(R.string.about_developer),
                        body = stringResource(R.string.about_developer_name),
                    )
                }
                item {
                    AboutSection(
                        title = stringResource(R.string.about_acknowledgements),
                        body = stringResource(R.string.about_acknowledgements_body),
                    )
                }
                item {
                    HorizontalDivider()
                    TextButton(onClick = onShowLicenses) {
                        Text(stringResource(R.string.about_open_source_licenses))
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
