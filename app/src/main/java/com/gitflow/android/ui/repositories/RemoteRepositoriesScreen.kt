package com.gitflow.android.ui.repositories

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import com.gitflow.android.R
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.auth.providerDisplayName
import com.gitflow.android.ui.auth.providerIcon
import androidx.compose.foundation.clickable
import com.gitflow.android.ui.auth.providerIconColor
import com.gitflow.android.ui.components.CloneProgressOverlay
import com.gitflow.android.ui.util.formatBytes
import com.gitflow.android.ui.util.isoToShortDate
import java.io.File
import java.util.*

private const val SIZE_WARNING_THRESHOLD_BYTES = 50L * 1024L * 1024L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteRepositoriesScreen(
    onNavigateBack: () -> Unit,
    onRepositoryCloned: () -> Unit,
    viewModel: RemoteRepositoriesViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val repositories by viewModel.repositories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isCloning by viewModel.isCloning.collectAsState()
    val authenticatedProviders by viewModel.authenticatedProviders.collectAsState()

    // Refresh auth state when the screen (re-)enters composition
    LaunchedEffect(Unit) {
        viewModel.refreshAuthState()
    }

    data class PendingClone(val repository: GitRemoteRepository, val localPath: String, val defaultBranchOnly: Boolean)
    var pendingClone by remember { mutableStateOf<PendingClone?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingClone
        if (granted && request != null) {
            viewModel.startCloneInBackground(
                context = context,
                repository = request.repository,
                localPath = request.localPath,
                singleBranch = if (request.defaultBranchOnly) request.repository.defaultBranch else null,
                onStarted = onRepositoryCloned
            )
        } else if (!granted) {
            viewModel.showError(context.getString(R.string.notification_permission_required))
        }
        pendingClone = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_repos_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.remote_repos_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshRepositories() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.remote_repos_refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProviderTabs(
                    authenticatedProviders = authenticatedProviders,
                    selectedProvider = selectedProvider,
                    onProviderSelected = { viewModel.selectProvider(it) }
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.remote_repos_loading))
                        }
                    }
                } else if (errorMessage != null) {
                    ErrorMessage(
                        message = errorMessage ?: "",
                        onRetry = { viewModel.refreshRepositories() }
                    )
                } else if (repositories.isEmpty()) {
                    EmptyRepositoriesMessage(selectedProvider)
                } else {
                    RepositoriesList(
                        repositories = repositories,
                        isCloning = isCloning,
                        onCloneRepository = { repository, localPath, defaultBranchOnly ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingClone = PendingClone(repository, localPath, defaultBranchOnly)
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.startCloneInBackground(
                                    context = context,
                                    repository = repository,
                                    localPath = localPath,
                                    singleBranch = if (defaultBranchOnly) repository.defaultBranch else null,
                                    onStarted = onRepositoryCloned
                                )
                            }
                        }
                    )
                }
            }

            CloneProgressOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

@Composable
fun ProviderTabs(
    authenticatedProviders: List<GitProvider>,
    selectedProvider: GitProvider?,
    onProviderSelected: (GitProvider) -> Unit
) {
    if (authenticatedProviders.isEmpty()) return

    val selectedIndex = authenticatedProviders.indexOf(selectedProvider).coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        authenticatedProviders.forEach { provider ->
            Tab(
                selected = selectedProvider == provider,
                onClick = { onProviderSelected(provider) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        providerIcon(provider),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = providerIconColor(provider)
                    )
                    Text(
                        text = providerDisplayName(provider),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.remote_repos_retry))
                }
            }
        }
    }
}


