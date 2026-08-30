package com.musicapp.player.feature.scan

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.musicapp.player.core.designsystem.component.BareIconButton
import com.musicapp.player.core.designsystem.component.bounceOverscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.feature.category.CategoryNavigationAction
import com.musicapp.player.feature.category.CategoryHeader
import com.musicapp.player.feature.permission.MediaPermissionState
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.ui.shell.WindowLayoutPolicy

@Composable
fun ScanMusicScreenRoute(
    viewModel: ScanViewModel,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    permissionState: MediaPermissionState,
    onConfirmPermission: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
    onScanMusic: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDialogVisible by rememberSaveable { mutableStateOf(false) }
    var scanAfterPermission by rememberSaveable { mutableStateOf(false) }
    var folderKindName by rememberSaveable { mutableStateOf(PathRuleKind.INCLUDE.name) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.persistTreePermission(uri)
        uri.toScanFolder(context)?.let { folder ->
            viewModel.addFolder(folder.volumeName, folder.directory, PathRuleKind.valueOf(folderKindName))
        }
    }

    LaunchedEffect(permissionState, scanAfterPermission) {
        if (scanAfterPermission && permissionState is MediaPermissionState.Granted) {
            scanAfterPermission = false
            permissionDialogVisible = false
            onScanMusic()
        }
    }

    fun beginScan() {
        if (!state.canScan || state.isScanning) return
        if (permissionState is MediaPermissionState.Granted) {
            onScanMusic()
        } else {
            permissionDialogVisible = true
        }
    }

    ScanMusicScreen(
        state = state,
        contentInsets = contentInsets,
        policy = policy,
        onBack = onBack,
        onStartScan = ::beginScan,
        onUseMediaLibraryChange = viewModel::setUseAndroidMediaLibrary,
        onSkipShortAudioChange = viewModel::setSkipShortAudio,
        onAddFolder = {
            folderKindName = PathRuleKind.INCLUDE.name
            folderLauncher.launch(null)
        },
        onAddBlockedFolder = {
            folderKindName = PathRuleKind.EXCLUDE.name
            folderLauncher.launch(null)
        },
        onRemoveFolder = viewModel::removeFolder,
        onOpenApplicationSettings = onOpenApplicationSettings,
        bottomPadding = bottomPadding,
    )

    if (permissionDialogVisible) {
        PermissionExplanationDialog(
            state = permissionState,
            onDismiss = { permissionDialogVisible = false },
            onConfirm = {
                scanAfterPermission = true
                permissionDialogVisible = false
                when (permissionState) {
                    is MediaPermissionState.PurposeExplanation -> onConfirmPermission()
                    is MediaPermissionState.DeniedCanRetry -> onRetryPermission()
                    is MediaPermissionState.PermanentlyDenied -> onOpenPermissionSettings()
                    else -> Unit
                }
            },
        )
    }
}

@Composable
private fun ScanMusicScreen(
    state: ScanUiState,
    contentInsets: WindowInsets,
    policy: WindowLayoutPolicy,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onUseMediaLibraryChange: (Boolean) -> Unit,
    onSkipShortAudioChange: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onAddBlockedFolder: () -> Unit,
    onRemoveFolder: (com.musicapp.player.core.domain.model.PathRuleId) -> Unit,
    onOpenApplicationSettings: () -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val dimensions = MusicTheme.dimensions
    var blockedFoldersExpanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = dimensions.settingsContentMaxWidth),
        ) {
            CategoryHeader(
                title = stringResource(R.string.scan_media_source),
                policy = policy,
                navigationAction = CategoryNavigationAction.BACK,
                onNavigationClick = onBack,
            )
            if (state.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = dimensions.contentHorizontalPadding),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).bounceOverscroll(),
                contentPadding = PaddingValues(
                    start = dimensions.contentHorizontalPadding,
                    end = dimensions.contentHorizontalPadding,
                    top = dimensions.spaceSmall,
                    bottom = dimensions.spaceSmall + bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
            ) {
                item {
                    ScanActionCard(
                        enabled = state.canScan && !state.isScanning,
                        onClick = onStartScan,
                    )
                }
                item {
                    SwitchCard(
                        title = stringResource(R.string.scan_use_android_media_library),
                        checked = state.settings.scanMode == com.musicapp.player.core.domain.model.ScanMode.ALL,
                        onCheckedChange = onUseMediaLibraryChange,
                    )
                }
                item { SectionLabel(stringResource(R.string.scan_custom_folders)) }
                items(state.includeFolders, key = { it.id.value }) { rule ->
                    FolderRuleCard(rule = rule, onRemove = onRemoveFolder)
                }
                item {
                    ActionCard(
                        iconResId = R.drawable.ic_common_folder_add,
                        title = stringResource(R.string.scan_add_custom_folder),
                        onClick = onAddFolder,
                    )
                }
                item { SectionLabel(stringResource(R.string.scan_settings)) }
                item {
                    ActionCard(
                        iconResId = R.drawable.ic_common_open_in_new,
                        title = stringResource(R.string.scan_manage_storage_permission),
                        onClick = onOpenApplicationSettings,
                        trailingIconResId = R.drawable.ic_common_chevron_right,
                    )
                }
                item {
                    SwitchCard(
                        title = stringResource(R.string.scan_skip_short_audio),
                        checked = state.settings.skipShortAudio,
                        onCheckedChange = onSkipShortAudioChange,
                    )
                }
                item {
                    ActionCard(
                        iconResId = R.drawable.ic_sidebar_folders,
                        title = stringResource(R.string.scan_blocked_folders),
                        onClick = { blockedFoldersExpanded = !blockedFoldersExpanded },
                        trailingIconResId = R.drawable.ic_common_chevron_right,
                    )
                }
                if (blockedFoldersExpanded) {
                    items(state.blockedFolders, key = { it.id.value }) { rule ->
                        FolderRuleCard(rule = rule, onRemove = onRemoveFolder)
                    }
                    item {
                        ActionCard(
                            iconResId = R.drawable.ic_common_folder_add,
                            title = stringResource(R.string.scan_add_blocked_folder),
                            onClick = onAddBlockedFolder,
                        )
                    }
                }
                item { TechnicalSupportCard() }
            }
        }
    }
}

