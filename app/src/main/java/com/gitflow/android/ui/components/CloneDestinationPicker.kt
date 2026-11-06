package com.gitflow.android.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitflow.android.R
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.data.storage.hasSharedStorageAccess
import com.gitflow.android.data.storage.treeUriToFile
import org.koin.compose.koinInject
import java.io.File

/**
 * Returns a launcher of the system folder picker that hands back a path JGit can use.
 * The folder grant of the picker itself is useless to JGit, so file access is obtained before the picker opens.
 */
@Composable
fun rememberSharedFolderPicker(onPicked: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showAccessDialog by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val folder = treeUriToFile(uri)
        if (folder == null) onError(context.getString(R.string.add_repo_folder_unresolved)) else onPicked(folder.absolutePath)
    }
    val accessSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasSharedStorageAccess(context)) folderLauncher.launch(null)
    }
    val accessPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasSharedStorageAccess(context)) folderLauncher.launch(null)
    }

    if (showAccessDialog) {
        AlertDialog(
            onDismissRequest = { showAccessDialog = false },
            title = { Text(stringResource(R.string.add_repo_storage_access_title)) },
            text = { Text(stringResource(R.string.add_repo_storage_access_required)) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessDialog = false
                    requestSharedStorageAccess(context, accessSettingsLauncher, accessPermissionLauncher)
                }) { Text(stringResource(R.string.settings_permission_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessDialog = false }) { Text(stringResource(R.string.add_repo_cancel)) }
            }
        )
    }

    return {
        if (hasSharedStorageAccess(context)) folderLauncher.launch(null) else showAccessDialog = true
    }
}

private fun requestSharedStorageAccess(
    context: Context,
    settingsLauncher: ActivityResultLauncher<Intent>,
    permissionLauncher: ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        return
    }
    val appScreen = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
    try {
        settingsLauncher.launch(appScreen)
    } catch (error: ActivityNotFoundException) {
        // Some firmwares lack the per-app screen and only offer the list of all apps.
        settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

/** Cloning into an existing folder fails, so such a target is rejected before the clone starts. */
fun isCloneTargetUsable(parentPath: String, folderName: String): Boolean =
    folderName.isNotBlank() && !File(parentPath, folderName.trim()).exists()

@Composable
fun rememberDefaultCloneParent(): String {
    val context = LocalContext.current
    val settingsManager: AppSettingsManager = koinInject()
    return remember { settingsManager.getRepositoriesBaseDir(context).absolutePath }
}

@Composable
fun CloneDestinationPicker(
    parentPath: String,
    folderName: String,
    onParentChange: (String) -> Unit,
    onFolderNameChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val settingsManager: AppSettingsManager = koinInject()
    val defaultParent = rememberDefaultCloneParent()
    val recentParents = remember {
        settingsManager.getRecentCloneParents().filter { it != defaultParent && File(it).isDirectory }
    }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pickFolder = rememberSharedFolderPicker(
        onPicked = { path ->
            pickerError = null
            onParentChange(path)
        },
        onError = { message -> pickerError = message }
    )
    val targetPath = File(parentPath, folderName.trim()).absolutePath
    val targetExists = remember(parentPath, folderName) {
        folderName.isNotBlank() && !isCloneTargetUsable(parentPath, folderName)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.clone_destination_title), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = parentPath == defaultParent,
                onClick = { onParentChange(defaultParent) },
                label = { Text(stringResource(R.string.clone_destination_default)) },
                enabled = enabled
            )
            recentParents.forEach { recent ->
                FilterChip(
                    selected = parentPath == recent,
                    onClick = { onParentChange(recent) },
                    label = { Text(File(recent).name.ifEmpty { recent }) },
                    enabled = enabled
                )
            }
            if (parentPath != defaultParent && parentPath !in recentParents) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(File(parentPath).name.ifEmpty { parentPath }) },
                    enabled = enabled
                )
            }
            AssistChip(
                onClick = pickFolder,
                label = { Text(stringResource(R.string.clone_destination_choose)) },
                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                enabled = enabled
            )
        }
        OutlinedTextField(
            value = folderName,
            onValueChange = onFolderNameChange,
            label = { Text(stringResource(R.string.clone_destination_folder_name)) },
            singleLine = true,
            isError = targetExists,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = targetPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis
        )
        if (targetExists) {
            Text(
                text = stringResource(R.string.clone_destination_exists),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        pickerError?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
