package com.gitflow.android.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.ConflictResolutionStrategy
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.MergeConflict
import com.gitflow.android.data.models.MergeConflictSection
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.RealGitRepository
import com.gitflow.android.ui.components.FileChangeCard
import kotlinx.coroutines.launch

@Composable
fun ChangesScreen(
    repository: Repository?,
    gitRepository: RealGitRepository
) {
    val context = LocalContext.current
    if (repository == null) {
        EmptyStateMessage(stringResource(R.string.changes_select_repo))
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var changes by remember { mutableStateOf<List<FileChange>>(emptyList()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var conflictDetails by remember { mutableStateOf<MergeConflict?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var isConflictLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository.id) {
        isLoading = true
        showConflictDialog = false
        conflictDetails = null
        changes = gitRepository.getChangedFiles(repository)
        gitRepository.refreshRepository(repository)
        isLoading = false
        commitMessage = ""
        isProcessing = false
        isConflictLoading = false
    }

    val stagedFiles = changes.filter { it.stage == ChangeStage.STAGED }
    val unstagedFiles = changes.filter { it.stage == ChangeStage.UNSTAGED }
    val pendingPushCommits = repository.pendingPushCommits

    suspend fun reloadChanges() {
        changes = gitRepository.getChangedFiles(repository)
        gitRepository.refreshRepository(repository)
    }

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
                val fallback = context.getString(R.string.changes_unable_to_stage)
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
            reloadChanges()
        }
    }

    val commitChanges = commit@{
        val message = commitMessage.trim()
        if (message.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.changes_commit_message_empty)) }
            return@commit
        }
        guardLaunch {
            val result = gitRepository.commit(repository, message)
            if (result.isSuccess) {
                commitMessage = ""
                reloadChanges()
                snackbarHostState.showSnackbar(context.getString(R.string.changes_commit_created))
            } else {
                val fallback = context.getString(R.string.changes_commit_failed)
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
        }
    }

    val pushChanges = {
        guardLaunch {
            val result = gitRepository.push(repository)
            val message = if (result.success) {
                if (result.message.isNotBlank()) result.message else context.getString(R.string.changes_push_successful)
            } else {
                if (result.message.isNotBlank()) result.message else context.getString(R.string.changes_push_failed)
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
                    context.getString(R.string.changes_unable_to_unstage_file)
                } else {
                    context.getString(R.string.changes_unable_to_stage_file)
                }
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }

            reloadChanges()
        }
    }

    val acceptOurs: (FileChange) -> Unit = { file ->
        guardLaunch {
            val result = gitRepository.resolveConflict(repository, file.path, ConflictResolutionStrategy.OURS)
            if (result.isSuccess) {
                reloadChanges()
                snackbarHostState.showSnackbar(context.getString(R.string.changes_conflict_resolved_current))
            } else {
                val fallback = context.getString(R.string.changes_conflict_resolve_failed)
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
        }
    }

    val acceptTheirs: (FileChange) -> Unit = { file ->
        guardLaunch {
            val result = gitRepository.resolveConflict(repository, file.path, ConflictResolutionStrategy.THEIRS)
            if (result.isSuccess) {
                reloadChanges()
                snackbarHostState.showSnackbar(context.getString(R.string.changes_conflict_resolved_incoming))
            } else {
                val fallback = context.getString(R.string.changes_conflict_resolve_failed)
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
            }
        }
    }

    val openConflict: (FileChange) -> Unit = { file ->
        scope.launch {
            try {
                isConflictLoading = true
                val conflict = gitRepository.getMergeConflict(repository, file.path)
                if (conflict != null) {
                    conflictDetails = conflict
                    showConflictDialog = true
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.changes_conflict_details_failed))
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.localizedMessage ?: context.getString(R.string.changes_conflict_details_failed))
            } finally {
                isConflictLoading = false
            }
        }
    }

    ChangesContent(
        isLoading = isLoading,
        isProcessing = isProcessing,
        isConflictLoading = isConflictLoading,
        snackbarHostState = snackbarHostState,
        stagedFiles = stagedFiles,
        unstagedFiles = unstagedFiles,
        commitMessage = commitMessage,
        onCommitMessageChange = { commitMessage = it },
        onStageAll = stageAll,
        onCommit = commitChanges,
        onPush = pushChanges,
        onFileToggle = toggleFile,
        onResolveConflict = openConflict,
        onAcceptOurs = acceptOurs,
        onAcceptTheirs = acceptTheirs,
        canPush = repository.hasRemoteOrigin,
        pendingPushCommits = pendingPushCommits
    )

    conflictDetails?.let { details ->
        if (showConflictDialog) {
            MergeConflictDialog(
                conflict = details,
                isBusy = isProcessing,
                onDismiss = {
                    showConflictDialog = false
                    conflictDetails = null
                },
                onApply = { resolvedContent ->
                    guardLaunch {
                        val result = gitRepository.resolveConflictWithContent(repository, details.path, resolvedContent)
                        if (result.isSuccess) {
                            showConflictDialog = false
                            conflictDetails = null
                            reloadChanges()
                            snackbarHostState.showSnackbar(context.getString(R.string.changes_conflict_manual_success))
                        } else {
                            val fallback = context.getString(R.string.changes_conflict_resolve_failed)
                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: fallback)
                        }
                    }
                }
            )
        }
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
    onCommitMessageChange: (String) -> Unit,
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
                    Text(stringResource(R.string.changes_stage_all))
                }
                Button(
                    onClick = onCommit,
                    modifier = Modifier.weight(1f),
                    enabled = stagedFiles.isNotEmpty() && commitMessage.isNotBlank() && !isBusy
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.changes_commit_button))
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
