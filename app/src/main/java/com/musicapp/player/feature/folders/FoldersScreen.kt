package com.musicapp.player.feature.folders

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.musicapp.player.core.designsystem.component.BareIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.EmptyState
import com.musicapp.player.core.designsystem.component.GutterMode
import com.musicapp.player.core.designsystem.component.RightGutterOverlay
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.TrackSummaryRow
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryNavigationIconButton
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.CategoryTrackSortMenu
import com.musicapp.player.feature.category.labelRes
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun FoldersScreenRoute(
    viewModel: FoldersViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoldersScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        openDrawer = openDrawer,
        onScanMusic = onScanMusic,
        onFolderClick = onFolderClick,
        onPlayFolder = viewModel::playFolder,
    )
}

@Composable
fun FolderDetailScreenRoute(
    folderId: FolderId,
    viewModel: FolderDetailViewModel,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { viewModel.open(folderId) }
    FolderDetailScreen(
        state = state,
        contentInsets = contentInsets,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onFolderSortSelected = viewModel::selectFolderSort,
        onTrackSortSelected = viewModel::selectTrackSort,
        onFolderClick = onFolderClick,
        onTrackClick = { viewModel.playTrack(it.id) },
    )
}

@Composable
private fun FoldersScreen(
    state: FoldersUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    onScanMusic: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
    onPlayFolder: (FolderId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val coroutineScope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredFolders = remember(state.musicFolders, searchQuery) {
        state.musicFolders.filter { it.matchesFolderSearch(searchQuery) }
    }
    val sections = remember(filteredFolders) { groupFoldersIntoSections(filteredFolders) }
    val displayFolders = remember(sections) { sections.flatMap(FolderSection::folders) }
    val indexLabels = remember { sectionIndexLabels() }
    val sectionPositions = remember(sections, state.volumes.size) {
        sectionStartPositions(sections, leadingItemCount = state.volumes.size)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedSection by remember(listState, sections, state.volumes.size) {
        derivedStateOf {
            sectionLabelAtPosition(
                sections = sections,
                itemPosition = listState.firstVisibleItemIndex,
                leadingItemCount = state.volumes.size,
            )
        }
    }
    val canScroll by remember(listState) {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    val gutterMode = remember(displayFolders, canScroll, selectedSection, sections, sectionPositions) {
        if (displayFolders.isEmpty() || !canScroll) {
            GutterMode.Hidden
        } else {
            GutterMode.Index(
                sortOrder = SectionSortOrder.ASCENDING,
                activeSection = selectedSection,
                populatedBuckets = sections.map(FolderSection::label).toSet(),
                onSectionSelected = { label ->
                    sectionPositions[label]?.let { position ->
                        coroutineScope.launch {
                            listState.scrollToItem(position.coerceAtLeast(0))
                        }
                    }
                },
            )
        }
    }
    val hasContent = state.volumes.isNotEmpty() || state.musicFolders.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        ) {
            FoldersHeader(
                policy = policy,
                openDrawer = openDrawer,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onOpenSearch = { searchActive = true },
                onCloseSearch = {
                    searchActive = false
                    searchQuery = ""
                },
                onSearchQueryChange = { searchQuery = it },
            )
            when {
                !hasContent ->
                    EmptyState(
                        modifier = Modifier.weight(1f)
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                        title = stringResource(R.string.folders_empty_title),
                        description = stringResource(R.string.folders_empty_description),
                        actionLabel = stringResource(R.string.navigation_scan_music),
                        actionIconRes = R.drawable.ic_sidebar_scan,
                        onAction = onScanMusic,
                    )
                else ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .padding(horizontal = dimensions.contentHorizontalPadding),
                        contentPadding = PaddingValues(
                            top = dimensions.spaceSmall,
                            bottom = dimensions.spaceSmall,
                            end = dimensions.spaceExtraSmall,
                        ),
                        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
                    ) {
                        items(
                            items = state.volumes,
                            key = { "volume:${it.id.sourceId}" },
                        ) { volume ->
                            FolderVolumeCard(
                                volume = volume,
                                onClick = { onFolderClick(volume.folder.id) },
                            )
                        }
                        if (displayFolders.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "folder-search-empty") {
                                EmptyState(
                                    title = stringResource(R.string.folders_no_results_title),
                                    description = stringResource(R.string.folders_no_results_description),
                                )
                            }
                        } else {
                            items(
                                items = displayFolders,
                                key = { "folder:${it.id.sourceId}" },
                            ) { folder ->
                                FolderShortcutCard(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.id) },
                                    onPlayAll = { onPlayFolder(folder.id) },
                                )
                            }
                        }
                    }
            }
        }
        RightGutterOverlay(
            mode = gutterMode,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
        )
    }
}

