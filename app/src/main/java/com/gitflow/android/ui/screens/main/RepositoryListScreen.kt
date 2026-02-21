package com.gitflow.android.ui.screens.main

import android.Manifest
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gitflow.android.R
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.components.RepositoryCard
import com.gitflow.android.ui.components.dialogs.AddRepositoryDialog
import com.gitflow.android.ui.components.dialogs.DeleteRepositoryDialog

@Composable
fun RepositoryListScreen(
    repositories: List<Repository>,
    gitRepository: IGitRepository,
    onRepositorySelected: (Repository) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val repoViewModel: RepositoryListViewModel = viewModel(
        factory = RepositoryListViewModel.Factory(application, gitRepository)
    )
    val uiState by repoViewModel.uiState.collectAsState()

    // AuthManager нужен только для clone URL-авторизации — не бизнес-логика, создаём здесь
    val authManager = remember { AuthManager(context) }

    // Pending clone — временное состояние UI для flow с разрешением уведомлений
    data class PendingClone(val name: String, val url: String, val approximateSize: Long?)
    var pendingClone by remember { mutableStateOf<PendingClone?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingClone
        if (granted && pending != null) {
            repoViewModel.startManualClone(context, authManager, pending.name, pending.url, pending.approximateSize)
        } else if (!granted) {
            repoViewModel.setDialogError(context.getString(R.string.notification_permission_required))
        }
        pendingClone = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            onAdd = { name, url, isClone, approximateSize ->
                if (isClone && url.isNotEmpty() &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingClone = PendingClone(name, url, approximateSize)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (isClone && url.isNotEmpty()) {
                    repoViewModel.startManualClone(context, authManager, name, url, approximateSize)
                } else {
                    repoViewModel.createRepository(name)
                }
            },
            onAddLocal = { path -> repoViewModel.addRepository(path) },
            onNavigateToRemote = { navController.navigate("remote_repositories") }
        )
    }

    if (uiState.showDeleteConfirmDialog && uiState.repositoryToDelete != null) {
        DeleteRepositoryDialog(
            repository = uiState.repositoryToDelete!!,
            onDismiss = { repoViewModel.dismissDeleteConfirm() },
            onConfirm = { deleteFiles ->
                repoViewModel.deleteRepository(uiState.repositoryToDelete!!, deleteFiles)
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
