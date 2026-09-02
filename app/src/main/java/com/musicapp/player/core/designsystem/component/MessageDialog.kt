package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * 通用单按钮提示/确认对话框。
 *
 * 遵循设计系统规范：
 * - 标题可选（[title]），存在时约束为单行（超长省略），采用 [MusicTheme.typography.headlineSmall]；
 * - 正文采用 [MusicTheme.typography.bodyMedium] 与 [MusicTheme.colors.onSurfaceVariant]；
 * - 支持通过 [content] 插槽自定义富文本、滚动列表等复杂内容；
 * - 底部单个主色操作按钮采用 [Modifier.fillMaxWidth] 撑满宽度，配合 [MusicTheme.shapes.pill] 胶囊形状；
 * - 按钮原生自带规范的 Material Ripple（水波纹）触控动效与 48dp 最小触控区域。
 */
@Composable
fun MessageDialog(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmLabel: String = stringResource(R.string.dismiss),
    onConfirm: () -> Unit = onDismiss,
) {
    MessageDialog(
        modifier = modifier,
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Text(
            text = message,
            style = MusicTheme.typography.bodyMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * 通用单按钮提示对话框（自定义内容插槽版本）。
 */
@Composable
fun MessageDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmLabel: String = stringResource(R.string.dismiss),
    onConfirm: () -> Unit = onDismiss,
    content: @Composable () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MusicTheme.shapes.extraLarge,
        title =
            if (title != null) {
                {
                    Text(
                        text = title,
                        style = MusicTheme.typography.headlineSmall,
                        color = MusicTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.minimumTouchTarget),
                shape = MusicTheme.shapes.pill,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MusicTheme.colors.primary,
                        contentColor = MusicTheme.colors.onPrimary,
                    ),
            ) {
                Text(
                    text = confirmLabel,
                    style = MusicTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        dismissButton = null,
    )
}

@Preview(name = "Message Dialog with Title", showBackground = true)
@Composable
private fun MessageDialogWithTitlePreview() {
    MusicAppTheme {
        MessageDialog(
            title = "歌单已创建",
            message = "已成功创建歌单“我的至爱金曲”。",
            confirmLabel = "好的",
            onDismiss = {},
        )
    }
}

@Preview(name = "Message Dialog without Title", showBackground = true)
@Composable
private fun MessageDialogWithoutTitlePreview() {
    MusicAppTheme {
        MessageDialog(
            message = "操作已顺利完成，已为您更新曲目库。",
            confirmLabel = "知道了",
            onDismiss = {},
        )
    }
}

@Preview(name = "Message Dialog Custom Content", showBackground = true)
@Composable
private fun MessageDialogCustomContentPreview() {
    MusicAppTheme {
        MessageDialog(
            title = "扫描结果",
            confirmLabel = "关闭",
            onDismiss = {},
        ) {
            Text(
                text = "已扫描并导入 128 首歌曲。",
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
    }
}