@Composable
private fun FoldersHeader(
    policy: WindowLayoutPolicy,
    openDrawer: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = dimensions.playerHeaderHeight)
            .padding(horizontal = dimensions.topBarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
    ) {
        if (policy == WindowLayoutPolicy.COMPACT_DRAWER) {
            CategoryNavigationIconButton(CategoryNavigationAction.DRAWER, openDrawer)
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
                                text = stringResource(R.string.folders_search_placeholder),
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
                    contentDescription = stringResource(R.string.folders_search_close),
                    tint = MusicTheme.colors.onSurface,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.folders_page_title),
                style = MusicTheme.typography.titleLarge,
                color = MusicTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            BareIconButton(
                onClick = onOpenSearch,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_search),
                    contentDescription = stringResource(R.string.folders_search_label),
                    tint = MusicTheme.colors.onSurface,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
    }
}

@Composable
private fun FolderVolumeCard(
    volume: FolderVolumeItem,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    val title = if (volume.isPrimary) {
        stringResource(R.string.folder_internal_storage)
    } else {
        volume.displayName?.takeIf(String::isNotBlank)
            ?: volume.folder.displayName.takeIf(String::isNotBlank)
            ?: stringResource(R.string.folder_unknown_volume)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MusicTheme.aeroCardContainerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = dimensions.spaceMedium,
                top = dimensions.spaceMedium,
                end = dimensions.spaceSmall,
                bottom = dimensions.spaceMedium,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_storage),
                contentDescription = null,
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall)) {
                Text(
                    text = title,
                    style = MusicTheme.typography.titleMedium,
                    color = MusicTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                volume.rootPath?.takeIf(String::isNotBlank)?.let { path ->
                    Text(
                        text = path,
                        style = MusicTheme.typography.bodySmall,
                        color = MusicTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StorageCapacityText(volume.usedBytes, volume.totalBytes)
            }
            Box(
                modifier = Modifier.size(dimensions.minimumTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_chevron_right),
                    contentDescription = null,
                    tint = MusicTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
    }
}

@Composable
private fun StorageCapacityText(usedBytes: Long?, totalBytes: Long?) {
    val capacity = storageCapacityParts(usedBytes, totalBytes) ?: return
    Text(
        text = stringResource(
            R.string.folder_storage_capacity,
            capacity.first,
            capacity.second,
            stringResource(capacity.third),
        ),
        style = MusicTheme.typography.bodySmall,
        color = MusicTheme.colors.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun FolderShortcutCard(
    folder: FolderNode,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MusicTheme.aeroCardContainerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = dimensions.spaceMedium, end = dimensions.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_folder),
                contentDescription = null,
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
            Column(
                modifier = Modifier.weight(1f).padding(vertical = dimensions.spaceMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                Text(
                    text = folder.displayName,
                    style = MusicTheme.typography.titleMedium,
                    color = MusicTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.folder_track_count,
                        folder.recursiveTrackCount,
                        folder.recursiveTrackCount,
                    ),
                    style = MusicTheme.typography.bodySmall,
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                BareIconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_more_vertical),
                        contentDescription = stringResource(R.string.folder_more_actions, folder.displayName),
                        tint = MusicTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_play_all)) },
                        onClick = {
                            menuExpanded = false
                            onPlayAll()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderDetailScreen(
    state: FolderDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onFolderSortSelected: (FolderSortField) -> Unit,
    onTrackSortSelected: (CategoryTrackSortField) -> Unit,
    onFolderClick: (FolderId) -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    if (state.isBrowserOnly) {
        FolderBrowserScreen(
            state = state,
            contentInsets = contentInsets,
            onBack = onBack,
            onFolderClick = onFolderClick,
        )
        return
    }
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
    ) {
        CategoryHeader(
            title = folderDetailTitle(state),
            onBack = onBack,
            trailingContent = {
                TextButton(
                    onClick = onPlayAll,
                    enabled = state.recursiveTracks.any { it.availability == Availability.AVAILABLE },
                ) { Text(stringResource(R.string.category_play_all)) }
                FolderSortMenu(state.folderSort, onFolderSortSelected)
                CategoryTrackSortMenu(
                    sort = state.trackSort,
                    fields = listOf(
                        CategoryTrackSortField.TITLE,
                        CategoryTrackSortField.ARTIST,
                        CategoryTrackSortField.DATE_ADDED,
                        CategoryTrackSortField.DURATION,
                    ),
                    onSelected = onTrackSortSelected,
                )
            },
        )
        if (state.childFolders.isEmpty() && state.directTracks.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                title = stringResource(R.string.folder_empty_title),
                description = stringResource(R.string.folder_empty_description),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
            ) {
                if (state.childFolders.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.folder_subfolders_section),
                            style = MusicTheme.typography.titleMedium,
                            color = MusicTheme.colors.onSurface,
                            modifier = Modifier.padding(dimensions.spaceSmall),
                        )
                    }
                    items(state.childFolders, key = { it.id.sourceId }) { folder ->
                        FolderRow(folder, onFolderClick)
                        HorizontalDivider()
                    }
                }
                if (state.directTracks.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.folder_tracks_section),
                            style = MusicTheme.typography.titleMedium,
                            color = MusicTheme.colors.onSurface,
                            modifier = Modifier.padding(dimensions.spaceSmall),
                        )
                    }
                    items(
                        state.directTracks,
                        key = { "${it.id.volumeName}:${it.id.mediaStoreId}" },
                    ) { track ->
                        TrackSummaryRow(
                            title = track.title,
                            artist = track.artistName,
                            duration = formatDuration(track.durationMs),
                            enabled = track.availability == Availability.AVAILABLE,
                            statusLabel =
                                if (track.availability == Availability.TEMPORARILY_UNAVAILABLE) {
                                    stringResource(R.string.track_temporarily_unavailable)
                                } else {
                                    null
                                },
                            onClick = { onTrackClick(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserScreen(
    state: FolderDetailUiState,
    contentInsets: WindowInsets,
    onBack: () -> Unit,
    onFolderClick: (FolderId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
    ) {
        CategoryHeader(
            title = folderDetailTitle(state),
            onBack = onBack,
            titleStyle = MusicTheme.typography.titleLarge,
        )
        if (state.childFolders.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                title = stringResource(R.string.folder_empty_title),
                description = stringResource(R.string.folder_empty_description),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = dimensions.contentHorizontalPadding),
                contentPadding = PaddingValues(vertical = dimensions.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                items(state.childFolders, key = { it.id.sourceId }) { folder ->
                    BrowserFolderRow(folder = folder, onClick = { onFolderClick(folder.id) })
                }
            }
        }
    }
}

@Composable
private fun BrowserFolderRow(
    folder: FolderNode,
    onClick: () -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MusicTheme.aeroCardContainerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .padding(horizontal = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_common_folder),
                contentDescription = null,
                tint = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(dimensions.spaceLarge),
            )
            Text(
                text = folder.displayName,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderRow(folder: FolderNode, onClick: (FolderId) -> Unit) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(dimensions.trackListItemHeight)
            .clickable { onClick(folder.id) }
            .padding(horizontal = dimensions.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.displayName,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
                maxLines = 1,
            )
            Text(
                if (folder.id.relativePath.isEmpty()) {
                    stringResource(R.string.folder_volume_root)
                } else {
                    folder.id.relativePath
                },
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            pluralStringResource(
                R.plurals.category_track_count,
                folder.recursiveTrackCount,
                folder.recursiveTrackCount,
            ),
            style = MusicTheme.typography.labelMedium,
            color = MusicTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderSortMenu(sort: FolderSort, onSelected: (FolderSortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(sort.field.labelRes()) + stringResource(sort.direction.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FolderSortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    onClick = { onSelected(field); expanded = false },
                )
            }
        }
    }
}

@StringRes
private fun FolderSortField.labelRes(): Int =
    when (this) {
        FolderSortField.NAME -> R.string.sort_name
        FolderSortField.TRACK_COUNT -> R.string.sort_track_count
    }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun FolderNode.matchesFolderSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true
    return displayName.contains(normalizedQuery, ignoreCase = true) ||
        folderSearchKey(displayName).contains(folderSearchKey(normalizedQuery))
}

private enum class StorageUnit(
    val divisor: Double,
    val labelRes: Int,
    val fractionDigits: Int,
) {
    BYTES(1.0, R.string.folder_storage_unit_bytes, 0),
    KILOBYTES(1024.0, R.string.folder_storage_unit_kilobytes, 2),
    MEGABYTES(1024.0 * 1024.0, R.string.folder_storage_unit_megabytes, 2),
    GIGABYTES(1024.0 * 1024.0 * 1024.0, R.string.folder_storage_unit_gigabytes, 2),
    TERABYTES(1024.0 * 1024.0 * 1024.0 * 1024.0, R.string.folder_storage_unit_terabytes, 2),
}

private fun storageCapacityParts(usedBytes: Long?, totalBytes: Long?): Triple<String, String, Int>? {
    if (usedBytes == null || totalBytes == null || usedBytes < 0 || totalBytes <= 0) return null
    val unit = when {
        totalBytes >= StorageUnit.TERABYTES.divisor -> StorageUnit.TERABYTES
        totalBytes >= StorageUnit.GIGABYTES.divisor -> StorageUnit.GIGABYTES
        totalBytes >= StorageUnit.MEGABYTES.divisor -> StorageUnit.MEGABYTES
        totalBytes >= StorageUnit.KILOBYTES.divisor -> StorageUnit.KILOBYTES
        else -> StorageUnit.BYTES
    }
    return Triple(
        formatStorageValue(usedBytes, unit),
        formatStorageValue(totalBytes, unit),
        unit.labelRes,
    )
}

private fun formatStorageValue(bytes: Long, unit: StorageUnit): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = unit.fractionDigits
        minimumFractionDigits = unit.fractionDigits
        roundingMode = RoundingMode.DOWN
    }.format(bytes.toDouble() / unit.divisor)

@Composable
private fun folderDetailTitle(state: FolderDetailUiState): String =
    if (state.isVolumeRoot && state.volumeIsPrimary) {
        stringResource(R.string.folder_internal_storage)
    } else {
        state.displayName?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.folder_unknown_name)
    }
