package com.gitflow.android.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.MergeConflict
import com.gitflow.android.data.models.MergeConflictSection
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.StashEntry
import com.gitflow.android.data.models.SyncProgress
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.components.FileChangeCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesScreen(
    repository: Repository?,
    gitRepository: IGitRepository
) {
    if (repository == null) {
        EmptyStateMessage(stringResource(R.string.changes_select_repo))
        return
    }

    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: ChangesViewModel = viewModel(
        key = repository.id,
        factory = ChangesViewModelFactory(application, gitRepository, repository)
    )
    val uiState by viewModel.uiState.collectAsState()

    // Sync repository metadata (pendingPushCommits, canPush) when MainScreen's flow updates them
    LaunchedEffect(repository.pendingPushCommits, repository.hasRemoteOrigin) {
        viewModel.onRepositoryUpdated(repository)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show transient snackbar messages emitted by the ViewModel
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val stagedFiles = remember(uiState.changes) {
        uiState.changes.filter { it.stage == ChangeStage.STAGED }
    }
    val unstagedFiles = remember(uiState.changes) {
        uiState.changes.filter { it.stage == ChangeStage.UNSTAGED }
    }

    var isManualRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) isManualRefreshing = false
    }

    PullToRefreshBox(
        isRefreshing = isManualRefreshing,
        onRefresh = {
            isManualRefreshing = true
            viewModel.loadChanges()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        ChangesContent(
            isLoading = uiState.isLoading,
            isProcessing = uiState.isProcessing,
            isConflictLoading = uiState.isConflictLoading,
            snackbarHostState = snackbarHostState,
            stagedFiles = stagedFiles,
            unstagedFiles = unstagedFiles,
            commitMessage = uiState.commitMessage,
            isAmendMode = uiState.isAmendMode,
            onCommitMessageChange = viewModel::setCommitMessage,
            onToggleAmend = viewModel::toggleAmendMode,
            onStashOpen = viewModel::openStashDialog,
            onStageAll = viewModel::stageAll,
            onCommit = viewModel::commit,
            onPush = viewModel::push,
            onFileToggle = viewModel::toggleFile,
            onResolveConflict = viewModel::openConflict,
            onAcceptOurs = viewModel::acceptOurs,
            onAcceptTheirs = viewModel::acceptTheirs,
            canPush = uiState.canPush,
            pendingPushCommits = uiState.pendingPushCommits
        )

        // Push progress overlay
        uiState.syncProgress?.let { progress ->
            SyncProgressBanner(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    uiState.conflictDetails?.let { details ->
        MergeConflictDialog(
            conflict = details,
            isBusy = uiState.isProcessing,
            onDismiss = viewModel::dismissConflict,
            onApply = viewModel::resolveConflictWithContent
        )
    }

    if (uiState.showStashDialog) {
        StashDialog(
            stashList = uiState.stashList,
            isLoading = uiState.isStashLoading,
            isBusy = uiState.isProcessing,
            onDismiss = viewModel::dismissStashDialog,
            onSave = viewModel::stashSave,
            onApply = viewModel::stashApply,
            onPop = viewModel::stashPop,
            onDrop = viewModel::stashDrop
        )
    }
}

@Composable
private fun ChangesContent(
    isLoading: Boolean,
    isProcessing: Boolean,
    isConflictLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    commitMessage: String,
    isAmendMode: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onToggleAmend: () -> Unit,
    onStashOpen: () -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    onFileToggle: (FileChange) -> Unit,
    onResolveConflict: (FileChange) -> Unit,
    onAcceptOurs: (FileChange) -> Unit,
    onAcceptTheirs: (FileChange) -> Unit,
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
                if (isProcessing || isConflictLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }

                CommitSection(
                    commitMessage = commitMessage,
                    isAmendMode = isAmendMode,
                    onCommitMessageChange = onCommitMessageChange,
                    onToggleAmend = onToggleAmend,
                    onStashOpen = onStashOpen,
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
                    onFileToggle = onFileToggle,
                    onResolveConflict = onResolveConflict,
                    onAcceptOurs = onAcceptOurs,
                    onAcceptTheirs = onAcceptTheirs
                )
            }
        }
    }
}

