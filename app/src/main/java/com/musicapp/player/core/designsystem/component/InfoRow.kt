package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme

@Composable
internal fun InfoRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minHeight: Dp? = null,
    contentPadding: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingValue: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleMaxLines: Int? = null,
    subtitleMaxLines: Int? = null,
) {
    val dimensions = MusicTheme.dimensions
    val paddingValues = contentPadding ?: PaddingValues(
        horizontal = dimensions.spaceMedium,
        vertical = dimensions.spaceSmall,
    )
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = minHeight ?: dimensions.minimumTouchTarget)
        .let { base ->
            if (onClick != null) {
                base
                    .clip(MusicTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onClick)
            } else {
                base
            }
        }
        .padding(paddingValues)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
        ) {
            Text(
                text = title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = titleMaxLines ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MusicTheme.typography.bodySmall,
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = subtitleMaxLines ?: Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            supportingContent?.invoke(this)
        }
        trailingValue?.let {
            Text(
                text = it,
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        trailingContent?.invoke()
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_common_chevron_right),
                contentDescription = null,
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
        }
    }
}
