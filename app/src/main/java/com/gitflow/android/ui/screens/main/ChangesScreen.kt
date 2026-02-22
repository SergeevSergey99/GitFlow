package com.gitflow.android.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.R
import com.gitflow.android.data.models.ChangeStatus
import com.gitflow.android.data.models.ChangeStage
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.FileDiff
import com.gitflow.android.data.models.FileChange
import com.gitflow.android.data.models.MergeConflict
import com.gitflow.android.data.models.MergeConflictSection
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.models.StashEntry
import com.gitflow.android.data.models.SyncProgress
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.ui.components.FileChangeCard
import com.gitflow.android.ui.components.StartEllipsizedText
import com.gitflow.android.ui.screens.SideBySideDiffView
import com.gitflow.android.ui.screens.UnifiedDiffView
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val viewModel: ChangesViewModel = viewModel(
        key = repository.id,
        factory = ChangesViewModelFactory(application, gitRepository, repository)
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedDiff by remember { mutableStateOf<FileDiff?>(null) }
    var isDiffLoading by remember { mutableStateOf(false) }
    var diffError by remember { mutableStateOf<String?>(null) }

    var historyTargetPath by remember { mutableStateOf<String?>(null) }
    var historyCommits by remember { mutableStateOf<List<Commit>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

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
            commitDescription = uiState.commitDescription,
            isAmendMode = uiState.isAmendMode,
            onCommitMessageChange = viewModel::setCommitMessage,
            onCommitDescriptionChange = viewModel::setCommitDescription,
            onToggleAmend = viewModel::toggleAmendMode,
            onStashOpen = viewModel::openStashDialog,
            onStageAll = viewModel::stageAll,
            onFetch = viewModel::fetch,
            onPull = viewModel::pull,
            onCommit = viewModel::commit,
            onPush = viewModel::push,
            onFileToggle = viewModel::toggleFile,
            onFilesToggle = viewModel::toggleFiles,
            onDiscardFile = viewModel::discardFileChanges,
            onOpenFileDiff = { file ->
                scope.launch {
                    isDiffLoading = true
                    diffError = null
                    try {
                        val diff = gitRepository.getWorkingFileDiff(repository, file.path, file.stage)
                        if (diff != null) {
                            selectedDiff = diff
                        } else {
                            diffError = context.getString(R.string.changes_diff_unavailable)
                        }
                    } catch (_: Exception) {
                        diffError = context.getString(R.string.changes_diff_unavailable)
                    } finally {
                        isDiffLoading = false
                    }
                }
            },
            onOpenFileHistory = { path ->
                historyTargetPath = path
                isHistoryLoading = true
                historyError = null
                historyCommits = emptyList()
                scope.launch {
                    try {
                        val commits = gitRepository.getFileHistoryForPath(repository, path)
                        historyCommits = commits
                        if (commits.isEmpty()) {
                            historyError = context.getString(R.string.commit_detail_history_no_changes)
                        }
                    } catch (_: Exception) {
                        historyError = context.getString(R.string.commit_detail_history_error)
                    } finally {
                        isHistoryLoading = false
                    }
                }
            },
            onResolveConflict = viewModel::openConflict,
            onAcceptOurs = viewModel::acceptOurs,
            onAcceptTheirs = viewModel::acceptTheirs,
            canPush = uiState.canPush,
            pendingPushCommits = uiState.pendingPushCommits,
            pendingPullCommits = uiState.pendingPullCommits
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

    if (isDiffLoading) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.changes_diff_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.commit_detail_diff_loading))
                }
            }
        )
    }

    diffError?.let { error ->
        AlertDialog(
            onDismissRequest = { diffError = null },
            title = { Text(stringResource(R.string.changes_diff_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { diffError = null }) {
                    Text(stringResource(R.string.commit_detail_history_close))
                }
            }
        )
    }

    selectedDiff?.let { diff ->
        WorkingTreeDiffDialog(
            diff = diff,
            onDismiss = { selectedDiff = null }
        )
    }

    historyTargetPath?.let { path ->
        FileHistoryDialog(
            path = path,
            commits = historyCommits,
            isLoading = isHistoryLoading,
            error = historyError,
            onDismiss = { historyTargetPath = null }
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
    commitDescription: String,
    isAmendMode: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onCommitDescriptionChange: (String) -> Unit,
    onToggleAmend: () -> Unit,
    onStashOpen: () -> Unit,
    onStageAll: () -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    onFileToggle: (FileChange) -> Unit,
    onFilesToggle: (List<FileChange>) -> Unit,
    onDiscardFile: (String) -> Unit,
    onOpenFileDiff: (FileChange) -> Unit,
    onOpenFileHistory: (String) -> Unit,
    onResolveConflict: (FileChange) -> Unit,
    onAcceptOurs: (FileChange) -> Unit,
    onAcceptTheirs: (FileChange) -> Unit,
    canPush: Boolean,
    pendingPushCommits: Int,
    pendingPullCommits: Int
) {
    var isCommitSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var isTreeView by rememberSaveable { mutableStateOf(false) }
    var contextMenuTargetPath by remember { mutableStateOf<String?>(null) }

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

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isCommitSectionExpanded) {
                            CommitSection(
                                commitMessage = commitMessage,
                                commitDescription = commitDescription,
                                isAmendMode = isAmendMode,
                                onCommitMessageChange = onCommitMessageChange,
                                onCommitDescriptionChange = onCommitDescriptionChange,
                                onToggleAmend = onToggleAmend,
                                onStashOpen = onStashOpen,
                                stagedFiles = stagedFiles,
                                unstagedFiles = unstagedFiles,
                                onStageAll = onStageAll,
                                onFetch = onFetch,
                                onPull = onPull,
                                onCommit = onCommit,
                                onPush = onPush,
                                isBusy = isProcessing,
                                canPush = canPush,
                                pendingPushCommits = pendingPushCommits,
                                pendingPullCommits = pendingPullCommits
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        FileChangesList(
                            modifier = Modifier.weight(1f),
                            stagedFiles = stagedFiles,
                            unstagedFiles = unstagedFiles,
                            isTreeView = isTreeView,
                            contextMenuTargetPath = contextMenuTargetPath,
                            onContextMenuTargetChange = { contextMenuTargetPath = it },
                            onToggleViewMode = { isTreeView = !isTreeView },
                            onFileToggle = onFileToggle,
                            onFilesToggle = onFilesToggle,
                            onDiscardFile = onDiscardFile,
                            onOpenFileDiff = onOpenFileDiff,
                            onOpenFileHistory = onOpenFileHistory,
                            onResolveConflict = onResolveConflict,
                            onAcceptOurs = onAcceptOurs,
                            onAcceptTheirs = onAcceptTheirs
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-16).dp),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        IconButton(
                            onClick = { isCommitSectionExpanded = !isCommitSectionExpanded },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (isCommitSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCommitSectionExpanded) {
                                    stringResource(R.string.changes_hide_commit_controls)
                                } else {
                                    stringResource(R.string.changes_show_commit_controls)
                                },
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitSection(
    commitMessage: String,
    commitDescription: String,
    isAmendMode: Boolean,
    onCommitMessageChange: (String) -> Unit,
    onCommitDescriptionChange: (String) -> Unit,
    onToggleAmend: () -> Unit,
    onStashOpen: () -> Unit,
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    onStageAll: () -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    isBusy: Boolean,
    canPush: Boolean,
    pendingPushCommits: Int,
    pendingPullCommits: Int
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
            OutlinedTextField(
                value = commitDescription,
                onValueChange = onCommitDescriptionChange,
                label = { Text(stringResource(R.string.changes_commit_description_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6
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
                    AdaptiveSingleLineButtonText(
                        text = if (isAmendMode) stringResource(R.string.changes_amend_button)
                        else stringResource(R.string.changes_commit_button)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onFetch,
                    modifier = Modifier.weight(1f),
                    enabled = canPush && !isBusy
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.changes_fetch_button),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = onPull,
                    modifier = Modifier.weight(1f),
                    enabled = canPush && !isBusy
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (pendingPullCommits > 0) {
                            stringResource(R.string.changes_pull_button_count, pendingPullCommits)
                        } else {
                            stringResource(R.string.changes_pull_button)
                        },
                        maxLines = 1,
                        softWrap = false
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
private fun AdaptiveSingleLineButtonText(text: String) {
    var compact by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        fontSize = if (compact) 11.sp else 14.sp,
        onTextLayout = { layoutResult ->
            if (!compact && layoutResult.hasVisualOverflow) {
                compact = true
            }
        }
    )
}

@Composable
private fun FileChangesList(
    modifier: Modifier = Modifier,
    stagedFiles: List<FileChange>,
    unstagedFiles: List<FileChange>,
    isTreeView: Boolean,
    contextMenuTargetPath: String?,
    onContextMenuTargetChange: (String?) -> Unit,
    onToggleViewMode: () -> Unit,
    onFileToggle: (FileChange) -> Unit,
    onFilesToggle: (List<FileChange>) -> Unit,
    onDiscardFile: (String) -> Unit,
    onOpenFileDiff: (FileChange) -> Unit,
    onOpenFileHistory: (String) -> Unit,
    onResolveConflict: (FileChange) -> Unit,
    onAcceptOurs: (FileChange) -> Unit,
    onAcceptTheirs: (FileChange) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (stagedFiles.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.changes_staged_title, stagedFiles.size),
                    isTreeView = isTreeView,
                    onToggleViewMode = onToggleViewMode,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isTreeView) {
                item {
                    FileChangesTreeSection(
                        files = stagedFiles,
                        isStaged = true,
                        contextMenuTargetPath = contextMenuTargetPath,
                        onContextMenuTargetChange = onContextMenuTargetChange,
                        onFileToggle = onFileToggle,
                        onFilesToggle = onFilesToggle,
                        onOpenFileDiff = onOpenFileDiff,
                        onOpenFileHistory = onOpenFileHistory,
                        onDiscardFile = onDiscardFile
                    )
                }
            } else {
                items(stagedFiles) { file ->
                    FileChangeCard(
                        file = file,
                        isStaged = true,
                        onToggle = { onFileToggle(file) },
                        onOpen = { onOpenFileDiff(file) },
                        onLongPress = { onContextMenuTargetChange(file.path) },
                        isMenuExpanded = contextMenuTargetPath == file.path,
                        onDismissMenu = { onContextMenuTargetChange(null) },
                        menuContent = {
                            FileChangeActionsMenu(
                                file = file,
                                onDismiss = { onContextMenuTargetChange(null) },
                                onOpenHistory = { onOpenFileHistory(file.path) },
                                onDiscard = { onDiscardFile(file.path) }
                            )
                        },
                        onResolveConflict = { onResolveConflict(file) },
                        onAcceptOurs = { onAcceptOurs(file) },
                        onAcceptTheirs = { onAcceptTheirs(file) }
                    )
                }
            }
        }

        if (unstagedFiles.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.changes_unstaged_title, unstagedFiles.size),
                    isTreeView = isTreeView,
                    onToggleViewMode = onToggleViewMode,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isTreeView) {
                item {
                    FileChangesTreeSection(
                        files = unstagedFiles,
                        isStaged = false,
                        contextMenuTargetPath = contextMenuTargetPath,
                        onContextMenuTargetChange = onContextMenuTargetChange,
                        onFileToggle = onFileToggle,
                        onFilesToggle = onFilesToggle,
                        onOpenFileDiff = onOpenFileDiff,
                        onOpenFileHistory = onOpenFileHistory,
                        onDiscardFile = onDiscardFile
                    )
                }
            } else {
                items(unstagedFiles) { file ->
                    FileChangeCard(
                        file = file,
                        isStaged = false,
                        onToggle = { onFileToggle(file) },
                        onOpen = { onOpenFileDiff(file) },
                        onLongPress = { onContextMenuTargetChange(file.path) },
                        isMenuExpanded = contextMenuTargetPath == file.path,
                        onDismissMenu = { onContextMenuTargetChange(null) },
                        menuContent = {
                            FileChangeActionsMenu(
                                file = file,
                                onDismiss = { onContextMenuTargetChange(null) },
                                onOpenHistory = { onOpenFileHistory(file.path) },
                                onDiscard = { onDiscardFile(file.path) }
                            )
                        },
                        onResolveConflict = { onResolveConflict(file) },
                        onAcceptOurs = { onAcceptOurs(file) },
                        onAcceptTheirs = { onAcceptTheirs(file) }
                    )
                }
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

@Composable
private fun SectionHeader(
    title: String,
    isTreeView: Boolean,
    onToggleViewMode: () -> Unit,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleViewMode,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isTreeView) Icons.Default.ViewList else Icons.Default.AccountTree,
                contentDescription = if (isTreeView) stringResource(R.string.commit_detail_tab_files) else stringResource(R.string.commit_detail_tab_file_tree),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class FileTreeEntry(
    val name: String,
    val path: String,
    val isFile: Boolean,
    val file: FileChange? = null,
    val children: List<FileTreeEntry> = emptyList()
)

private fun buildFileTree(files: List<FileChange>): List<FileTreeEntry> {
    data class MutableNode(
        val name: String,
        val path: String,
        var file: FileChange? = null,
        val children: MutableMap<String, MutableNode> = linkedMapOf()
    )

    val root = MutableNode(name = "", path = "")
    files.forEach { file ->
        val parts = file.path.split('/')
        var current = root
        parts.forEachIndexed { index, part ->
            val childPath = if (current.path.isEmpty()) part else "${current.path}/$part"
            val child = current.children.getOrPut(part) { MutableNode(name = part, path = childPath) }
            if (index == parts.lastIndex) {
                child.file = file
            }
            current = child
        }
    }

    fun toImmutable(node: MutableNode): FileTreeEntry {
        val sortedChildren = node.children.values
            .map(::toImmutable)
            .sortedWith(compareBy<FileTreeEntry> { it.isFile }.thenBy { it.name })
        return FileTreeEntry(
            name = node.name,
            path = node.path,
            isFile = node.file != null,
            file = node.file,
            children = sortedChildren
        )
    }

    fun collapse(node: FileTreeEntry, isRoot: Boolean = false): FileTreeEntry {
        if (node.isFile) return node
        val collapsedChildren = node.children.map { collapse(it) }
        if (!isRoot && collapsedChildren.size == 1 && !collapsedChildren.first().isFile) {
            val child = collapsedChildren.first()
            return collapse(
                FileTreeEntry(
                    name = "${node.name}/${child.name}",
                    path = child.path,
                    isFile = false,
                    children = child.children
                )
            )
        }
        return node.copy(children = collapsedChildren)
    }

    return root.children.values.map { collapse(toImmutable(it), isRoot = true) }
}

private fun collectLeafFiles(node: FileTreeEntry): List<FileChange> {
    if (node.isFile) return listOfNotNull(node.file)
    return node.children.flatMap(::collectLeafFiles)
}

@Composable
private fun FileChangesTreeSection(
    files: List<FileChange>,
    isStaged: Boolean,
    contextMenuTargetPath: String?,
    onContextMenuTargetChange: (String?) -> Unit,
    onFileToggle: (FileChange) -> Unit,
    onFilesToggle: (List<FileChange>) -> Unit,
    onOpenFileDiff: (FileChange) -> Unit,
    onOpenFileHistory: (String) -> Unit,
    onDiscardFile: (String) -> Unit
) {
    val nodes = remember(files) { buildFileTree(files) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nodes.forEach { node ->
            FileTreeRow(
                node = node,
                level = 0,
                isStaged = isStaged,
                contextMenuTargetPath = contextMenuTargetPath,
                onContextMenuTargetChange = onContextMenuTargetChange,
                onFileToggle = onFileToggle,
                onFilesToggle = onFilesToggle,
                onOpenFileDiff = onOpenFileDiff,
                onOpenFileHistory = onOpenFileHistory,
                onDiscardFile = onDiscardFile
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileTreeEntry,
    level: Int,
    isStaged: Boolean,
    contextMenuTargetPath: String?,
    onContextMenuTargetChange: (String?) -> Unit,
    onFileToggle: (FileChange) -> Unit,
    onFilesToggle: (List<FileChange>) -> Unit,
    onOpenFileDiff: (FileChange) -> Unit,
    onOpenFileHistory: (String) -> Unit,
    onDiscardFile: (String) -> Unit
) {
    var expanded by remember(node.path) { mutableStateOf(true) }
    val allFiles = remember(node) { collectLeafFiles(node) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 12).dp)
            .combinedClickable(
                onClick = {
                    if (node.isFile) {
                        node.file?.let(onOpenFileDiff)
                    } else {
                        expanded = !expanded
                        if (allFiles.isNotEmpty()) onFilesToggle(allFiles)
                    }
                },
                onLongClick = {
                    if (node.isFile) onContextMenuTargetChange(node.path)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isStaged,
                onCheckedChange = {
                    if (node.isFile) {
                        node.file?.let(onFileToggle)
                    } else if (allFiles.isNotEmpty()) {
                        onFilesToggle(allFiles)
                    }
                }
            )
            Icon(
                imageVector = when {
                    node.isFile -> fileStatusIcon(node.file?.status ?: ChangeStatus.MODIFIED)
                    expanded -> Icons.Default.FolderOpen
                    else -> Icons.Default.Folder
                },
                contentDescription = null,
                tint = if (node.isFile) fileStatusColor(node.file?.status ?: ChangeStatus.MODIFIED)
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StartEllipsizedText(
                text = node.name,
                modifier = Modifier.weight(1f),
                style = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            if (node.isFile) {
                val file = node.file
                if (file != null) {
                    Text(
                        text = "+${file.additions}/-${file.deletions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DropdownMenu(
            expanded = contextMenuTargetPath == node.path && node.isFile,
            onDismissRequest = { onContextMenuTargetChange(null) }
        ) {
            node.file?.let { file ->
                FileChangeActionsMenu(
                    file = file,
                    onDismiss = { onContextMenuTargetChange(null) },
                    onOpenHistory = { onOpenFileHistory(file.path) },
                    onDiscard = { onDiscardFile(file.path) }
                )
            }
        }
    }

    if (!node.isFile && expanded) {
        node.children.forEach { child ->
            FileTreeRow(
                node = child,
                level = level + 1,
                isStaged = isStaged,
                contextMenuTargetPath = contextMenuTargetPath,
                onContextMenuTargetChange = onContextMenuTargetChange,
                onFileToggle = onFileToggle,
                onFilesToggle = onFilesToggle,
                onOpenFileDiff = onOpenFileDiff,
                onOpenFileHistory = onOpenFileHistory,
                onDiscardFile = onDiscardFile
            )
        }
    }
}

@Composable
private fun FileChangeActionsMenu(
    file: FileChange,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    onDiscard: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.changes_reset_file_changes)) },
        onClick = {
            onDismiss()
            onDiscard()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_view_history)) },
        onClick = {
            onDismiss()
            onOpenHistory()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_copy_file_name)) },
        onClick = {
            clipboard.setPrimaryClip(ClipData.newPlainText("file_name", file.path.substringAfterLast('/')))
            onDismiss()
            Toast.makeText(context, context.getString(R.string.commit_detail_toast_copied_file_name), Toast.LENGTH_SHORT).show()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_copy_file_path)) },
        onClick = {
            clipboard.setPrimaryClip(ClipData.newPlainText("file_path", file.path))
            onDismiss()
            Toast.makeText(context, context.getString(R.string.commit_detail_toast_copied_file_path), Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
private fun WorkingTreeDiffDialog(
    diff: FileDiff,
    onDismiss: () -> Unit
) {
    var sideBySide by rememberSaveable(diff.path) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StartEllipsizedText(
                        text = diff.path,
                        modifier = Modifier.weight(1f),
                        style = LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    IconButton(onClick = { sideBySide = false }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.ViewAgenda,
                            contentDescription = null,
                            tint = if (!sideBySide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { sideBySide = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.ViewColumn,
                            contentDescription = null,
                            tint = if (sideBySide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                Divider()
                if (sideBySide) SideBySideDiffView(diff) else UnifiedDiffView(diff)
            }
        }
    }
}

@Composable
private fun FileHistoryDialog(
    path: String,
    commits: List<Commit>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StartEllipsizedText(
                        text = path,
                        modifier = Modifier.weight(1f),
                        style = LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                Divider()
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(commits) { commit ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = commit.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${commit.hash.take(7)} • ${commit.author}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fileStatusIcon(status: ChangeStatus): androidx.compose.ui.graphics.vector.ImageVector = when (status) {
    ChangeStatus.ADDED -> Icons.Default.AddCircle
    ChangeStatus.MODIFIED -> Icons.Default.Edit
    ChangeStatus.DELETED -> Icons.Default.RemoveCircle
    ChangeStatus.RENAMED -> Icons.Default.DriveFileRenameOutline
    ChangeStatus.COPIED -> Icons.Default.FileCopy
    ChangeStatus.UNTRACKED -> Icons.Default.HelpOutline
}

@Composable
private fun fileStatusColor(status: ChangeStatus): androidx.compose.ui.graphics.Color = when (status) {
    ChangeStatus.ADDED -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    ChangeStatus.MODIFIED -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    ChangeStatus.DELETED -> androidx.compose.ui.graphics.Color(0xFFF44336)
    ChangeStatus.RENAMED -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    ChangeStatus.COPIED -> androidx.compose.ui.graphics.Color(0xFF3F51B5)
    ChangeStatus.UNTRACKED -> MaterialTheme.colorScheme.onSurfaceVariant
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
