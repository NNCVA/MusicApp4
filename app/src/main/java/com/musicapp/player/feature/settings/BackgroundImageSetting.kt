package com.musicapp.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.ChoiceRow
import com.musicapp.player.core.designsystem.component.CircularRippleIconButton
import com.musicapp.player.data.background.BackgroundState
import com.musicapp.player.theme.MusicTheme

@Composable
internal fun BackgroundImageSetting(
    background: BackgroundState,
    enabled: Boolean,
    onChoose: () -> Unit,
    onDelete: () -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_background_choose),
        subtitle = stringResource(
            when {
                background.isUnavailable -> R.string.settings_background_unavailable
                background.hasSelection -> R.string.settings_background_selected
                else -> R.string.settings_background_default
            },
        ),
        enabled = enabled,
        interactionModifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onChoose),
        trailingContent = {
            if (background.hasSelection) {
                CircularRippleIconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.size(MusicTheme.dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_delete),
                        contentDescription = stringResource(R.string.settings_background_delete),
                        tint = MusicTheme.colors.onSurface,
                        modifier = Modifier.size(MusicTheme.dimensions.statusIndicatorSize),
                    )
                }
            }
        },
    )
}
