package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.musicapp.player.theme.MusicTheme

@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmallMedium),
    ) {
        Text(
            text = title,
            style = MusicTheme.typography.titleSmall,
            color = MusicTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimensions.spaceMedium),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MusicTheme.shapes.large,
            color = MusicTheme.aeroCardContainerColor,
            contentColor = MusicTheme.colors.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(dimensions.spaceMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
                content = content,
            )
        }
    }
}