@Composable
private fun ScanActionCard(enabled: Boolean, onClick: () -> Unit) {
    val dimensions = MusicTheme.dimensions
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MusicTheme.aeroCardContainerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.playerControlsHeight)
                .padding(horizontal = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sidebar_scan),
                contentDescription = null,
                tint = MusicTheme.colors.primary,
            )
            Text(
                text = stringResource(R.string.scan_now),
                color = MusicTheme.colors.primary,
                style = MusicTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
internal fun SwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MusicTheme.shapes.large)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {},
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.playerControlsHeight)
                .padding(horizontal = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun ActionCard(
    iconResId: Int,
    title: String,
    onClick: () -> Unit,
    trailingIconResId: Int? = null,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimensions.playerControlsHeight)
                .padding(horizontal = dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = MusicTheme.colors.primary,
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = MusicTheme.colors.primary,
                style = MusicTheme.typography.titleMedium,
            )
            trailingIconResId?.let { icon ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MusicTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderRuleCard(
    rule: PathRule,
    onRemove: (com.musicapp.player.core.domain.model.PathRuleId) -> Unit,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimensions.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sidebar_folders),
                contentDescription = null,
                tint = MusicTheme.colors.onSurface,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.directory.ifBlank { stringResource(R.string.scan_storage_root) },
                    style = MusicTheme.typography.titleMedium,
                    color = MusicTheme.colors.onSurface,
                )
                Text(
                    text = scanFolderPath(rule),
                    style = MusicTheme.typography.bodyMedium,
                    color = MusicTheme.colors.onSurfaceVariant,
                )
            }
            BareIconButton(
                onClick = { onRemove(rule.id) },
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_common_close),
                    contentDescription = stringResource(R.string.scan_remove_folder),
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = MusicTheme.dimensions.spaceMedium),
        color = MusicTheme.colors.onSurfaceVariant,
        style = MusicTheme.typography.titleMedium,
    )
}

@Composable
private fun TechnicalSupportCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.scan_technical_support),
            modifier = Modifier.padding(MusicTheme.dimensions.spaceMedium),
            color = MusicTheme.colors.onSurfaceVariant,
            style = MusicTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PermissionExplanationDialog(
    state: MediaPermissionState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isPermanent = state is MediaPermissionState.PermanentlyDenied
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_permission_dialog_title), color = MusicTheme.colors.onSurface) },
        text = {
            Text(
                stringResource(
                    if (isPermanent) {
                        R.string.permission_permanently_denied
                    } else {
                        R.string.scan_permission_dialog_description
                    },
                ),
                color = MusicTheme.colors.onSurfaceVariant,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.scan_permission_decline))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (isPermanent) R.string.permission_open_settings else R.string.scan_permission_agree,
                    ),
                )
            }
        },
    )
}

private data class ScanFolderSelection(
    val volumeName: String,
    val directory: String,
)

private fun Uri.toScanFolder(context: Context): ScanFolderSelection? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull()
        ?: return null
    val separator = documentId.indexOf(':')
    if (separator <= 0) return null
    val storageId = documentId.substring(0, separator)
    val directory = documentId.substring(separator + 1).replace('\\', '/').trim('/')
    val volumeName = when {
        storageId.equals("primary", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        storageId.equals("primary", ignoreCase = true) -> "external"
        else -> storageId
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        volumeName !in MediaStore.getExternalVolumeNames(context)
    ) {
        return null
    }
    return ScanFolderSelection(volumeName, directory)
}

private fun Context.persistTreePermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun scanFolderPath(rule: PathRule): String =
    buildString {
        append("/storage/")
        append(rule.volumeName)
        if (rule.directory.isNotBlank()) {
            append('/')
            append(rule.directory)
        }
    }
