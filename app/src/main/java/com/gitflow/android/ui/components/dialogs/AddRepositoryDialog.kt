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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.repository.CloneProgress
import com.gitflow.android.data.repository.CloneProgressCallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAdd: (String, String, Boolean) -> Unit,
    onAddLocal: (String) -> Unit = {},
    onNavigateToRemote: () -> Unit = {},
    cloneProgress: CloneProgress? = null,
    cloneProgressCallback: CloneProgressCallback? = null,
    authManager: AuthManager? = null
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var localPath by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var customDestination by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = "Add Repository",
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

                    if (selectedTab == 0 && cloneProgress != null) {
                        CloneProgressView(
                            cloneProgress = cloneProgress,
                            showLogs = showLogs,
                            onToggleLogs = { showLogs = !showLogs }
                        )
                    } else {
                        DefaultLoadingView(selectedTab)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                DialogActions(
                    selectedTab = selectedTab,
                    isLoading = isLoading,
                    cloneProgressCallback = cloneProgressCallback,
                    name = name,
                    url = url,
                    localPath = localPath,
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
    val tabTitles = listOf("Clone", "Local", "Create", "Remote")
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
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onBrowseDestination: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("Repository URL") },
        placeholder = { Text("https://github.com/user/repo.git") },
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
        label = { Text("Local name") },
        placeholder = { Text("my-project") },
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
        label = { Text("Destination (optional)") },
        placeholder = { Text("Leave empty for default location") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = onBrowseDestination,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = "Browse")
            }
        },
        enabled = !isLoading
    )

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
                        text = when {
                            !githubToken.isNullOrEmpty() && url.contains("github.com") -> "Authenticated with GitHub"
                            !gitlabToken.isNullOrEmpty() && url.contains("gitlab.com") -> "Authenticated with GitLab"
                            !githubToken.isNullOrEmpty() -> "GitHub token available"
                            !gitlabToken.isNullOrEmpty() -> "GitLab token available"
                            else -> "Authentication available"
                        },
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
                        text = "Not authenticated - private repos may not be accessible",
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
        label = { Text("Repository Path") },
        placeholder = { Text("/storage/emulated/0/MyRepos/project") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = onBrowsePath,
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = "Browse")
            }
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Select an existing Git repository from your device storage",
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
        label = { Text("Repository name") },
        placeholder = { Text("my-new-project") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.Create, contentDescription = null)
        },
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "This will create a new Git repository locally",
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
            text = "Browse Remote Repositories",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Connect to GitHub or GitLab to browse and clone your remote repositories",
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
            Text("Browse Repositories")
        }
    }
}

@Composable
private fun CloneProgressView(
    cloneProgress: CloneProgress,
    showLogs: Boolean,
    onToggleLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cloning Repository",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = onToggleLogs) {
                    Text(
                        text = if (showLogs) "Hide Logs" else "Show Logs",
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cloneProgress.stage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = cloneProgress.progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (cloneProgress.total > 0) {
                    Text(
                        text = "${cloneProgress.completed} / ${cloneProgress.total} (${(cloneProgress.progress * 100).toInt()}%)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (cloneProgress.estimatedTimeRemaining.isNotEmpty()) {
                    Text(
                        text = "ETA: ${cloneProgress.estimatedTimeRemaining}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (showLogs && cloneProgress.logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        reverseLayout = true
                    ) {
                        items(cloneProgress.logs.reversed()) { log ->
                            Text(
                                text = log,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
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
            text = when (selectedTab) {
                0 -> "Cloning repository..."
                1 -> "Adding local repository..."
                2 -> "Creating repository..."
                else -> "Processing..."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DialogActions(
    selectedTab: Int,
    isLoading: Boolean,
    cloneProgressCallback: CloneProgressCallback?,
    name: String,
    url: String,
    localPath: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, Boolean) -> Unit,
    onAddLocal: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = {
                if (isLoading && selectedTab == 0 && cloneProgressCallback?.isCancelled() == false) {
                    cloneProgressCallback?.cancel()
                } else {
                    onDismiss()
                }
            },
            enabled = true
        ) {
            Text(
                if (isLoading && selectedTab == 0 && cloneProgressCallback?.isCancelled() == false) {
                    "Cancel Clone"
                } else {
                    "Cancel"
                }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                when (selectedTab) {
                    0 -> { // Clone
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            onAdd(name, url, true)
                        }
                    }
                    1 -> { // Local
                        if (localPath.isNotEmpty()) {
                            onAddLocal(localPath)
                        }
                    }
                    2 -> { // Create
                        if (name.isNotEmpty()) {
                            onAdd(name, "", false)
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
                    when (selectedTab) {
                        0 -> "Clone"
                        1 -> "Add Local"
                        2 -> "Create"
                        else -> "Add"
                    }
                )
            }
        }
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