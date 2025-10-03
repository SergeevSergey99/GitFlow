package com.gitflow.android.ui.components.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAdd: (String, String, Boolean, Long?) -> Unit,
    onAddLocal: (String) -> Unit = {},
    onNavigateToRemote: () -> Unit = {},
    authManager: AuthManager? = null
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var localPath by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var customDestination by remember { mutableStateOf("") }
    var approximateSize by remember { mutableStateOf<Long?>(null) }
    var isSizeLoading by remember { mutableStateOf(false) }
    var sizeError by remember { mutableStateOf<String?>(null) }
    // Launcher for choosing directory for cloning
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            customDestination = getRealPathFromUri(it)
        }
    }

    // Launcher for choosing existing repository directory
    val repoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            localPath = getRealPathFromUri(it)
        }
    }

    LaunchedEffect(url, authManager, selectedTab) {
        approximateSize = null
        sizeError = null

        if (selectedTab != 0) {
            isSizeLoading = false
            return@LaunchedEffect
        }

        if (url.isBlank() || authManager == null) {
            isSizeLoading = false
            return@LaunchedEffect
        }

        val currentUrl = url
        isSizeLoading = true
        try {
            delay(500)
            if (currentUrl != url) {
                return@LaunchedEffect
            }
            approximateSize = authManager.getRepositoryApproximateSize(currentUrl)
        } catch (e: Exception) {
            sizeError = e.message ?: "Failed to estimate repository size"
        } finally {
            isSizeLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_repo_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                RepositoryTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> CloneTab(
                        url = url,
                        name = name,
                        customDestination = customDestination,
                        isLoading = isLoading,
                        authManager = authManager,
                        isSizeLoading = isSizeLoading,
                        approximateSizeBytes = approximateSize,
                        sizeError = sizeError,
                        onUrlChange = {
                            url = it
                            if (it.isNotEmpty()) {
                                val repoName = extractRepoNameFromUrl(it)
                                if (repoName.isNotEmpty()) {
                                    name = repoName
                                }
                            }
                        },
                        onNameChange = { name = it },
                        onDestinationChange = { customDestination = it },
                        onBrowseDestination = { directoryPickerLauncher.launch(null) }
                    )
                    1 -> LocalTab(
                        localPath = localPath,
                        isLoading = isLoading,
                        onPathChange = { localPath = it },
                        onBrowsePath = { repoPickerLauncher.launch(null) }
                    )
                    2 -> CreateTab(
                        name = name,
                        isLoading = isLoading,
                        onNameChange = { name = it }
                    )
                    3 -> RemoteTab(
                        onNavigateToRemote = {
                            onDismiss()
                            onNavigateToRemote()
                        }
                    )
                }

                // Error message display
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                // Loading indicator and progress
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DefaultLoadingView(selectedTab)
                }

                Spacer(modifier = Modifier.height(16.dp))

                DialogActions(
                    selectedTab = selectedTab,
                    isLoading = isLoading,
                    name = name,
                    url = url,
                    localPath = localPath,
                    approximateSize = approximateSize,
                    onDismiss = onDismiss,
                    onAdd = onAdd,
                    onAddLocal = onAddLocal
                )
            }
        }
    }
}

