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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.components.CloneProgressOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val SIZE_WARNING_THRESHOLD_BYTES = 50L * 1024L * 1024L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteRepositoriesScreen(
    onNavigateBack: () -> Unit,
    onRepositoryCloned: () -> Unit,
    viewModel: RemoteRepositoriesViewModel = viewModel()
) {
    android.util.Log.d("RemoteRepositoriesScreen", "RemoteRepositoriesScreen началась")

    val context = LocalContext.current
    android.util.Log.d("RemoteRepositoriesScreen", "Получен context")

    val authManager = remember {
        android.util.Log.d("RemoteRepositoriesScreen", "Создаем AuthManager")
        try {
            AuthManager(context)
        } catch (e: Exception) {
            android.util.Log.e("RemoteRepositoriesScreen", "Ошибка создания AuthManager: ${e.message}", e)
            throw e
        }
    }

    android.util.Log.d("RemoteRepositoriesScreen", "AuthManager создан, подписываемся на ViewModel")

    val repositories by viewModel.repositories.collectAsState()
    android.util.Log.d("RemoteRepositoriesScreen", "repositories collectAsState готово")

    val isLoading by viewModel.isLoading.collectAsState()
    android.util.Log.d("RemoteRepositoriesScreen", "isLoading collectAsState готово")

    val errorMessage by viewModel.errorMessage.collectAsState()
    android.util.Log.d("RemoteRepositoriesScreen", "errorMessage collectAsState готово")

    val selectedProvider by viewModel.selectedProvider.collectAsState()
    android.util.Log.d("RemoteRepositoriesScreen", "selectedProvider collectAsState готово")

    val isCloning by viewModel.isCloning.collectAsState()
    android.util.Log.d("RemoteRepositoriesScreen", "isCloning collectAsState готово")

    data class PendingClone(val repository: GitRemoteRepository, val localPath: String)
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
                authManager = authManager,
                onStarted = onRepositoryCloned
            )
        } else if (!granted) {
            viewModel.showError(context.getString(R.string.notification_permission_required))
        }
        pendingClone = null
    }

    LaunchedEffect(Unit) {
        android.util.Log.d("RemoteRepositoriesScreen", "LaunchedEffect начался")
        try {
            viewModel.initializeRepositories(authManager)
            android.util.Log.d("RemoteRepositoriesScreen", "initializeRepositories завершен")
        } catch (e: Exception) {
            android.util.Log.e("RemoteRepositoriesScreen", "Ошибка в initializeRepositories: ${e.message}", e)
        }
    }

    android.util.Log.d("RemoteRepositoriesScreen", "Начинаем рендеринг Scaffold")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_repos_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.remote_repos_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshRepositories(authManager) }
                    ) {
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                android.util.Log.d("RemoteRepositoriesScreen", "Вызываем ProviderTabs с selectedProvider=$selectedProvider")

                // Tabs для выбора провайдера
                ProviderTabs(
                    selectedProvider = selectedProvider,
                    onProviderSelected = { provider ->
                        viewModel.selectProvider(provider, authManager)
                    },
                    isGitHubAuthenticated = authManager.isAuthenticated(GitProvider.GITHUB),
                    isGitLabAuthenticated = authManager.isAuthenticated(GitProvider.GITLAB)
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
                        message = errorMessage!!,
                        onRetry = { viewModel.refreshRepositories(authManager) }
                    )
                } else if (repositories.isEmpty()) {
                    EmptyRepositoriesMessage(selectedProvider)
                } else {
                    RepositoriesList(
                        repositories = repositories,
                        isCloning = isCloning,
                        onCloneRepository = { repository, localPath ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingClone = PendingClone(repository, localPath)
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.startCloneInBackground(
                                    context = context,
                                    repository = repository,
                                    localPath = localPath,
                                    authManager = authManager,
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
    selectedProvider: GitProvider?,
    onProviderSelected: (GitProvider) -> Unit,
    isGitHubAuthenticated: Boolean,
    isGitLabAuthenticated: Boolean
) {
    android.util.Log.d("ProviderTabs", "Рендеринг ProviderTabs: selectedProvider=$selectedProvider, github=$isGitHubAuthenticated, gitlab=$isGitLabAuthenticated")

    TabRow(
        selectedTabIndex = when (selectedProvider) {
            GitProvider.GITHUB -> 0
            GitProvider.GITLAB -> 1
            null -> 0 // Вместо -1 используем 0 чтобы избежать падения
        }
    ) {
        Tab(
            selected = selectedProvider == GitProvider.GITHUB,
            onClick = {
                if (isGitHubAuthenticated) {
                    onProviderSelected(GitProvider.GITHUB)
                }
            },
            enabled = isGitHubAuthenticated
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = if (isGitHubAuthenticated) Color(0xFF24292F) else Color.Gray
                )
                Text("GitHub")
                if (!isGitHubAuthenticated) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.remote_repos_not_authorized),
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
            }
        }

        Tab(
            selected = selectedProvider == GitProvider.GITLAB,
            onClick = {
                if (isGitLabAuthenticated) {
                    onProviderSelected(GitProvider.GITLAB)
                }
            },
            enabled = isGitLabAuthenticated
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (isGitLabAuthenticated) Color(0xFFFC6D26) else Color.Gray
                )
                Text("GitLab")
                if (!isGitLabAuthenticated) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.remote_repos_not_authorized),
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
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
    onCloneRepository: (GitRemoteRepository, String) -> Unit
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
                onClone = { localPath ->
                    onCloneRepository(repository, localPath)
                }
            )
        }
    }
}

@Composable
fun RepositoryCard(
    repository: GitRemoteRepository,
    isCloning: Boolean,
    onClone: (String) -> Unit
) {
    var showCloneDialog by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf(false) }
    var pendingClonePath by remember { mutableStateOf<String?>(null) }
    
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
                        when (repository.provider) {
                            GitProvider.GITHUB -> Icons.Default.Code
                            GitProvider.GITLAB -> Icons.Default.Storage
                        },
                        contentDescription = repository.provider.name,
                        modifier = Modifier.size(16.dp),
                        tint = when (repository.provider) {
                            GitProvider.GITHUB -> Color(0xFF24292F)
                            GitProvider.GITLAB -> Color(0xFFFC6D26)
                        }
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
                    text = stringResource(R.string.remote_repos_updated, formatDate(repository.updatedAt)),
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
            onDismiss = { showCloneDialog = false },
            onConfirm = { localPath ->
                showCloneDialog = false
                val requiresWarning = shouldWarnAboutRepositorySize(repository.approximateSizeBytes)
                if (requiresWarning) {
                    pendingClonePath = localPath
                    showSizeWarning = true
                } else {
                    onClone(localPath)
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
                        showSizeWarning = false
                        pendingClonePath = null
                        if (path != null) {
                            onClone(path)
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    val defaultLocalPath = remember(context, repositoryName) {
        val baseDir = AppSettingsManager(context).getRepositoriesBaseDir(context)
        File(baseDir, repositoryName).absolutePath
    }
    var localPath by remember(defaultLocalPath) { mutableStateOf(defaultLocalPath) }

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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(localPath) },
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

    val megabytes = size / 1024.0 / 1024.0
    return if (megabytes >= 1024) {
        String.format(Locale.getDefault(), "%.1f ГБ", megabytes / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f МБ", megabytes)
    }
}

private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
}