@Composable
fun EmptyRepositoriesMessage(selectedProvider: GitProvider?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (selectedProvider) {
                    null -> stringResource(R.string.remote_repos_select_provider)
                    else -> stringResource(R.string.remote_repos_not_found)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RepositoriesList(
    repositories: List<GitRemoteRepository>,
    isCloning: Boolean,
    onCloneRepository: (GitRemoteRepository, String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(repositories) { repository ->
            RepositoryCard(
                repository = repository,
                isCloning = isCloning,
                onClone = { localPath, defaultBranchOnly ->
                    onCloneRepository(repository, localPath, defaultBranchOnly)
                }
            )
        }
    }
}

@Composable
fun RepositoryCard(
    repository: GitRemoteRepository,
    isCloning: Boolean,
    onClone: (localPath: String, defaultBranchOnly: Boolean) -> Unit
) {
    var showCloneDialog by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf(false) }
    var pendingClonePath by remember { mutableStateOf<String?>(null) }
    var pendingCloneDefaultOnly by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = repository.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repository.private) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.remote_repos_private),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        providerIcon(repository.provider),
                        contentDescription = repository.provider.name,
                        modifier = Modifier.size(16.dp),
                        tint = providerIconColor(repository.provider)
                    )
                }
            }
            
            repository.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.remote_repos_updated, isoToShortDate(repository.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = { showCloneDialog = true },
                    enabled = !isCloning
                ) {
                    if (isCloning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.remote_repos_clone))
                    }
                }
            }
        }
    }
    
    if (showCloneDialog) {
        CloneDialog(
            repositoryName = repository.name,
            approximateSizeBytes = repository.approximateSizeBytes,
            defaultBranch = repository.defaultBranch,
            onDismiss = { showCloneDialog = false },
            onConfirm = { localPath, defaultBranchOnly ->
                showCloneDialog = false
                val requiresWarning = shouldWarnAboutRepositorySize(repository.approximateSizeBytes)
                if (requiresWarning) {
                    pendingClonePath = localPath
                    pendingCloneDefaultOnly = defaultBranchOnly
                    showSizeWarning = true
                } else {
                    onClone(localPath, defaultBranchOnly)
                }
            }
        )
    }

    if (showSizeWarning) {
        val formattedSize = formatRepositorySize(repository.approximateSizeBytes)
        AlertDialog(
            onDismissRequest = {
                showSizeWarning = false
                pendingClonePath = null
            },
            title = { Text(stringResource(R.string.remote_repos_large_title)) },
            text = {
                Text(
                    text = if (formattedSize != null) {
                        stringResource(R.string.remote_repos_large_message_size, formattedSize)
                    } else {
                        stringResource(R.string.remote_repos_large_message)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = pendingClonePath
                        val defaultOnly = pendingCloneDefaultOnly
                        showSizeWarning = false
                        pendingClonePath = null
                        if (path != null) {
                            onClone(path, defaultOnly)
                        }
                    }
                ) {
                    Text(stringResource(R.string.remote_repos_clone_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSizeWarning = false
                        pendingClonePath = null
                    }
                ) {
                    Text(stringResource(R.string.remote_repos_clone_cancel))
                }
            }
        )
    }
}

@Composable
fun CloneDialog(
    repositoryName: String,
    approximateSizeBytes: Long?,
    defaultBranch: String,
    onDismiss: () -> Unit,
    onConfirm: (localPath: String, defaultBranchOnly: Boolean) -> Unit
) {
    val context = LocalContext.current
    val settingsManager: AppSettingsManager = koinInject()
    val defaultLocalPath = remember(repositoryName) {
        val baseDir = settingsManager.getRepositoriesBaseDir(context)
        File(baseDir, repositoryName).absolutePath
    }
    var localPath by remember(defaultLocalPath) { mutableStateOf(defaultLocalPath) }
    var defaultBranchOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_repos_clone_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.remote_repos_clone_path_label))
                formatRepositorySize(approximateSizeBytes)?.let { sizeString ->
                    Text(
                        text = stringResource(R.string.remote_repos_clone_size, sizeString),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = localPath,
                    onValueChange = { localPath = it },
                    label = { Text(stringResource(R.string.remote_repos_local_path)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (defaultBranch.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { defaultBranchOnly = !defaultBranchOnly },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = defaultBranchOnly, onCheckedChange = { defaultBranchOnly = it })
                        Text(
                            text = stringResource(R.string.remote_repos_default_branch_only, defaultBranch),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(localPath, defaultBranchOnly) },
                enabled = localPath.isNotBlank()
            ) {
                Text(stringResource(R.string.remote_repos_clone))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.remote_repos_clone_cancel))
            }
        }
    )
}

private fun shouldWarnAboutRepositorySize(sizeBytes: Long?): Boolean {
    val size = sizeBytes ?: return false
    return size > SIZE_WARNING_THRESHOLD_BYTES
}

private fun formatRepositorySize(sizeBytes: Long?): String? {
    val size = sizeBytes ?: return null
    if (size <= 0) return null
    return formatBytes(size)
}

// formatDate(String) moved to ui/util/Formatters.kt (isoToShortDate — fixes the UTC 'Z' bug)
