package com.musicapp.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.MusicWindowSizeClass
import com.musicapp.player.theme.MusicTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
  title: String,
  artist: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  artwork: @Composable () -> Unit = {},
  trailingContent: @Composable () -> Unit = {},
) {
  val dimensions = MusicTheme.dimensions
  val typography = MusicTheme.typography
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .height(dimensions.trackRowHeight)
        .semantics { this.selected = selected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    color =
      if (selected) MaterialTheme.colorScheme.secondaryContainer
      else MaterialTheme.colorScheme.surface,
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = dimensions.componentGrid * 2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (selected) {
        Checkbox(checked = true, onCheckedChange = null)
      } else {
        Box(
          modifier =
            Modifier.size(dimensions.trackRowHeight - dimensions.componentGrid * 4)
              .clip(MusicTheme.shapes.artwork),
        ) {
          artwork()
        }
      }
      Spacer(Modifier.width(dimensions.componentGrid * 3))
      Column(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .padding(vertical = dimensions.componentGrid * 2),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = title,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface,
          style = typography.trackTitle,
        )
        Text(
          text = artist,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = typography.trackArtist,
        )
      }
      trailingContent()
    }
  }
}

@Composable
fun MusicMediaGrid(
  modifier: Modifier = Modifier,
  content: LazyGridScope.() -> Unit,
) {
  val dimensions = MusicTheme.dimensions
  LazyVerticalGrid(
    columns = GridCells.Adaptive(dimensions.minimumGridCardWidth),
    modifier = modifier,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(dimensions.horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(dimensions.cardSpacing),
    verticalArrangement = Arrangement.spacedBy(dimensions.cardSpacing),
    content = content,
  )
}

@Composable
fun MusicGridCard(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  artwork: @Composable () -> Unit = {},
) {
  val dimensions = MusicTheme.dimensions
  val typography = MusicTheme.typography
  Surface(
    onClick = onClick,
    modifier = modifier.widthIn(min = dimensions.minimumGridCardWidth),
    shape = MusicTheme.shapes.card,
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MusicTheme.shapes.artwork)) { artwork() }
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .height(dimensions.gridCardInfoHeight)
            .padding(horizontal = dimensions.componentGrid * 2, vertical = dimensions.componentGrid),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = title,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurface,
          style = typography.cardTitle,
        )
        Text(
          text = subtitle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = typography.cardArtist,
        )
      }
    }
  }
}

@Composable
fun MultiSelectionBar(
  selectedCount: Int,
  onClose: () -> Unit,
  onSelectAll: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable () -> Unit = {},
) {
  val dimensions = MusicTheme.dimensions
  Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(dimensions.trackRowHeight)
          .padding(horizontal = dimensions.componentGrid * 2),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onClose, modifier = Modifier.height(dimensions.minimumTouchTarget)) {
        Text(stringResource(R.string.action_close_selection))
      }
      Text(
        text = pluralStringResource(R.plurals.selection_count, selectedCount, selectedCount),
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.onSurface,
        style = MusicTheme.typography.selectionTitle,
      )
      TextButton(onClick = onSelectAll, modifier = Modifier.height(dimensions.minimumTouchTarget)) {
        Text(stringResource(R.string.action_select_all))
      }
      actions()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailContainer(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  windowSizeClass: MusicWindowSizeClass = MusicTheme.windowSizeClass,
  content: @Composable () -> Unit,
) {
  if (!visible) return
  if (windowSizeClass == MusicWindowSizeClass.Compact) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) { content() }
  } else {
    Dialog(
      onDismissRequest = onDismissRequest,
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = MusicTheme.dimensions.detailDialogMaxWidth),
        shape = MusicTheme.shapes.container,
      ) {
        content()
      }
    }
  }
}
