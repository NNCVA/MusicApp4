package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.musicapp.player.R
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.theme.MusicTheme

/**
 * 通用可搜索顶栏组件（SearchableTopBar）。
 *
 * 承载页面导航（抽屉/返回）、标题显示、内联搜索输入交互与尾部操作插槽。
 */
@Composable
fun SearchableTopBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    title: String = "",
    titleStyle: TextStyle = MusicTheme.typography.titleLarge,
    navigationAction: CategoryNavigationAction? = null,
    onNavigationClick: () -> Unit = {},
    searchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onCloseSearch: () -> Unit = {},
    searchPlaceholder: String = stringResource(R.string.tracks_search_placeholder),
    titleContent: (@Composable () -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .height(dimensions.playerHeaderHeight)
            .padding(
                start = dimensions.topBarHorizontalPadding,
                end = dimensions.contentHorizontalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        if (navigationAction != null) {
            CategoryNavigationIconButton(
                action = navigationAction,
                onClick = onNavigationClick,
            )
        }

        if (searchActive) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MusicTheme.typography.titleLarge.copy(color = MusicTheme.colors.onSurface),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isBlank()) {
                            Text(
                                text = searchPlaceholder,
                                style = MusicTheme.typography.titleMedium,
                                color = MusicTheme.colors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            BareIconButton(
                onClick = onCloseSearch,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_close),
                    contentDescription = stringResource(R.string.tracks_search_close),
                    tint = MusicTheme.colors.onSurface,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        } else {
            if (titleContent != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    titleContent()
                }
            } else {
                Text(
                    text = title,
                    style = titleStyle,
                    color = MusicTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onOpenSearch != null) {
                BareIconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_search),
                        contentDescription = stringResource(R.string.tracks_search_label),
                        tint = MusicTheme.colors.onSurface,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }

            trailingContent()
        }
    }
}
