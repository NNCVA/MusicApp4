package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme

/**
 * Shared visual shell for settings choices.
 *
 * The caller owns the interaction modifier so each page can keep its existing
 * selectable/toggleable/slider semantics and business callback.
 */
@Composable
internal fun ChoiceRow(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    minHeight: Dp? = null,
    interactionModifier: Modifier = Modifier,
    mergeDescendants: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimensions = MusicTheme.dimensions
    val contentAlpha = if (enabled) 1f else MusicAlpha.Disabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight ?: dimensions.minimumTouchTarget)
            .clip(MusicTheme.shapes.medium)
            .then(interactionModifier)
            .semantics(mergeDescendants = mergeDescendants) {}
            .padding(horizontal = dimensions.spaceExtraSmall, vertical = dimensions.spaceSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        Column(
            modifier = Modifier.weight(1f).padding(MusicTheme.dimensions.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MusicTheme.typography.titleMedium,
                    color = MusicTheme.colors.onSurface.copy(alpha = contentAlpha),
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MusicTheme.typography.bodySmall,
                    color = MusicTheme.colors.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            supportingContent?.invoke(this)
        }
        trailingContent()
    }
}
