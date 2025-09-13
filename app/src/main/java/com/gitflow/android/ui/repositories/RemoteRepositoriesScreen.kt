package com.gitflow.android.ui.repositories

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.GitRemoteRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteRepositoriesScreen(
    onNavigateBack: () -> Unit,
    onRepositoryCloned: () -> Unit,
    viewModel: RemoteRepositoriesViewModel = viewModel()
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    
    val repositories by viewModel.repositories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isCloning by viewModel.isCloning.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.initializeRepositories(authManager)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Удаленные репозитории") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshRepositories(authManager) }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            
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
                        Text("Загрузка репозиториев...")
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
                        viewModel.cloneRepository(
                            repository = repository,
                            localPath = localPath,
                            authManager = authManager,
                            onSuccess = onRepositoryCloned
                        )
                    }
                )
            }
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
    TabRow(
        selectedTabIndex = when (selectedProvider) {
            GitProvider.GITHUB -> 0
            GitProvider.GITLAB -> 1
            null -> -1
        }
    ) {
        Tab(
            selected = selectedProvider == GitProvider.GITHUB,
            onClick = { if (isGitHubAuthenticated) onProviderSelected(GitProvider.GITHUB) },
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
                        contentDescription = "Не авторизован",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
            }
        }
        
        Tab(
            selected = selectedProvider == GitProvider.GITLAB,
            onClick = { if (isGitLabAuthenticated) onProviderSelected(GitProvider.GITLAB) },
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
                        contentDescription = "Не авторизован",
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
                    Text("Повторить")
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
                    null -> "Выберите провайдер для просмотра репозиториев"
                    else -> "Репозитории не найдены"
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
                            contentDescription = "Частный",
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
                    text = "Обновлен ${formatDate(repository.updatedAt)}",
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
                        Text("Клонировать")
                    }
                }
            }
        }
    }
    
    if (showCloneDialog) {
        CloneDialog(
            repositoryName = repository.name,
            onDismiss = { showCloneDialog = false },
            onConfirm = { localPath ->
                showCloneDialog = false
                onClone(localPath)
            }
        )
    }
}

@Composable
fun CloneDialog(
    repositoryName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var localPath by remember { mutableStateOf("/storage/emulated/0/GitFlow/$repositoryName") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Клонировать репозиторий") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Выберите путь для клонирования:")
                OutlinedTextField(
                    value = localPath,
                    onValueChange = { localPath = it },
                    label = { Text("Локальный путь") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(localPath) },
                enabled = localPath.isNotBlank()
            ) {
                Text("Клонировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
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
