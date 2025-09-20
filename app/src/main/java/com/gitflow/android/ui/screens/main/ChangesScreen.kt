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
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.components.FileChangeCard
import kotlinx.coroutines.delay
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

    var stagedFiles by remember { mutableStateOf(listOf<FileChange>()) }
    var unstagedFiles by remember { mutableStateOf(listOf<FileChange>()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Загружаем изменения при смене репозитория
    LaunchedEffect(repository) {
        isLoading = true
        unstagedFiles = gitRepository.getChangedFiles(repository)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Commit section
        CommitSection(
            commitMessage = commitMessage,
            onCommitMessageChange = { commitMessage = it },
            stagedFiles = stagedFiles,
            unstagedFiles = unstagedFiles,
            onStageAll = {
                stagedFiles = unstagedFiles
                unstagedFiles = emptyList()
            },
            onCommit = {
                scope.launch {
                    // TODO: Реализовать создание коммита через JGit
                    delay(500)
                    stagedFiles = emptyList()
                    commitMessage = ""
                    // Перезагружаем изменения
                    unstagedFiles = gitRepository.getChangedFiles(repository)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // File changes
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            FileChangesList(
                stagedFiles = stagedFiles,
                unstagedFiles = unstagedFiles,
                onFileToggle = { file, isCurrentlyStaged ->
                    if (isCurrentlyStaged) {
                        // Unstage file
                        unstagedFiles = unstagedFiles + file
                        stagedFiles = stagedFiles - file
                    } else {
                        // Stage file
                        stagedFiles = stagedFiles + file
                        unstagedFiles = unstagedFiles - file
                    }
                }
            )
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
    onCommit: () -> Unit
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
                    enabled = unstagedFiles.isNotEmpty()
                ) {
                    Text("Stage All")
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1f),
                    enabled = stagedFiles.isNotEmpty() && commitMessage.isNotEmpty()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Commit")
                }
            }
        }
    }
}

@Composable
private fun FileChangesList(
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    onFileToggle: (FileChange, Boolean) -> Unit
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
                    onToggle = { onFileToggle(file, true) }
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
                    onToggle = { onFileToggle(file, false) }
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