@Composable
private fun RepositoryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabTitles = listOf(
        stringResource(R.string.add_repo_tab_clone),
        stringResource(R.string.add_repo_tab_local),
        stringResource(R.string.add_repo_tab_create),
        stringResource(R.string.add_repo_tab_remote)
    )
    var containerWidth by remember { mutableStateOf(0) }
    val tabCount = tabTitles.size
    val maxChars = tabTitles.maxOf { it.length }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width
            }
    ) {
        val tabWidthPx = if (tabCount > 0) containerWidth / tabCount else 0
        val fontSizePx = if (maxChars > 0) tabWidthPx / (maxChars) else 14f
        val fontSizeSp = with(density) { fontSizePx.toFloat().toSp() }

        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            title,
                            fontSize = fontSizeSp,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CloneTab(
    url: String,
    name: String,
    customDestination: String,
    isLoading: Boolean,
    authManager: AuthManager?,
    isSizeLoading: Boolean,
    approximateSizeBytes: Long?,
    sizeError: String?,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onBrowseDestination: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.add_repo_url_label)) },
        placeholder = { Text(stringResource(R.string.add_repo_url_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            IconButton(
                onClick = {
                    val clipboardText = clipboardManager.getText()?.text ?: ""
                    if (clipboardText.isNotEmpty()) {
                        onUrlChange(clipboardText)
                    }
                },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
            }
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.add_repo_name_label)) },
        placeholder = { Text(stringResource(R.string.add_repo_name_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.Folder, contentDescription = null)
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = customDestination,
        onValueChange = onDestinationChange,
        label = { Text(stringResource(R.string.add_repo_destination_label)) },
        placeholder = { Text(stringResource(R.string.add_repo_destination_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = onBrowseDestination,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.add_repo_browse))
            }
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isSizeLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.add_repo_estimating_size),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        approximateSizeBytes?.let { size ->
            Text(
                text = stringResource(R.string.add_repo_approximate_download, formatApproximateSize(size)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        sizeError?.let { message ->
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    // Показываем статус авторизации
    authManager?.let { auth ->
        Spacer(modifier = Modifier.height(8.dp))

        val githubToken = auth.getAccessToken(GitProvider.GITHUB)
        val gitlabToken = auth.getAccessToken(GitProvider.GITLAB)

        if (!githubToken.isNullOrEmpty() || !gitlabToken.isNullOrEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(when {
                            !githubToken.isNullOrEmpty() && url.contains("github.com") -> R.string.add_repo_authenticated_github
                            !gitlabToken.isNullOrEmpty() && url.contains("gitlab.com") -> R.string.add_repo_authenticated_gitlab
                            !githubToken.isNullOrEmpty() -> R.string.add_repo_github_token_available
                            !gitlabToken.isNullOrEmpty() -> R.string.add_repo_gitlab_token_available
                            else -> R.string.add_repo_auth_available
                        }),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_repo_not_authenticated),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalTab(
    localPath: String,
    isLoading: Boolean,
    onPathChange: (String) -> Unit,
    onBrowsePath: () -> Unit
) {
    OutlinedTextField(
        value = localPath,
        onValueChange = onPathChange,
        label = { Text(stringResource(R.string.add_repo_path_label)) },
        placeholder = { Text(stringResource(R.string.add_repo_path_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = onBrowsePath,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.add_repo_browse))
            }
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.add_repo_local_description),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CreateTab(
    name: String,
    isLoading: Boolean,
    onNameChange: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.add_repo_repo_name_label)) },
        placeholder = { Text(stringResource(R.string.add_repo_create_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.Create, contentDescription = null)
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.add_repo_create_description),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RemoteTab(
    onNavigateToRemote: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.add_repo_remote_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.add_repo_remote_description),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onNavigateToRemote,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_repo_remote_browse))
        }
    }
}

@Composable
private fun DefaultLoadingView(selectedTab: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(when (selectedTab) {
                0 -> R.string.add_repo_cloning
                1 -> R.string.add_repo_adding_local
                2 -> R.string.add_repo_creating
                else -> R.string.add_repo_processing
            }),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DialogActions(
    selectedTab: Int,
    isLoading: Boolean,
    name: String,
    url: String,
    localPath: String,
    approximateSize: Long?,
    onDismiss: () -> Unit,
    onAdd: (String, String, Boolean, Long?) -> Unit,
    onAddLocal: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onDismiss
        ) {
            Text(stringResource(R.string.add_repo_cancel))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                when (selectedTab) {
                    0 -> { // Clone
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            onAdd(name, url, true, approximateSize)
                        }
                    }
                    1 -> { // Local
                        if (localPath.isNotEmpty()) {
                            onAddLocal(localPath)
                        }
                    }
                    2 -> { // Create
                        if (name.isNotEmpty()) {
                            onAdd(name, "", false, null)
                        }
                    }
                }
            },
            enabled = when (selectedTab) {
                0 -> name.isNotEmpty() && url.isNotEmpty() && !isLoading
                1 -> localPath.isNotEmpty() && !isLoading
                2 -> name.isNotEmpty() && !isLoading
                else -> false
            }
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    stringResource(when (selectedTab) {
                        0 -> R.string.add_repo_clone_button
                        1 -> R.string.add_repo_add_local_button
                        2 -> R.string.add_repo_create_button
                        else -> R.string.add_repo_add_button
                    })
                )
            }
        }
    }
}

private fun formatApproximateSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "Unknown"

    val kilobytes = sizeBytes / 1024.0
    val megabytes = kilobytes / 1024.0
    val gigabytes = megabytes / 1024.0

    return when {
        gigabytes >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gigabytes)
        megabytes >= 1 -> String.format(Locale.getDefault(), "%.1f MB", megabytes)
        else -> String.format(Locale.getDefault(), "%.0f KB", kilobytes)
    }
}

fun extractRepoNameFromUrl(url: String): String {
    return try {
        val cleanUrl = url.removeSuffix(".git")
        val segments = cleanUrl.split("/")
        if (segments.size >= 2) {
            segments.last()
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

fun getRealPathFromUri(uri: Uri): String {
    // Для простоты возвращаем строковое представление URI
    // В реальном приложении здесь должна быть более сложная логика
    // для преобразования content:// URI в реальные пути
    return uri.toString().replace("content://com.android.externalstorage.documents/tree/", "/storage/emulated/0/")
        .replace("%3A", "/")
        .replace("%2F", "/")
}
