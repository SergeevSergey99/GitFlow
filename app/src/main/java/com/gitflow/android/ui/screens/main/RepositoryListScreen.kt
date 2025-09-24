package com.gitflow.android.ui.screens.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.CloneProgressCallback
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.components.RepositoryCard
import com.gitflow.android.ui.components.dialogs.AddRepositoryDialog
import com.gitflow.android.ui.components.dialogs.DeleteRepositoryDialog
import com.gitflow.android.services.CloneRepositoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RepositoryListScreen(
    repositories: List<Repository>,
    onRepositorySelected: (Repository) -> Unit,
    navController: NavController
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var repositoryToDelete by remember { mutableStateOf<Repository?>(null) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    var cloneProgressCallback by remember { mutableStateOf<CloneProgressCallback?>(null) }
    data class PendingManualClone(val name: String, val url: String)
    var pendingManualClone by remember { mutableStateOf<PendingManualClone?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gitRepository = remember { RealGitRepository(context) }
    val authManager = remember { AuthManager(context) }

    // Observe clone progress
    val cloneProgress by (cloneProgressCallback?.progress?.collectAsState() ?: remember { mutableStateOf(null) })

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingManualClone
        if (granted && pending != null) {
            isLoading = true
            errorMessage = null
            startManualClone(scope, context, authManager, pending.name, pending.url,
                onSuccess = {
                    showAddDialog = false
                    isLoading = false
                    errorMessage = null
                    cloneProgressCallback = null
                },
                onError = { message ->
                    errorMessage = message
                    isLoading = false
                }
            )
        } else if (!granted) {
            errorMessage = context.getString(R.string.notification_permission_required)
        }
        pendingManualClone = null
    }

    // Log progress changes
    LaunchedEffect(cloneProgress) {
        android.util.Log.d("RepositoryListScreen", "Clone progress updated: $cloneProgress")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (repositories.isEmpty()) {
            EmptyRepositoryState(
                onAddClick = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(repositories) { repository ->
                    RepositoryCard(
                        repository = repository,
                        onClick = { onRepositorySelected(repository) },
                        onDelete = { repo, deleteFiles ->
                            repositoryToDelete = repo
                            showDeleteConfirmDialog = true
                        }
                    )
                }
                // Добавляем пустое пространство снизу для видимости последней карточки
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Repository")
        }
    }

    // Dialogs
    if (showAddDialog) {
        AddRepositoryDialog(
            onDismiss = {
                showAddDialog = false
                errorMessage = null
                isLoading = false
                cloneProgressCallback?.cancel()
                cloneProgressCallback = null
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
            cloneProgress = cloneProgress,
            cloneProgressCallback = cloneProgressCallback,
            authManager = authManager,
            onAdd = { name, url, isClone ->
                if (isClone && url.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingManualClone = PendingManualClone(name, url)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (isClone && url.isNotEmpty()) {
                    isLoading = true
                    errorMessage = null
                    startManualClone(scope, context, authManager, name, url,
                        onSuccess = {
                            showAddDialog = false
                            isLoading = false
                            errorMessage = null
                            cloneProgressCallback = null
                        },
                        onError = { message ->
                            errorMessage = message
                            isLoading = false
                        }
                    )
                } else {
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null

                            // Creating new repository
                            val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val localPath = "${appDir.absolutePath}/repositories/$name"

                            val result = gitRepository.createRepository(name, localPath)
                            result.fold(
                                onSuccess = {
                                    showAddDialog = false
                                    isLoading = false
                                },
                                onFailure = { exception ->
                                    errorMessage = exception.message ?: "Failed to create repository"
                                    isLoading = false
                                }
                            )
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Unknown error occurred"
                            isLoading = false
                        }
                    }
                }
            },
            onAddLocal = { path ->
                scope.launch {
                    try {
                        isLoading = true
                        errorMessage = null

                        val result = gitRepository.addRepository(path)
                        result.fold(
                            onSuccess = {
                                showAddDialog = false
                                isLoading = false
                            },
                            onFailure = { exception ->
                                errorMessage = exception.message ?: "Failed to add repository"
                                isLoading = false
                            }
                        )
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Unknown error occurred"
                        isLoading = false
                    }
                }
            },
            onNavigateToRemote = {
                navController.navigate("remote_repositories")
            }
        )
    }

    if (showDeleteConfirmDialog && repositoryToDelete != null) {
        DeleteRepositoryDialog(
            repository = repositoryToDelete!!,
            onDismiss = {
                showDeleteConfirmDialog = false
                repositoryToDelete = null
                deleteMessage = null
            },
            onConfirm = { deleteFiles ->
                scope.launch {
                    try {
                        val repo = repositoryToDelete!!
                        if (deleteFiles) {
                            val result = gitRepository.removeRepositoryWithFiles(repo.id)
                            result.fold(
                                onSuccess = {
                                    showDeleteConfirmDialog = false
                                    repositoryToDelete = null
                                    deleteMessage = null
                                },
                                onFailure = { exception ->
                                    deleteMessage = exception.message ?: "Failed to delete repository"
                                }
                            )
                        } else {
                            gitRepository.removeRepository(repo.id)
                            showDeleteConfirmDialog = false
                            repositoryToDelete = null
                            deleteMessage = null
                        }
                    } catch (e: Exception) {
                        deleteMessage = e.message ?: "Unknown error occurred"
                    }
                }
            },
            errorMessage = deleteMessage
        )
    }
}

@Composable
private fun EmptyRepositoryState(
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No repositories yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Add your first repository to get started",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Repository")
        }
    }
}

private fun startManualClone(
    scope: CoroutineScope,
    context: Context,
    authManager: AuthManager,
    name: String,
    url: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        try {
            val appDir = context.getExternalFilesDir(null) ?: context.filesDir
            val fallbackName = url.substringAfterLast('/').removeSuffix(".git")
            val targetName = if (name.isBlank()) fallbackName else name
            val targetPath = "${appDir.absolutePath}/repositories/$targetName"

            val finalUrl = if (!url.contains("@")) {
                val githubToken = authManager.getAccessToken(GitProvider.GITHUB)
                when {
                    !githubToken.isNullOrEmpty() && url.contains("github.com") ->
                        url.replace("https://", "https://$githubToken@")
                    else -> {
                        val gitlabToken = authManager.getAccessToken(GitProvider.GITLAB)
                        if (!gitlabToken.isNullOrEmpty() && url.contains("gitlab.com")) {
                            url.replace("https://", "https://$gitlabToken@")
                        } else {
                            url
                        }
                    }
                }
            } else {
                url
            }

            CloneRepositoryService.start(
                context = context,
                repoName = targetName,
                repoFullName = url,
                cloneUrl = finalUrl,
                localPath = targetPath
            )

            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error occurred")
        }
    }
}
