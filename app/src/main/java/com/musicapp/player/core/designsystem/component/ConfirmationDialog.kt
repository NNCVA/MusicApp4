package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme

/**
 * 通用二次确认对话框。
 *
 * 遵循设计系统规范：
 * - 标题约束为单行（超长省略），内容文本支持多行自适应；
 * - 底部取消与确认两个按钮各占左右一半宽度（平分），采用 [MusicTheme.shapes.pill] 胶囊形状；
 * - 确认按钮采用高视觉层级的实心 [Button]，支持常规主色与危险操作警示色（[isDestructive]）；
 * - 取消按钮采用带背景色的 [FilledTonalButton]，形成清晰的次级视觉背景；
 * - 按钮原生自带规范的 Material Ripple（水波纹）触控动效与 48dp 最小触控区域。
 */
@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = stringResource(R.string.dismiss),
    isDestructive: Boolean = false,
) {
    val dimensions = MusicTheme.dimensions
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MusicTheme.shapes.extraLarge,
        title = {
            Text(
                text = title,
                style = MusicTheme.typography.headlineSmall,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Text(
                text = text,
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                // 取消按钮：左半侧，胶囊形状，带背景色
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = dimensions.minimumTouchTarget),
                    shape = MusicTheme.shapes.pill,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MusicTheme.colors.surfaceVariant,
                            contentColor = MusicTheme.colors.onSurfaceVariant,
                        ),
                ) {
                    Text(
                        text = cancelLabel,
                        style = MusicTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 确认按钮：右半侧，胶囊形状，主色或警示红色
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).heightIn(min = dimensions.minimumTouchTarget),
                    shape = MusicTheme.shapes.pill,
                    colors =
                        if (isDestructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MusicTheme.colors.error,
                                contentColor = MusicTheme.colors.onError,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MusicTheme.colors.primary,
                                contentColor = MusicTheme.colors.onPrimary,
                            )
                        },
                ) {
                    Text(
                        text = confirmLabel,
                        style = MusicTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        dismissButton = null,
    )
}

@Preview(name = "Standard Confirmation", showBackground = true)
@Composable
private fun ConfirmationDialogPreview() {
    MusicAppTheme {
        ConfirmationDialog(
            title = "确认操作",
            text = "是否执行该常规操作？此操作可以正常继续。",
            confirmLabel = "确认",
            cancelLabel = "取消",
            onConfirm = {},
            onDismiss = {},
            isDestructive = false,
        )
    }
}

@Preview(name = "Destructive Confirmation", showBackground = true)
@Composable
private fun DestructiveConfirmationDialogPreview() {
    MusicAppTheme {
        ConfirmationDialog(
            title = "清空播放历史",
            text = "确定要清空全部播放历史吗？此操作无法撤销。",
            confirmLabel = "清空",
            cancelLabel = "取消",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
        )
    }
}