@Composable
private fun CommitSection(
    commitMessage: String,
    isAmendMode: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onToggleAmend: () -> Unit,
    onStashOpen: () -> Unit,
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
                stringResource(R.string.changes_commit_title),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                label = { Text(stringResource(R.string.changes_commit_message_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAmendMode,
                    onCheckedChange = { onToggleAmend() },
                    enabled = !isBusy
                )
                Text(
                    text = stringResource(R.string.changes_amend_last_commit),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAmendMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStageAll,
                    modifier = Modifier.weight(1f),
                    enabled = unstagedFiles.isNotEmpty() && !isBusy
                ) {
                    Text(stringResource(R.string.changes_stage_all))
                }
                OutlinedButton(
                    onClick = onStashOpen,
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.changes_stash_button))
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1f),
                    enabled = (isAmendMode || stagedFiles.isNotEmpty()) && commitMessage.isNotBlank() && !isBusy
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isAmendMode) stringResource(R.string.changes_amend_button)
                        else stringResource(R.string.changes_commit_button)
                    )
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
                        Text(stringResource(R.string.changes_push_button, pendingPushCommits))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.changes_remote_not_configured),
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
    onFileToggle: (FileChange) -> Unit,
    onResolveConflict: (FileChange) -> Unit,
    onAcceptOurs: (FileChange) -> Unit,
    onAcceptTheirs: (FileChange) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (stagedFiles.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.changes_staged_title, stagedFiles.size),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(stagedFiles) { file ->
                FileChangeCard(
                    file = file,
                    isStaged = true,
                    onToggle = { onFileToggle(file) },
                    onResolveConflict = { onResolveConflict(file) },
                    onAcceptOurs = { onAcceptOurs(file) },
                    onAcceptTheirs = { onAcceptTheirs(file) }
                )
            }
        }

        if (unstagedFiles.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.changes_unstaged_title, unstagedFiles.size),
                    fontWeight = FontWeight.Medium
                )
            }
            items(unstagedFiles) { file ->
                FileChangeCard(
                    file = file,
                    isStaged = false,
                    onToggle = { onFileToggle(file) },
                    onResolveConflict = { onResolveConflict(file) },
                    onAcceptOurs = { onAcceptOurs(file) },
                    onAcceptTheirs = { onAcceptTheirs(file) }
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
                            stringResource(R.string.changes_no_changes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private enum class SectionChoice {
    OURS,
    THEIRS,
    CUSTOM
}

private data class SectionUiState(
    val section: MergeConflictSection,
    val choice: MutableState<SectionChoice>,
    val customText: MutableState<String>
)

@Composable
private fun MergeConflictDialog(
    conflict: MergeConflict,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val sectionStates = remember(conflict) {
        conflict.sections.map { section ->
            SectionUiState(
                section = section,
                choice = mutableStateOf(SectionChoice.OURS),
                customText = mutableStateOf(section.oursContent)
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 200.dp, max = 620.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = conflict.path,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.changes_conflict_choose_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                sectionStates.forEachIndexed { index, state ->
                    ConflictSectionEditor(
                        index = index,
                        state = state,
                        oursLabel = conflict.oursLabel,
                        theirsLabel = conflict.theirsLabel
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.changes_conflict_cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val selections = sectionStates.map { state ->
                                when (state.choice.value) {
                                    SectionChoice.OURS -> state.section.oursContent
                                    SectionChoice.THEIRS -> state.section.theirsContent
                                    SectionChoice.CUSTOM -> state.customText.value
                                }
                            }
                            val resolvedContent = buildResolvedContent(conflict, selections)
                            onApply(resolvedContent)
                        },
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.changes_conflict_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictSectionEditor(
    index: Int,
    state: SectionUiState,
    oursLabel: String,
    theirsLabel: String
) {
    val section = state.section
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.changes_conflict_number, index + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.choice.value == SectionChoice.OURS,
                    onClick = {
                        state.choice.value = SectionChoice.OURS
                        state.customText.value = section.oursContent
                    },
                    label = { Text(stringResource(R.string.changes_conflict_ours_label, oursLabel)) }
                )
                FilterChip(
                    selected = state.choice.value == SectionChoice.THEIRS,
                    onClick = {
                        state.choice.value = SectionChoice.THEIRS
                        state.customText.value = section.theirsContent
                    },
                    label = { Text(stringResource(R.string.changes_conflict_theirs_label, theirsLabel)) }
                )
                FilterChip(
                    selected = state.choice.value == SectionChoice.CUSTOM,
                    onClick = {
                        state.choice.value = SectionChoice.CUSTOM
                    },
                    label = { Text(stringResource(R.string.changes_conflict_manual)) }
                )
            }

            section.baseContent?.let { base ->
                SectionDiffPreview(
                    title = stringResource(R.string.changes_conflict_base_version),
                    content = base
                )
            }

            SectionDiffPreview(
                title = stringResource(R.string.changes_conflict_ours_branch),
                content = section.oursContent
            )
            SectionDiffPreview(
                title = stringResource(R.string.changes_conflict_theirs_branch),
                content = section.theirsContent
            )

            if (state.choice.value == SectionChoice.CUSTOM) {
                OutlinedTextField(
                    value = state.customText.value,
                    onValueChange = { state.customText.value = it },
                    label = { Text(stringResource(R.string.changes_conflict_custom_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        }
    }
}

@Composable
private fun SectionDiffPreview(
    title: String,
    content: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (content.isEmpty()) stringResource(R.string.changes_conflict_empty) else content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun buildResolvedContent(conflict: MergeConflict, selections: List<String>): String {
    val lines = conflict.originalLines
    val result = mutableListOf<String>()
    var cursor = 0

    conflict.sections.forEachIndexed { index, section ->
        while (cursor < section.startLineIndex && cursor < lines.size) {
            result.add(lines[cursor])
            cursor++
        }

        val replacement = selections.getOrNull(index).orEmpty()
        if (replacement.isNotEmpty()) {
            result.addAll(replacement.split("\n"))
        }

        cursor = section.endLineIndex.coerceAtMost(lines.size)
    }

    while (cursor < lines.size) {
        result.add(lines[cursor])
        cursor++
    }

    return result.joinToString("\n")
}

@Composable
private fun StashDialog(
    stashList: List<StashEntry>,
    isLoading: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit
) {
    var stashMessage by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changes_stash_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Save new stash section
                OutlinedTextField(
                    value = stashMessage,
                    onValueChange = { stashMessage = it },
                    label = { Text(stringResource(R.string.changes_stash_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isBusy
                )
                Button(
                    onClick = {
                        onSave(stashMessage)
                        stashMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.changes_stash_save_button))
                }

                HorizontalDivider()

                // Stash list
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (stashList.isEmpty()) {
                    Text(
                        text = stringResource(R.string.changes_stash_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stashList.forEach { entry ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = entry.message.ifBlank { "stash@{${entry.index}}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${entry.branch} · ${dateFormat.format(Date(entry.timestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { onApply(entry.index) },
                                            enabled = !isBusy,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.changes_stash_apply))
                                        }
                                        OutlinedButton(
                                            onClick = { onPop(entry.index) },
                                            enabled = !isBusy,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.changes_stash_pop))
                                        }
                                        OutlinedButton(
                                            onClick = { onDrop(entry.index) },
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.changes_conflict_cancel))
            }
        }
    )
}

@Composable
private fun SyncProgressBanner(
    progress: SyncProgress,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.task.ifBlank { stringResource(R.string.push_progress_pushing) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                val fraction = progress.fraction
                if (fraction != null && progress.totalWork > 0) {
                    Text(
                        text = "${progress.done} / ${progress.totalWork}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val fraction = progress.fraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
