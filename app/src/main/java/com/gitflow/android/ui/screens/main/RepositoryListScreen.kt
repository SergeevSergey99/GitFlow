package com.gitflow.android.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavController
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.Repository
import com.gitflow.android.ui.components.RepositoryCard
import com.gitflow.android.ui.components.rememberCloneNotificationPrompt
import com.gitflow.android.ui.components.dialogs.AddRepositoryDialog
import com.gitflow.android.ui.components.dialogs.DeleteRepositoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryListScreen(
    repositories: List<Repository>,
    selectedRepositoryId: String? = null,
    onRepositorySelected: (Repository) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    navController: NavController
) {
    val context = LocalContext.current
    val repoViewModel: RepositoryListViewModel = koinViewModel()
    val uiState by repoViewModel.uiState.collectAsState()

    val authManager: AuthManager = koinInject()
    val requestCloneNotifications = rememberCloneNotificationPrompt()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (repositories.isEmpty()) {
            EmptyRepositoryState(
                onAddClick = { repoViewModel.showAddDialog() }
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
                        isSelected = repository.id == selectedRepositoryId,
                        onClick = { onRepositorySelected(repository) },
                        onDelete = { repo, _ ->
                            repoViewModel.showDeleteConfirm(repo)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { repoViewModel.showAddDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.repo_list_add_repository))
        }
    }

    if (uiState.showAddDialog) {
        AddRepositoryDialog(
            onDismiss = { repoViewModel.dismissAddDialog() },
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            authManager = authManager,
            onAdd = { name, url, isClone, approximateSize, targetPath ->
                if (isClone && url.isNotEmpty()) {
                    requestCloneNotifications {
                        repoViewModel.startManualClone(context, authManager, name, url, approximateSize, targetPath)
                    }
                } else {
                    repoViewModel.createRepository(name, targetPath)
                }
            },
            onAddLocal = { path -> repoViewModel.addRepository(path) },
            onNavigateToRemote = { navController.navigate("remote_repositories") }
        )
    }

    val repositoryToDelete = uiState.repositoryToDelete
    if (uiState.showDeleteConfirmDialog && repositoryToDelete != null) {
        DeleteRepositoryDialog(
            repository = repositoryToDelete,
            onDismiss = { repoViewModel.dismissDeleteConfirm() },
            onConfirm = { deleteFiles ->
                repoViewModel.deleteRepository(repositoryToDelete, deleteFiles)
            },
            errorMessage = uiState.deleteMessage
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
            stringResource(R.string.repo_list_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            stringResource(R.string.repo_list_empty_subtitle),
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
            Text(stringResource(R.string.repo_list_add_button))
        }
    }
}
