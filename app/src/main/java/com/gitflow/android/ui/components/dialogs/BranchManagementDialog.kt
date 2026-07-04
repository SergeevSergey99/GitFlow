package com.gitflow.android.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.gitflow.android.R
import com.gitflow.android.data.models.Branch
import com.gitflow.android.data.models.RepoOperationState
import com.gitflow.android.data.models.Repository
import com.gitflow.android.ui.screens.main.BranchesViewModel

@Composable
fun BranchManagementDialog(
    repository: Repository,
    currentBranch: String,
    onDismiss: () -> Unit,
    onBranchChanged: () -> Unit
) {
    val viewModel: BranchesViewModel = koinViewModel(
        key = "branches_${repository.id}",
        parameters = { parametersOf(repository) }
    )
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showNewBranchForm by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var checkoutOnCreate by remember { mutableStateOf(true) }

    var branchToDelete by remember { mutableStateOf<Branch?>(null) }
    var mergeTarget by remember { mutableStateOf<Branch?>(null) }
    var rebaseTarget by remember { mutableStateOf<Branch?>(null) }

    // Refresh the host repo state after any successful mutation, but keep the dialog open —
    // conflicts from merge/rebase need the user to stay here (and then resolve in Changes).
    LaunchedEffect(uiState.mutationSignal) {
        if (uiState.mutationSignal > 0) {
            onBranchChanged()
        }
    }

    // Re-read branch and merge/rebase state each time the dialog opens, so a merge/rebase
    // resolved/aborted elsewhere (Changes screen or the conflict header) is reflected here.
    // Also drop any stale message from a previous session so it doesn't linger.
    LaunchedEffect(Unit) {
        viewModel.clearError()
        viewModel.clearMessage()
        viewModel.loadBranches()
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
                // Grab initial focus on a hidden target so the search field does not auto-focus
                // and pop up the keyboard when the dialog opens. Tapping the field still focuses it.
                val focusCatcher = remember { FocusRequester() }
                Box(
                    Modifier
                        .size(1.dp)
                        .focusRequester(focusCatcher)
                        .focusable()
                )
                LaunchedEffect(Unit) { focusCatcher.requestFocus() }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
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

                // Success feedback (merge/rebase/checkout/etc.)
                if (uiState.message != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                uiState.message!!,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Merge/rebase in progress — direct the user to resolve in Changes, offer abort/continue.
                if (uiState.operationState == RepoOperationState.MERGING ||
                    uiState.operationState == RepoOperationState.REBASING
                ) {
                    OperationBanner(
                        state = uiState.operationState,
                        enabled = !uiState.operationInProgress,
                        onContinue = { viewModel.continueRebase() },
                        onAbort = { viewModel.abortOperation() }
                    )
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
                                    onDelete = { branchToDelete = branch },
                                    onMerge = { mergeTarget = branch },
                                    onRebase = { rebaseTarget = branch }
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
                                    onDelete = null,
                                    onMerge = { mergeTarget = branch },
                                    onRebase = { rebaseTarget = branch }
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

    // Merge confirmation dialog
    mergeTarget?.let { branch ->
        MergeRebaseConfirmDialog(
            title = stringResource(R.string.branches_merge_confirm_title),
            text = stringResource(R.string.branches_merge_confirm_text, branch.name, currentBranch),
            onConfirm = {
                viewModel.mergeBranch(branch)
                mergeTarget = null
            },
            onDismiss = { mergeTarget = null }
        )
    }

    // Rebase confirmation dialog
    rebaseTarget?.let { branch ->
        MergeRebaseConfirmDialog(
            title = stringResource(R.string.branches_rebase_confirm_title),
            text = stringResource(R.string.branches_rebase_confirm_text, branch.name, currentBranch),
            onConfirm = {
                viewModel.rebaseOnto(branch)
                rebaseTarget = null
            },
            onDismiss = { rebaseTarget = null }
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
    onDelete: (() -> Unit)?,
    onMerge: () -> Unit,
    onRebase: () -> Unit
) {
    val isCurrent = branch.name == currentBranch
    var menuExpanded by remember { mutableStateOf(false) }
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
            imageVector = if (isCurrent) Icons.Default.AccountTree else Icons.AutoMirrored.Filled.CallSplit,
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
        if (!isCurrent) {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.branches_actions_menu),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.branches_action_merge)) },
                        onClick = {
                            menuExpanded = false
                            onMerge()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.branches_action_rebase)) },
                        onClick = {
                            menuExpanded = false
                            onRebase()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationBanner(
    state: RepoOperationState,
    enabled: Boolean,
    onContinue: () -> Unit,
    onAbort: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(
                    if (state == RepoOperationState.REBASING) R.string.branches_banner_rebasing
                    else R.string.branches_banner_merging
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (state == RepoOperationState.REBASING) {
                    TextButton(onClick = onContinue, enabled = enabled) {
                        Text(stringResource(R.string.branches_banner_continue))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onAbort, enabled = enabled) {
                    Text(
                        stringResource(R.string.branches_banner_abort),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeRebaseConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.branches_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.graph_commit_cancel))
            }
        }
    )
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
