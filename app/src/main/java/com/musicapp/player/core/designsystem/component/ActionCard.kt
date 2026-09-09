package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme

internal enum class ActionCardStatus {
    Normal,
    Success,
    Warning,
}

@Composable
internal fun ActionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconResId: Int? = null,
    trailingIconResId: Int? = null,
    status: ActionCardStatus = ActionCardStatus.Normal,
    enabled: Boolean = true,
    minHeight: Dp? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    titleStyle: TextStyle? = null,
    onClick: (() -> Unit)? = null,
) {
    val dimensions = MusicTheme.dimensions
    val colors = MusicTheme.colors
    val resolvedShape = shape ?: MusicTheme.shapes.large
    val resolvedContainerColor = containerColor ?: when (status) {
        ActionCardStatus.Normal -> MusicTheme.aeroCardContainerColor
        ActionCardStatus.Success -> colors.primaryContainer
        ActionCardStatus.Warning -> colors.tertiaryContainer
    }
    val resolvedContentColor = contentColor ?: when (status) {
        ActionCardStatus.Normal -> colors.primary
        ActionCardStatus.Success -> colors.onPrimaryContainer
        ActionCardStatus.Warning -> colors.onTertiaryContainer
    }
    val contentAlpha = if (enabled || onClick == null) 1f else MusicAlpha.Disabled
    val effectiveContentColor = resolvedContentColor.copy(alpha = contentAlpha)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = minHeight ?: dimensions.minimumTouchTarget)
                .padding(horizontal = dimensions.spaceMedium, vertical = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            iconResId?.let { icon ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = effectiveContentColor,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                Text(
                    text = title,
                    color = effectiveContentColor,
                    style = titleStyle ?: MusicTheme.typography.titleMedium,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                        style = MusicTheme.typography.bodySmall,
                    )
                }
            }
            trailingIconResId?.let { icon ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = resolvedShape,
            color = resolvedContainerColor,
            contentColor = effectiveContentColor,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = resolvedShape,
            color = resolvedContainerColor,
            contentColor = effectiveContentColor,
            content = content,
        )
    }
}
