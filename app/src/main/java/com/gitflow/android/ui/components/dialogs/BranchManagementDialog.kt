package com.gitflow.android.ui.components.dialogs

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.R
import com.gitflow.android.data.models.Branch
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.screens.main.BranchesViewModel

@Composable
fun BranchManagementDialog(
    repository: Repository,
    gitRepository: IGitRepository,
    currentBranch: String,
    onDismiss: () -> Unit,
    onBranchChanged: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: BranchesViewModel = viewModel(
        key = "branches_${repository.id}",
        factory = BranchesViewModel.Factory(application, repository, gitRepository)
    )
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showNewBranchForm by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var checkoutOnCreate by remember { mutableStateOf(true) }

    var branchToDelete by remember { mutableStateOf<Branch?>(null) }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            onBranchChanged()
            viewModel.clearMessage()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.branches_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.commit_detail_close))
                    }
                }

                HorizontalDivider()

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.branches_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                // Error card
                if (uiState.errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                val localBranches = uiState.branches
                    .filter { it.isLocal && it.name.contains(searchQuery, ignoreCase = true) }
                val remoteBranches = uiState.branches
                    .filter { !it.isLocal && it.name.contains(searchQuery, ignoreCase = true) }

                // Branch list
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            SectionHeader(stringResource(R.string.branches_section_local))
                        }
                        if (localBranches.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.branches_no_branches),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(localBranches) { branch ->
                                BranchRow(
                                    branch = branch,
                                    currentBranch = currentBranch,
                                    onCheckout = { viewModel.checkoutBranch(branch) },
                                    onDelete = { branchToDelete = branch }
                                )
                            }
                        }
                        item {
                            SectionHeader(stringResource(R.string.branches_section_remote))
                        }
                        if (remoteBranches.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.branches_no_branches),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(remoteBranches) { branch ->
                                BranchRow(
                                    branch = branch,
                                    currentBranch = currentBranch,
                                    onCheckout = { viewModel.checkoutBranch(branch) },
                                    onDelete = null
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // New branch form / button
                if (showNewBranchForm) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = newBranchName,
                            onValueChange = { newBranchName = it },
                            label = { Text(stringResource(R.string.branches_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Checkbox(
                                checked = checkoutOnCreate,
                                onCheckedChange = { checkoutOnCreate = it }
                            )
                            Text(stringResource(R.string.branches_checkout_on_create))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showNewBranchForm = false
                                newBranchName = ""
                            }) {
                                Text(stringResource(R.string.graph_commit_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.createBranch(newBranchName, checkoutOnCreate)
                                    showNewBranchForm = false
                                    newBranchName = ""
                                },
                                enabled = newBranchName.isNotBlank() && !uiState.operationInProgress
                            ) {
                                Text(stringResource(R.string.branches_create))
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showNewBranchForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.branches_new_branch))
                    }
                }

                if (uiState.operationInProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Delete branch confirmation dialog
    branchToDelete?.let { branch ->
        DeleteBranchConfirmDialog(
            branchName = branch.name,
            onConfirm = { force ->
                viewModel.deleteBranch(branch, force)
                branchToDelete = null
            },
            onDismiss = { branchToDelete = null }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun BranchRow(
    branch: Branch,
    currentBranch: String,
    onCheckout: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val isCurrent = branch.name == currentBranch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isCurrent) Modifier.clickable(onClick = onCheckout) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCurrent) Icons.Default.AccountTree else Icons.Default.CallSplit,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                branch.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (branch.ahead > 0 || branch.behind > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (branch.ahead > 0) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("↑${branch.ahead}", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                    if (branch.behind > 0) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("↓${branch.behind}", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
        if (isCurrent) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!isCurrent && branch.isLocal && onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DeleteBranchConfirmDialog(
    branchName: String,
    onConfirm: (force: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var force by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.branches_delete_title)) },
        text = {
            Column {
                Text(branchName, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = force, onCheckedChange = { force = it })
                    Text(
                        stringResource(R.string.branches_force_delete),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(force) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete_repo_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.graph_commit_cancel))
            }
        }
    )
}
