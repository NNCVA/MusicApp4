package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import kotlinx.coroutines.delay

/**
 * 通用单行文本输入对话框。
 *
 * 遵循设计系统规范：
 * - 标题约束为单行（超长省略），支持可选的辅助说明文本 [message]；
 * - 文本输入区采用 [MusicTheme.shapes.pill] 胶囊形状与 [MusicTheme.colors.surfaceContainerHighest] 深色表面容器；
 * - 支持占位符提示 [placeholder]、自动获取焦点并弹出软键盘、软键盘完成键（Done）快速提交以及一键清空按钮；
 * - 底部取消与确认两个按钮各占左右一半宽度（平分），采用 [MusicTheme.shapes.pill] 胶囊形状；
 * - 默认禁止空白文本提交（[allowBlank] 为 false 时非空校验，支持扩展 [validator]）。
 */
@Composable
fun TextInputDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    placeholder: String = "",
    message: String? = null,
    cancelLabel: String = stringResource(R.string.dismiss),
    allowBlank: Boolean = false,
    autoFocus: Boolean = true,
    validator: ((String) -> Boolean)? = null,
) {
    val dimensions = MusicTheme.dimensions
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            ),
        )
    }
    val currentText = textFieldValue.text
    val isConfirmEnabled =
        (allowBlank || currentText.isNotBlank()) && (validator?.invoke(currentText) ?: true)

    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MusicTheme.typography.bodyMedium,
                        color = MusicTheme.colors.onSurfaceVariant,
                    )
                }

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    shape = MusicTheme.shapes.pill,
                    color = MusicTheme.colors.surfaceContainerHighest,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(start = dimensions.spaceMedium, end = dimensions.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle =
                                MusicTheme.typography.bodyLarge.copy(
                                    color = MusicTheme.colors.onSurface,
                                ),
                            cursorBrush = SolidColor(MusicTheme.colors.primary),
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (isConfirmEnabled) {
                                            onConfirm(currentText.trim())
                                        }
                                    },
                                ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (currentText.isEmpty() && placeholder.isNotBlank()) {
                                        Text(
                                            text = placeholder,
                                            style = MusicTheme.typography.bodyLarge,
                                            color = MusicTheme.colors.onSurfaceVariant.copy(alpha = MusicAlpha.Hint),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        if (currentText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
                                },
                                modifier = Modifier.size(dimensions.minimumTouchTarget),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_common_close),
                                    contentDescription = stringResource(R.string.clear_input),
                                    tint = MusicTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.size(dimensions.spaceMedium),
                                )
                            }
                        }
                    }
                }
            }
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

                // 确认按钮：右半侧，胶囊形状，主色
                Button(
                    onClick = { onConfirm(currentText.trim()) },
                    enabled = isConfirmEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = dimensions.minimumTouchTarget),
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
            }
        },
        dismissButton = null,
    )
}

@Preview(name = "Standard TextInputDialog", showBackground = true)
@Composable
private fun TextInputDialogPreview() {
    MusicAppTheme {
        TextInputDialog(
            title = "新建歌单",
            confirmLabel = "创建",
            cancelLabel = "取消",
            placeholder = "请输入歌单名称",
            onConfirm = {},
            onDismiss = {},
            autoFocus = false,
        )
    }
}

@Preview(name = "Prefilled TextInputDialog", showBackground = true)
@Composable
private fun PrefilledTextInputDialogPreview() {
    MusicAppTheme {
        TextInputDialog(
            title = "重命名歌单",
            initialText = "我的至爱金曲",
            confirmLabel = "保存",
            cancelLabel = "取消",
            placeholder = "请输入歌单名称",
            onConfirm = {},
            onDismiss = {},
            autoFocus = false,
        )
    }
}
