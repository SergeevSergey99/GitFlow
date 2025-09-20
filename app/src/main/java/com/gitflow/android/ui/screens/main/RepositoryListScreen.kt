package com.gitflow.android.ui.screens.main

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
import androidx.navigation.NavController
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.CloneProgressCallback
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.components.RepositoryCard
import com.gitflow.android.ui.components.dialogs.AddRepositoryDialog
import com.gitflow.android.ui.components.dialogs.DeleteRepositoryDialog
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

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gitRepository = remember { RealGitRepository(context) }
    val authManager = remember { AuthManager(context) }

    // Observe clone progress
    val cloneProgress by (cloneProgressCallback?.progress?.collectAsState() ?: remember { mutableStateOf(null) })

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
            onAdd = { name, url, isClone ->
                scope.launch {
                    try {
                        isLoading = true
                        errorMessage = null

                        val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val defaultPath = "${appDir.absolutePath}/repositories/$name"


                        if (isClone && url.isNotEmpty()) {
                            // Cloning
                            val callback = CloneProgressCallback()
                            cloneProgressCallback = callback

                            val result = gitRepository.cloneRepository(url,defaultPath, name, callback)
                            result.fold(
                                onSuccess = {
                                    showAddDialog = false
                                    isLoading = false
                                    cloneProgressCallback = null
                                },
                                onFailure = { exception ->
                                    errorMessage = exception.message ?: "Unknown error occurred"
                                    isLoading = false
                                }
                            )
                        } else {
                            // Creating new repository
                            val result = gitRepository.createRepository(name, "")
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
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Unknown error occurred"
                        isLoading = false
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