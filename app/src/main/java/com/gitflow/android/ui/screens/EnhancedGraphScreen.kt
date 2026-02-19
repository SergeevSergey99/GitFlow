package com.gitflow.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.gitflow.android.R
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.GitRepository
import com.gitflow.android.ui.config.GraphConfig
import com.gitflow.android.ui.screens.main.EmptyStateMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Минимальный и рабочий экран графа. Исправления:
 * 1) Клики по коммиту открывают диалог деталей.
 * 2) Ветка не «появляется из пустоты»: рисуется BRANCH от родителя к первому коммиту ветки,
 *    а в строке коммита показывается бейдж «from <hash> • <branch>».
 * 3) Вся геометрия в dp→px внутри DrawScope, линии совпадают с точками на любой плотности.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedGraphView(
    repository: Repository?,
    gitRepository: GitRepository,
    config: GraphConfig = GraphConfig.Default
) {
    if (repository == null) {
        EmptyStateMessage(stringResource(R.string.graph_select_repo))
        return
    }

    val context = LocalContext.current
    var commits by remember { mutableStateOf<List<Commit>>(emptyList()) }
    var selectedCommit by remember { mutableStateOf<Commit?>(null) }
    var showCommitDetail by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var actionCommit by remember { mutableStateOf<Commit?>(null) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var commitTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagsLoading by remember { mutableStateOf(false) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var pendingDialog by remember { mutableStateOf<CommitActionDialog?>(null) }
    val currentBranchName = repository.currentBranch.ifEmpty { "HEAD" }

    // Загружаем коммиты при смене репозитория
    LaunchedEffect(repository) {
        isLoading = true
        commits = gitRepository.getCommits(repository)
        isLoading = false
    }

    LaunchedEffect(actionCommit) {
        if (actionCommit != null) {
            tagsLoading = true
            commitTags = gitRepository.getTagsForCommit(repository, actionCommit!!.hash)
            tagsLoading = false
        } else {
            commitTags = emptyList()
            tagsLoading = false
        }
    }

    fun showSnackbarMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun runRepositoryAction(
        commit: Commit,
        successMessage: String,
        clearSelection: Boolean = true,
        operation: suspend () -> Result<Unit>
    ) {
        if (isOperationRunning) return
        scope.launch {
            isOperationRunning = true
            val outcome = try {
                operation()
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (outcome.isSuccess) {
                try {
                    commits = gitRepository.getCommits(repository)
                } catch (refreshError: Exception) {
                    val fallback = context.getString(R.string.graph_commit_operation_failed_generic)
                    val message = refreshError.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
                    showSnackbarMessage(context.getString(R.string.graph_commit_operation_failed, message))
                }

                if (clearSelection) {
                    actionCommit = null
                    commitTags = emptyList()
                } else {
                    actionCommit = commit
                    commitTags = gitRepository.getTagsForCommit(repository, commit.hash)
                }
                pendingDialog = null
                showActionsSheet = false
                showSnackbarMessage(successMessage)
            } else {
                val fallback = context.getString(R.string.graph_commit_operation_failed_generic)
                val message = outcome.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
                showSnackbarMessage(context.getString(R.string.graph_commit_operation_failed, message))
            }
            isOperationRunning = false
        }
    }

    val graphData = remember(commits) { buildGraphData(commits) }

    Box(Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (commits.isEmpty()) {
            EmptyStateMessage(stringResource(R.string.graph_no_commits))
        } else {
            GraphCanvas(
                graphData = graphData,
                config = config,
                onCommitClick = { commit ->
                    selectedCommit = commit
                    showCommitDetail = true
                },
                onCommitLongPress = { commit ->
                    actionCommit = commit
                    showActionsSheet = true
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showCommitDetail && selectedCommit != null) {
        CommitDetailDialog(
            commit = selectedCommit!!,
            repository = repository,
            gitRepository = gitRepository,
            onDismiss = {
                showCommitDetail = false
                selectedCommit = null
            }
        )
    }

    if (showActionsSheet && actionCommit != null) {
        val commitForActions = actionCommit!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showActionsSheet = false
                actionCommit = null
                commitTags = emptyList()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.graph_commit_actions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = commitForActions.message.ifBlank { commitForActions.hash },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = commitForActions.hash.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (tagsLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (commitTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.graph_commit_tags_label, commitTags.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = commitTags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = stringResource(R.string.graph_commit_tags_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommitActionListItem(
                    icon = Icons.Default.Restore,
                    text = stringResource(R.string.graph_commit_action_reset, currentBranchName),
                    enabled = !isOperationRunning,
                    onClick = {
                        showActionsSheet = false
                        pendingDialog = CommitActionDialog.Reset(commitForActions)
                    }
                )

                CommitActionListItem(
                    icon = Icons.Default.Label,
                    text = stringResource(R.string.graph_commit_action_tag_add),
                    enabled = !isOperationRunning,
                    onClick = {
                        showActionsSheet = false
                        pendingDialog = CommitActionDialog.AddTag(commitForActions)
                    }
                )

                val canDeleteTag = commitTags.isNotEmpty()
                CommitActionListItem(
                    icon = Icons.Default.LabelOff,
                    text = stringResource(R.string.graph_commit_action_tag_delete),
                    enabled = canDeleteTag && !isOperationRunning,
                    onClick = {
                        if (canDeleteTag) {
                            showActionsSheet = false
                            pendingDialog = CommitActionDialog.DeleteTag(commitForActions, commitTags)
                        }
                    }
                )

                CommitActionListItem(
                    icon = Icons.Default.ContentCopy,
                    text = stringResource(R.string.graph_commit_action_cherry_pick),
                    enabled = !isOperationRunning,
                    onClick = {
                        showActionsSheet = false
                        pendingDialog = CommitActionDialog.CherryPick(commitForActions)
                    }
                )

                CommitActionListItem(
                    icon = Icons.Default.MergeType,
                    text = stringResource(R.string.graph_commit_action_merge, currentBranchName),
                    enabled = !isOperationRunning,
                    onClick = {
                        showActionsSheet = false
                        pendingDialog = CommitActionDialog.Merge(commitForActions)
                    }
                )
            }
        }
    }

    when (val dialogState = pendingDialog) {
        is CommitActionDialog.Reset -> {
            AlertDialog(
                onDismissRequest = { if (!isOperationRunning) pendingDialog = null },
                title = { Text(stringResource(R.string.graph_commit_reset_confirm_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.graph_commit_reset_confirm_message,
                            currentBranchName,
                            dialogState.commit.hash.take(7)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = {
                            runRepositoryAction(
                                commit = dialogState.commit,
                                successMessage = context.getString(
                                    R.string.graph_commit_reset_success,
                                    currentBranchName,
                                    dialogState.commit.hash.take(7)
                                )
                            ) {
                                gitRepository.hardResetToCommit(repository, dialogState.commit.hash)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.graph_commit_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = { pendingDialog = null }
                    ) {
                        Text(stringResource(R.string.graph_commit_cancel))
                    }
                }
            )
        }

        is CommitActionDialog.AddTag -> {
            var tagName by remember(dialogState) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!isOperationRunning) pendingDialog = null },
                title = { Text(stringResource(R.string.graph_commit_add_tag_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = tagName,
                            onValueChange = { tagName = it },
                            label = { Text(stringResource(R.string.graph_commit_add_tag_hint)) },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.graph_commit_add_tag_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = tagName.isNotBlank() && !isOperationRunning,
                        onClick = {
                            val trimmed = tagName.trim()
                            runRepositoryAction(
                                commit = dialogState.commit,
                                successMessage = context.getString(R.string.graph_commit_add_tag_success, trimmed),
                                clearSelection = false
                            ) {
                                gitRepository.createTag(repository, trimmed, dialogState.commit.hash)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.graph_commit_add_tag_button))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = { pendingDialog = null }
                    ) {
                        Text(stringResource(R.string.graph_commit_cancel))
                    }
                }
            )
        }

        is CommitActionDialog.DeleteTag -> {
            val tags = dialogState.availableTags
            var selectedTag by remember(dialogState) {
                mutableStateOf(tags.firstOrNull().orEmpty())
            }
            AlertDialog(
                onDismissRequest = { if (!isOperationRunning) pendingDialog = null },
                title = { Text(stringResource(R.string.graph_commit_delete_tag_title)) },
                text = {
                    if (tags.isEmpty()) {
                        Text(stringResource(R.string.graph_commit_delete_tag_placeholder))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedTag = tag }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedTag == tag,
                                        onClick = { selectedTag = tag }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tag,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedTag.isNotBlank() && !isOperationRunning,
                        onClick = {
                            val tagToDelete = selectedTag
                            runRepositoryAction(
                                commit = dialogState.commit,
                                successMessage = context.getString(R.string.graph_commit_delete_tag_success, tagToDelete),
                                clearSelection = false
                            ) {
                                gitRepository.deleteTag(repository, tagToDelete)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.graph_commit_delete_tag_button))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = { pendingDialog = null }
                    ) {
                        Text(stringResource(R.string.graph_commit_cancel))
                    }
                }
            )
        }

        is CommitActionDialog.CherryPick -> {
            AlertDialog(
                onDismissRequest = { if (!isOperationRunning) pendingDialog = null },
                title = { Text(stringResource(R.string.graph_commit_cherry_pick_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.graph_commit_cherry_pick_message,
                            dialogState.commit.hash.take(7)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = {
                            runRepositoryAction(
                                commit = dialogState.commit,
                                successMessage = context.getString(R.string.graph_commit_cherry_pick_success)
                            ) {
                                gitRepository.cherryPickCommit(repository, dialogState.commit.hash)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.graph_commit_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = { pendingDialog = null }
                    ) {
                        Text(stringResource(R.string.graph_commit_cancel))
                    }
                }
            )
        }

        is CommitActionDialog.Merge -> {
            AlertDialog(
                onDismissRequest = { if (!isOperationRunning) pendingDialog = null },
                title = { Text(stringResource(R.string.graph_commit_merge_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.graph_commit_merge_message,
                            dialogState.commit.hash.take(7),
                            currentBranchName
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = {
                            runRepositoryAction(
                                commit = dialogState.commit,
                                successMessage = context.getString(R.string.graph_commit_merge_success)
                            ) {
                                gitRepository.mergeCommitIntoCurrentBranch(repository, dialogState.commit.hash)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.graph_commit_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isOperationRunning,
                        onClick = { pendingDialog = null }
                    ) {
                        Text(stringResource(R.string.graph_commit_cancel))
                    }
                }
            )
        }

        null -> Unit
    }
}

/* ============================ Canvas / Rows ============================ */

@Composable
private fun GraphCanvas(
    graphData: GraphData,
    config: GraphConfig,
    onCommitClick: (Commit) -> Unit,
    onCommitLongPress: (Commit) -> Unit
) {
    val horizontalScrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
    ) {
        itemsIndexed(graphData.commits, key = { _, c -> c.hash }) { _, commit ->
            val node = graphData.nodePositions.getValue(commit.hash)
            val conns = graphData.connections[commit.hash].orEmpty()
            val forkInfo = graphData.forkFrom[commit.hash]

            GraphCommitRow(
                commit = commit,
                nodeData = node,
                connections = conns,
                forkInfo = forkInfo,
                maxLanes = graphData.maxLane,
                config = config,
                horizontalScrollState = horizontalScrollState,
                onClick = { onCommitClick(commit) },
                onLongPress = { onCommitLongPress(commit) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CommitActionListItem(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onClick() }
    )
}

private sealed class CommitActionDialog {
    data class Reset(val commit: Commit) : CommitActionDialog()
    data class AddTag(val commit: Commit) : CommitActionDialog()
    data class DeleteTag(val commit: Commit, val availableTags: List<String>) : CommitActionDialog()
    data class CherryPick(val commit: Commit) : CommitActionDialog()
    data class Merge(val commit: Commit) : CommitActionDialog()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphCommitRow(
    commit: Commit,
    nodeData: NodePosition,
    connections: List<Connection>,
    forkInfo: ForkInfo?,
    maxLanes: Int,
    config: GraphConfig,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val nodeColor = laneColor(nodeData.lane)

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .height(config.rowHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = config.rowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая колонка с графом
        Box(
            modifier = Modifier
                .width(config.getGraphWidth(maxLanes))
                .fillMaxHeight()
                .drawBehind {
                    // Все соединения для текущей строки
                    connections.forEach { connection ->
                        drawConnection(
                            connection = connection,
                            config = config,
                            colorFromLane = laneColor(connection.fromLane)
                        )
                    }
                }
        ) {
            // Узел
            Box(
                modifier = Modifier
                    .offset(x = (nodeData.lane * config.laneStep + config.nodeCenterOffset))
                    .align(Alignment.CenterStart)
            ) {
                // Основная точка с учетом типа коммита
                if (commit.isMergeCommit) {
                    // Квадратный узел для merge коммитов
                    Box(
                        modifier = Modifier
                            .size(config.nodeSize)
                            .offset(x = -(config.nodeSize / 2), y = 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(nodeColor)
                            .border(config.nodeBorderWidth, Color.White, RoundedCornerShape(2.dp))
                    )
                } else {
                    // Круглый узел для обычных коммитов
                    Box(
                        modifier = Modifier
                            .size(config.nodeSize)
                            .offset(x = -(config.nodeSize / 2), y = 0.dp)
                            .clip(CircleShape)
                            .background(nodeColor)
                            .border(config.nodeBorderWidth, Color.White, CircleShape)
                    )
                }
            }
        }

        // Правая информационная часть с возможностью прокрутки
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(start = config.infoStartPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Иконка merge коммита
                if (commit.isMergeCommit) {
                    androidx.compose.material3.Icon(
                        Icons.Default.MergeType,
                        contentDescription = stringResource(R.string.graph_merge_commit),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                
                Text(
                    text = commit.message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false
                )
            }

            Spacer(Modifier.height(config.textSpacing))

            Row(
                horizontalArrangement = Arrangement.spacedBy(config.badgeSpacing)
            ) {
                // hash
                /*Surface(
                    shape = RoundedCornerShape(config.badgeCornerRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        commit.hash.take(7),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(
                            horizontal = config.badgeHorizontalPadding,
                            vertical = config.badgeVerticalPadding
                        )
                    )
                }*/

                // время
                Surface(
                    shape = RoundedCornerShape(config.badgeCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = config.badgeHorizontalPadding,
                            vertical = config.badgeVerticalPadding
                        )
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(config.badgeIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(config.badgeIconSpacing))
                        Text(timeAgo(commit.timestamp), fontSize = 11.sp)
                    }
                }

                // автор
                Surface(
                    shape = RoundedCornerShape(config.badgeCornerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = config.badgeHorizontalPadding,
                            vertical = config.badgeVerticalPadding
                        )
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(config.badgeIconSize),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(config.badgeIconSpacing))
                        Text(
                            text = commit.author,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Показываем только ветки, где коммит является HEAD
                commit.branchHeads.forEach { branchHead ->
                    val branchColor = getBranchColor(branchHead)
                    Badge(
                        text = branchHead,
                        icon = Icons.Default.AccountTree,
                        background = branchColor.copy(alpha = 0.15f),
                        foreground = branchColor,
                        config = config
                    )
                }

                // бейджи тегов
                commit.tags.forEach { t ->
                    Badge(
                        text = t,
                        icon = Icons.Default.LocalOffer,
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                        foreground = MaterialTheme.colorScheme.onTertiaryContainer,
                        config = config
                    )
                }

                // «из какой ветки/коммита» если это старт новой ветки
                if (forkInfo != null) {
                    Badge(
                        text = if (forkInfo.parentBranch != null) {
                            stringResource(R.string.graph_from_badge_branch, forkInfo.parentHash.take(7), forkInfo.parentBranch)
                        } else {
                            stringResource(R.string.graph_from_badge, forkInfo.parentHash.take(7))
                        },
                        icon = Icons.Default.AccountTree,
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                        config = config
                    )
                }
            }
        }
    }
}

/* ============================ Drawing ============================ */

private fun DrawScope.drawConnection(
    connection: Connection,
    config: GraphConfig,
    colorFromLane: Color
) {
    val stepPx = config.laneStep.toPx()
    val centerPx = config.nodeCenterOffset.toPx()
    val strokePx = config.lineStrokeWidth.toPx()

    val x1 = connection.fromLane * stepPx + centerPx
    val x2 = connection.toLane * stepPx + centerPx
    val yMid = size.height / 2f

    when (connection.type) {
        ConnectionType.STRAIGHT -> {
            drawLine(
                color = laneColor(connection.fromLane),
                start = Offset(x1, 0f),
                end = Offset(x1, size.height),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
        ConnectionType.MERGE -> {
            val path = Path().apply {
                moveTo(x1, yMid)
                cubicTo(
                    x1, yMid + (size.height - yMid) * 0.3f,
                    x2, yMid + (size.height - yMid) * 0.7f,
                    x2, size.height
                )
            }
            drawPath(path, colorFromLane, style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        ConnectionType.BRANCH -> {
            val path = Path().apply {
                moveTo(x1, 0f)
                val dy = yMid
                val dx = kotlin.math.abs(x2 - x1)
                cubicTo(
                    x1, dy * 0.4f,
                    x2, dy - dx * 0.2f,
                    x2, yMid
                )
            }
            drawPath(path, laneColor(connection.fromLane), style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        ConnectionType.VERTICAL_FROM_NODE -> {
            // Нижняя половина вертикали (от центра узла вниз), чтобы линия «шла» к родителю ниже
            drawLine(
                color = laneColor(connection.fromLane),
                start = Offset(x1, yMid),
                end = Offset(x1, size.height),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }
}

/* ============================ Graph model ============================ */

private fun laneColor(index: Int): Color {
    val palette = listOf(
        Color(0xFF4F46E5), // indigo
        Color(0xFF059669), // emerald
        Color(0xFFE11D48), // rose
        Color(0xFFF59E0B), // amber
        Color(0xFF06B6D4), // cyan
        Color(0xFF8B5CF6), // violet
        Color(0xFF10B981), // green
        Color(0xFFEF4444)  // red
    )
    return palette[index % palette.size]
}

private data class GraphData(
    val commits: List<Commit>,
    val nodePositions: Map<String, NodePosition>,
    val connections: Map<String, List<Connection>>,
    val forkFrom: Map<String, ForkInfo>,
    val maxLane: Int
)

private data class NodePosition(
    val lane: Int,
    val row: Int
)

private data class Connection(
    val fromLane: Int,
    val toLane: Int,
    val type: ConnectionType
)

private enum class ConnectionType { STRAIGHT, MERGE, BRANCH, VERTICAL_FROM_NODE }

private data class ForkInfo(
    val parentHash: String,
    val parentLane: Int,
    val parentBranch: String?
)
private fun buildGraphData(commits: List<Commit>): GraphData {
    if (commits.isEmpty()) return GraphData(emptyList(), emptyMap(), emptyMap(), emptyMap(), 0)

    val byHash = commits.associateBy { it.hash }
    val nodePositions = mutableMapOf<String, NodePosition>()
    val connections = mutableMapOf<String, MutableList<Connection>>()
    val forkFrom = mutableMapOf<String, ForkInfo>()

    val activeLanes = mutableListOf<String?>()
    activeLanes.add(commits.first().hash)
    var maxLanes = 1

    commits.forEachIndexed { row, commit ->
        val conns = mutableListOf<Connection>()

        val waitingIdx = activeLanes.withIndex().filter { it.value == commit.hash }.map { it.index }
        var lane = waitingIdx.minOrNull() ?: -1
        if (waitingIdx.size > 1) {
            waitingIdx.sorted().drop(1).forEach { extra ->
                conns.add(Connection(fromLane = extra, toLane = lane, type = ConnectionType.BRANCH))
                activeLanes[extra] = null
            }
        }

        val lanesBefore = activeLanes.toList()
        lanesBefore.indices.forEach { i ->
            if (lanesBefore[i] != null) {
                conns.add(Connection(fromLane = i, toLane = i, type = ConnectionType.STRAIGHT))
            }
        }

        val wasExpected = lane != -1
        if (lane == -1) {
            lane = activeLanes.indexOf(null).takeIf { it != -1 } ?: activeLanes.size.also { activeLanes.add(null) }
        }

        val mainParent = commit.parents.firstOrNull()

        if (!wasExpected && mainParent != null && byHash.containsKey(mainParent)) {
            var parentLane = lanesBefore.indexOf(mainParent)
            if (parentLane == -1) {
                parentLane = activeLanes.indexOf(mainParent)
                if (parentLane == -1) {
                    parentLane = activeLanes.indexOf(null).takeIf { it != -1 }
                        ?: activeLanes.size.also { activeLanes.add(null) }
                    activeLanes[parentLane] = mainParent
                }
            }
            if (parentLane != lane) {
                // Настоящее ответвление: кривая + нижняя половина вертикали
                conns.add(Connection(fromLane = parentLane, toLane = lane, type = ConnectionType.BRANCH))
                forkFrom[commit.hash] = ForkInfo(
                    parentHash = mainParent,
                    parentLane = parentLane,
                    parentBranch = byHash[mainParent]?.branch
                )
            }
            // Добавляем нижнюю половину вертикали, чтобы линия шла от узла к родителю ниже
            conns.add(Connection(fromLane = lane, toLane = lane, type = ConnectionType.VERTICAL_FROM_NODE))
        }

        activeLanes[lane] = if (mainParent != null && byHash.containsKey(mainParent)) mainParent else null

        commit.parents.drop(1).forEach { p ->
            if (byHash.containsKey(p)) {
                var pLane = lanesBefore.indexOf(p)
                if (pLane == -1) {
                    pLane = activeLanes.indexOf(p)
                    if (pLane == -1) {
                        pLane = activeLanes.indexOf(null).takeIf { it != -1 }
                            ?: activeLanes.size.also { activeLanes.add(null) }
                    }
                }
                conns.add(Connection(fromLane = lane, toLane = pLane, type = ConnectionType.MERGE))
                activeLanes[pLane] = p
            }
        }

        while (activeLanes.isNotEmpty() && activeLanes.last() == null) activeLanes.removeLast()

        nodePositions[commit.hash] = NodePosition(lane = lane, row = row)
        connections[commit.hash] = conns
        if (activeLanes.size > maxLanes) maxLanes = activeLanes.size
    }

    return GraphData(
        commits = commits,
        nodePositions = nodePositions,
        connections = connections,
        forkFrom = forkFrom,
        maxLane = maxLanes
    )
}


/* ============================ UI utils ============================ */

@Composable
private fun Badge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
    config: GraphConfig
) {
    Surface(
        shape = RoundedCornerShape(config.badgeCornerRadius),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = config.badgeHorizontalPadding,
                vertical = config.badgeVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                icon,
                null,
                Modifier.size(config.badgeIconSize),
                tint = foreground
            )
            Spacer(Modifier.width(config.badgeIconSpacing))
            Text(
                text,
                fontSize = 11.sp,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ============================ Branch Colors ============================ */

private fun getBranchColor(branchName: String): Color {
    // Генерируем стабильный контрастный цвет для каждой ветки
    val colors = listOf(
        Color(0xFF1B5E20), // Dark Green
        Color(0xFF0D47A1), // Dark Blue  
        Color(0xFFE65100), // Dark Orange
        Color(0xFF4A148C), // Dark Purple
        Color(0xFFAD1457), // Dark Pink
        Color(0xFF006064), // Dark Cyan
        Color(0xFFBF360C), // Deep Orange Red
        Color(0xFF3E2723), // Dark Brown
        Color(0xFF263238), // Dark Blue Grey
        Color(0xFF1A237E), // Dark Indigo
        Color(0xFF827717), // Dark Olive
        Color(0xFF616161)  // Dark Grey
    )
    
    // Выбираем цвет на основе хеша имени ветки
    val index = branchName.hashCode().let { if (it < 0) -it else it } % colors.size
    return colors[index]
}

fun timeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000L}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
