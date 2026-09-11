package com.musicapp.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.musicapp.player.R

/**
 * 统一渲染静态实体图片（曲目、专辑、艺术家和歌单）。
 *
 * 请求进行中不提供 placeholder，图片层保持透明；请求成功显示真实图片，
 * 请求终态失败或 model 为空时才显示统一降级图。调用方负责尺寸、裁剪和语义。
 */
@Composable
fun EntityArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackPainter: Painter = painterResource(R.drawable.ic_playlist_album),
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        error = fallbackPainter,
        fallback = fallbackPainter,
    )
}
