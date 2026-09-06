package com.musicapp.player.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.MessageDialog
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import com.musicapp.player.core.designsystem.component.rememberBounceOverscrollEffect
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
                    onBack = onBack,
                )
            }
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier =
                    Modifier.fillMaxWidth().weight(1f)
                        .widthIn(max = dimensions.settingsContentMaxWidth)
                        .bounceOverscroll(overscrollEffect),
                contentPadding =
                    PaddingValues(
                        bottom = dimensions.spaceMedium + bottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                if (state.loadFailed) {
                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
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
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = dimensions.contentHorizontalPadding),
                        ) {
                            AboutAppHeader(
                                versionName = metadata.versionName,
                                versionCode = metadata.versionCode,
                            )
                        }
                    }
                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = dimensions.contentHorizontalPadding),
                        ) {
                            AboutCard(
                                onShowLicenses = onShowLicenses,
                            )
                        }
                    }
                }
            }
        }
    }

    val metadata = state.metadata
    if (state.isLicenseVisible && metadata != null) {
        MessageDialog(
            title = stringResource(R.string.about_open_source_licenses),
            confirmLabel = stringResource(R.string.about_license_close),
            onDismiss = onDismissLicenses,
            onConfirm = onDismissLicenses,
        ) {
            LazyColumn(modifier = Modifier.heightIn(max = dimensions.dialogListMaxHeight)) {
                item {
                    Text(
                        text = metadata.openSourceLicenseText,
                        style = MusicTheme.typography.bodySmall,
                        color = MusicTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutAppHeader(
    versionName: String,
    versionCode: Long,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = dimensions.spaceLarge, bottom = dimensions.spaceMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MusicTheme.shapes.medium,
            color = MusicTheme.colors.surfaceContainerLowest,
            shadowElevation = 2.dp,
            modifier = Modifier.size(72.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Image(
                    painter = painterResource(R.mipmap.music2),
                    contentDescription = stringResource(R.string.app_name),
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(MusicTheme.shapes.small),
                )
            }
        }
        Spacer(modifier = Modifier.height(dimensions.spaceMedium))
        Text(
            text = stringResource(R.string.app_name),
            style = MusicTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MusicTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(dimensions.spaceExtraSmall))
        Text(
            text =
                stringResource(
                    R.string.about_version_format,
                    versionName,
                    versionCode,
                ),
            style = MusicTheme.typography.bodyMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCard(
    onShowLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimensions.spaceExtraSmall),
        ) {
            AboutInfoItem(
                title = stringResource(R.string.about_developer),
                body = stringResource(R.string.about_developer_name),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = dimensions.spaceMedium),
                color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
            )
            AboutInfoItem(
                title = stringResource(R.string.about_acknowledgements),
                body = stringResource(R.string.about_acknowledgements_body),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = dimensions.spaceMedium),
                color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
            )
            AboutActionItem(
                title = stringResource(R.string.about_open_source_licenses),
                onClick = onShowLicenses,
            )
        }
    }
}

@Composable
private fun AboutInfoItem(
    title: String,
    body: String,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        Text(
            text = title,
            style = MusicTheme.typography.titleMedium,
            color = MusicTheme.colors.onSurface,
        )
        Text(
            text = body,
            style = MusicTheme.typography.bodyMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutActionItem(
    title: String,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MusicTheme.typography.titleMedium,
            color = MusicTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_common_chevron_right),
            contentDescription = null,
            tint = MusicTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
