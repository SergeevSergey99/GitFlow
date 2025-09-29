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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.components.FileChangeCard
import kotlinx.coroutines.launch

@Composable
fun ChangesScreen(
    repository: Repository?,
    gitRepository: RealGitRepository
) {
    if (repository == null) {
        EmptyStateMessage("Select a repository to view changes")
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var changes by remember { mutableStateOf<List<FileChange>>(emptyList()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository.id) {
        isLoading = true
        changes = gitRepository.getChangedFiles(repository)
        gitRepository.refreshRepository(repository)
        isLoading = false
        commitMessage = ""
        isProcessing = false
    }

    val stagedFiles = changes.filter { it.stage == ChangeStage.STAGED }
    val unstagedFiles = changes.filter { it.stage == ChangeStage.UNSTAGED }
    val pendingPushCommits = repository.pendingPushCommits

    fun guardLaunch(action: suspend () -> Unit) {
        if (isProcessing) return
        scope.launch {
            isProcessing = true
            try {
                action()
            } finally {
                isProcessing = false
            }
        }
    }

    val stageAll = {
        guardLaunch {
            val result = gitRepository.stageAll(repository)
            if (result.isFailure) {
                val fallback = "Unable to stage all changes"
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
            changes = gitRepository.getChangedFiles(repository)
        }
    }

    val commitChanges = commit@{
        val message = commitMessage.trim()
        if (message.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Commit message cannot be empty") }
            return@commit
        }
        guardLaunch {
            val result = gitRepository.commit(repository, message)
            if (result.isSuccess) {
                commitMessage = ""
                changes = gitRepository.getChangedFiles(repository)
                snackbarHostState.showSnackbar("Commit created")
            } else {
                val fallback = "Commit failed"
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
        }
    }

    val pushChanges = {
        guardLaunch {
            val result = gitRepository.push(repository)
            val message = if (result.success) {
                if (result.message.isNotBlank()) result.message else "Push successful"
            } else {
                if (result.message.isNotBlank()) result.message else "Push failed"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val toggleFile: (FileChange) -> Unit = { file ->
        guardLaunch {
            val result = if (file.stage == ChangeStage.STAGED) {
                gitRepository.unstageFile(repository, file.path)
            } else {
                gitRepository.stageFile(repository, file)
            }

            if (result.isFailure) {
                val fallback = if (file.stage == ChangeStage.STAGED) {
                    "Unable to unstage file"
                } else {
                    "Unable to stage file"
                }
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }

            changes = gitRepository.getChangedFiles(repository)
        }
    }

    ChangesContent(
        isLoading = isLoading,
        isProcessing = isProcessing,
        snackbarHostState = snackbarHostState,
        stagedFiles = stagedFiles,
        unstagedFiles = unstagedFiles,
        commitMessage = commitMessage,
        onCommitMessageChange = { commitMessage = it },
        onStageAll = stageAll,
        onCommit = commitChanges,
        onPush = pushChanges,
        onFileToggle = toggleFile,
        canPush = repository.hasRemoteOrigin,
        pendingPushCommits = pendingPushCommits
    )
}

@Composable
private fun ChangesContent(
    isLoading: Boolean,
    isProcessing: Boolean,
    snackbarHostState: SnackbarHostState,
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    onFileToggle: (FileChange) -> Unit,
    canPush: Boolean,
    pendingPushCommits: Int
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }

                CommitSection(
                    commitMessage = commitMessage,
                    onCommitMessageChange = onCommitMessageChange,
                    stagedFiles = stagedFiles,
                    unstagedFiles = unstagedFiles,
                    onStageAll = onStageAll,
                    onCommit = onCommit,
                    onPush = onPush,
                    isBusy = isProcessing,
                    canPush = canPush,
                    pendingPushCommits = pendingPushCommits
                )

                Spacer(modifier = Modifier.height(16.dp))

                FileChangesList(
                    stagedFiles = stagedFiles,
                    unstagedFiles = unstagedFiles,
                    onFileToggle = onFileToggle
                )
            }
        }
    }
}

@Composable
private fun CommitSection(
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    isBusy: Boolean,
    canPush: Boolean,
    pendingPushCommits: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Commit Changes",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStageAll,
                    modifier = Modifier.weight(1f),
                    enabled = unstagedFiles.isNotEmpty() && !isBusy
                ) {
                    Text("Stage All")
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1f),
                    enabled = stagedFiles.isNotEmpty() && commitMessage.isNotBlank() && !isBusy
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Commit")
                }
            }

            if (canPush) {
                if (pendingPushCommits > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onPush,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push ($pendingPushCommits)")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Remote 'origin' is not configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileChangesList(
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    onFileToggle: (FileChange) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (stagedFiles.isNotEmpty()) {
            item {
                Text(
                    "Staged Changes (${stagedFiles.size})",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(stagedFiles) { file ->
                FileChangeCard(
                    file = file,
                    isStaged = true,
                    onToggle = { onFileToggle(file) }
                )
            }
        }

        if (unstagedFiles.isNotEmpty()) {
            item {
                Text(
                    "Unstaged Changes (${unstagedFiles.size})",
                    fontWeight = FontWeight.Medium
                )
            }
            items(unstagedFiles) { file ->
                FileChangeCard(
                    file = file,
                    isStaged = false,
                    onToggle = { onFileToggle(file) }
                )
            }
        }

        if (stagedFiles.isEmpty() && unstagedFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No changes",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
