package com.gitflow.android.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import timber.log.Timber
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitflow.android.R
import com.gitflow.android.ui.components.StartEllipsizedText
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.IGitRepository
import com.gitflow.android.data.settings.AppSettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CodeLineHorizontalPadding = 16.dp
private val CodeLineNumberWidth = 48.dp
private val CodeLineContentSpacing = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailDialog(
    commit: Commit,
    repository: Repository?,
    gitRepository: IGitRepository,
    onDismiss: () -> Unit
) {
    val viewModel: CommitDetailViewModel = viewModel(
        key = commit.hash,
        factory = CommitDetailViewModelFactory(gitRepository, commit, repository)
    )
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager(context) }
    var previewExtensions by remember { mutableStateOf(settingsManager.getPreviewExtensions()) }
    var previewFileNames by remember { mutableStateOf(settingsManager.getPreviewFileNames()) }

    DisposableEffect(settingsManager) {
        val listener = settingsManager.registerPreviewSettingsListener {
            previewExtensions = settingsManager.getPreviewExtensions()
            previewFileNames = settingsManager.getPreviewFileNames()
        }
        onDispose {
            settingsManager.unregisterPreviewSettingsListener(listener)
        }
    }

    val normalizedPreviewExtensions = remember(previewExtensions) {
        previewExtensions.mapNotNull { normalizeExtensionToken(it) }.toSet()
    }

    val normalizedPreviewFileNames = remember(previewFileNames) {
        previewFileNames.mapNotNull { normalizeFileNameToken(it) }.toSet()
    }

    var showChangedFilesTree by remember { mutableStateOf(settingsManager.isCommitFilesTreeViewEnabled()) }
    var pendingRestoreConfirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun requestRestoreToCommit(fileName: String, filePath: String) {
        pendingRestoreConfirm = Pair(fileName) {
            viewModel.restoreFileToCommit(filePath) { success ->
                Toast.makeText(
                    context,
                    if (success) context.getString(R.string.commit_detail_toast_restored_commit) else context.getString(R.string.commit_detail_toast_restore_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun requestRestoreToParent(fileName: String, filePath: String) {
        if (commit.parents.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.commit_detail_toast_no_parent),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        pendingRestoreConfirm = Pair(fileName) {
            viewModel.restoreFileToParentCommit(filePath) { success ->
                Toast.makeText(
                    context,
                    if (success) context.getString(R.string.commit_detail_toast_restored_parent) else context.getString(R.string.commit_detail_toast_restore_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun requestViewHistory(fileName: String, filePath: String) {
        viewModel.loadFileHistory(
            FileTreeNode(
                name = fileName,
                path = filePath,
                type = FileTreeNodeType.FILE
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = commit.message,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Stats
                            if (uiState.isLoadingDiffs) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatChip(
                                        icon = Icons.Default.Add,
                                        value = "+${uiState.fileDiffs.sumOf { it.additions }}",
                                        color = Color(0xFF4CAF50)
                                    )
                                    StatChip(
                                        icon = Icons.Default.Remove,
                                        value = "-${uiState.fileDiffs.sumOf { it.deletions }}",
                                        color = Color(0xFFF44336)
                                    )
                                    StatChip(
                                        icon = Icons.Default.Description,
                                        value = stringResource(R.string.commit_detail_files_count, uiState.fileDiffs.size),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.commit_detail_close))
                        }
                    }
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = {
                            Text(
                                text = stringResource(R.string.commit_detail_tab_files),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            Text(
                                text = stringResource(R.string.commit_detail_tab_changes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        text = {
                            Text(
                                text = stringResource(R.string.commit_detail_tab_info),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        text = {
                            Text(
                                text = stringResource(R.string.commit_detail_tab_file_tree),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }

                // Tab content
                when (uiState.selectedTab) {
                    0 -> when {
                        uiState.isLoadingDiffs -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.diffsLoadError != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = uiState.diffsLoadError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            ChangedFilesView(
                                fileDiffs = uiState.fileDiffs,
                                selectedFile = uiState.selectedFile,
                                showTree = showChangedFilesTree,
                                hasParentCommit = commit.parents.isNotEmpty(),
                                contextMenuTargetPath = uiState.contextMenuTargetPath,
                                restoreInProgress = uiState.isRestoringFile,
                                allowedExtensions = normalizedPreviewExtensions,
                                allowedFileNames = normalizedPreviewFileNames,
                                onToggleViewMode = {
                                    val next = !showChangedFilesTree
                                    showChangedFilesTree = next
                                    settingsManager.setCommitFilesTreeViewEnabled(next)
                                },
                                onContextMenuTargetChange = { viewModel.setContextMenuTarget(it) },
                                onRestoreFromCommit = { diff ->
                                    viewModel.setContextMenuTarget(null)
                                    requestRestoreToCommit(diff.path.substringAfterLast('/'), diff.path)
                                },
                                onRestoreFromParent = { diff ->
                                    viewModel.setContextMenuTarget(null)
                                    requestRestoreToParent(diff.path.substringAfterLast('/'), diff.path)
                                },
                                onViewHistory = { diff ->
                                    viewModel.setContextMenuTarget(null)
                                    requestViewHistory(diff.path.substringAfterLast('/'), diff.path)
                                }
                            ) { file ->
                                viewModel.selectFile(file)
                                viewModel.selectTab(1)
                            }
                        }
                    }
                    1 -> when {
                        uiState.isLoadingDiffs -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.diffsLoadError != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = uiState.diffsLoadError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            DiffView(
                                selectedFile = uiState.selectedFile,
                                allowedExtensions = normalizedPreviewExtensions,
                                allowedFileNames = normalizedPreviewFileNames
                            )
                        }
                    }
                    2 -> CommitInfoView(commit)
                    3 -> FileTreeView(
                        commit = commit,
                        repository = repository,
                        gitRepository = gitRepository,
                        viewModel = viewModel,
                        onRequestRestoreToCommit = ::requestRestoreToCommit,
                        onRequestRestoreToParent = ::requestRestoreToParent,
                        onRequestViewHistory = ::requestViewHistory
                    )
                }
            }
        }
    }

    pendingRestoreConfirm?.let { (fileName, action) ->
        AlertDialog(
            onDismissRequest = { pendingRestoreConfirm = null },
            title = { Text(stringResource(R.string.commit_detail_restore_confirm_title)) },
            text = {
                Text(stringResource(R.string.commit_detail_restore_confirm_message, fileName))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreConfirm = null
                    action()
                }) {
                    Text(stringResource(R.string.commit_detail_restore_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreConfirm = null }) {
                    Text(stringResource(R.string.commit_detail_restore_confirm_cancel))
                }
            }
        )
    }

    uiState.historyDialogFile?.let { fileNode ->
        FileHistoryDialog(
            file = fileNode,
            commit = commit,
            history = uiState.fileHistory,
            isLoading = uiState.isHistoryLoading,
            error = uiState.historyError,
            isDiffLoading = uiState.isHistoryDiffLoading,
            selectedHistoryCommit = uiState.historyDiffCommit,
            onDismiss = { viewModel.dismissHistory() },
            onSelectCommit = { historyCommit ->
                viewModel.loadHistoryDiff(historyCommit)
            }
        )
    }

    uiState.historyDiffCommit?.let { historyCommit ->
        HistoryFileDiffDialog(
            commit = historyCommit,
            filePath = uiState.historyDialogFile?.path ?: uiState.historyDialogFile?.name ?: "",
            fileDiff = uiState.historyDiff,
            isLoading = uiState.isHistoryDiffLoading,
            error = uiState.historyDiffError,
            allowedExtensions = normalizedPreviewExtensions,
            allowedFileNames = normalizedPreviewFileNames,
            onDismiss = { viewModel.dismissHistoryDiff() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffView(
    selectedFile: FileDiff?,
    allowedExtensions: Set<String>,
    allowedFileNames: Set<String>
) {
    var showSideBySide by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (selectedFile == null) {
        // Пустое состояние когда файл не выбран
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.commit_detail_select_file),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Проверяем, поддерживается ли формат файла
    val isPreviewSupported = isPreviewSupported(
        fileName = selectedFile.path.substringAfterLast('/'),
        allowedExtensions = allowedExtensions,
        allowedFileNames = allowedFileNames
    )

    if (!isPreviewSupported) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.commit_detail_preview_not_supported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.commit_detail_preview_settings_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // View mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StartEllipsizedText(
                text = selectedFile.path,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showSideBySide = false },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ViewAgenda,
                        contentDescription = stringResource(R.string.commit_detail_unified_view),
                        tint = if (!showSideBySide) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showSideBySide = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ViewColumn,
                        contentDescription = stringResource(R.string.commit_detail_side_by_side),
                        tint = if (showSideBySide) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Divider()

        // Diff content
        if (showSideBySide) {
            SideBySideDiffView(selectedFile)
        } else {
            UnifiedDiffView(selectedFile)
        }
    }
}

@Composable
fun UnifiedDiffView(diff: FileDiff) {
    val horizontalScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
        ) {
            LazyColumn {
                diff.hunks.forEach { hunk ->
                    item {
                        // Hunk header
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = hunk.header,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    items(hunk.lines) { line ->
                        DiffLineView(line)
                    }
                }
            }
        }
    }
}

@Composable
fun SideBySideDiffView(diff: FileDiff) {
    val horizontalScrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Old version
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF44336).copy(alpha = 0.1f)
            ) {
                Text(
                    text = stringResource(R.string.commit_detail_old),
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn {
                    diff.hunks.forEach { hunk ->
                        items(hunk.lines.filter { it.type != LineType.ADDED }) { line ->
                            DiffLineView(line, compact = true)
                        }
                    }
                }
            }
        }

        VerticalDivider()

        // New version
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50).copy(alpha = 0.1f)
            ) {
                Text(
                    text = stringResource(R.string.commit_detail_new),
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn {
                    diff.hunks.forEach { hunk ->
                        items(hunk.lines.filter { it.type != LineType.DELETED }) { line ->
                            DiffLineView(line, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiffLineView(line: DiffLine, compact: Boolean = false) {
    val backgroundColor = when (line.type) {
        LineType.ADDED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        LineType.DELETED -> Color(0xFFF44336).copy(alpha = 0.1f)
        LineType.CONTEXT -> Color.Transparent
    }

    val linePrefix = when (line.type) {
        LineType.ADDED -> "+"
        LineType.DELETED -> "-"
        LineType.CONTEXT -> " "
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 2.dp)
        ) {
            if (!compact) {
                val lineNumberText = when (line.type) {
                    LineType.ADDED -> line.newLineNumber?.toString() ?: ""
                    LineType.DELETED -> line.oldLineNumber?.toString() ?: ""
                    LineType.CONTEXT -> line.oldLineNumber?.toString() ?: ""
                }
                Text(
                    text = lineNumberText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp)
                )
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = when (line.type) {
                                LineType.ADDED -> Color(0xFF4CAF50)
                                LineType.DELETED -> Color(0xFFF44336)
                                LineType.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        append(linePrefix)
                    }
                    append(" ")
                    append(line.content)
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ChangedFilesView(
    fileDiffs: List<FileDiff>,
    selectedFile: FileDiff?,
    showTree: Boolean,
    hasParentCommit: Boolean,
    contextMenuTargetPath: String?,
    restoreInProgress: Boolean,
    allowedExtensions: Set<String>,
    allowedFileNames: Set<String>,
    onToggleViewMode: () -> Unit,
    onContextMenuTargetChange: (String?) -> Unit,
    onRestoreFromCommit: (FileDiff) -> Unit,
    onRestoreFromParent: (FileDiff) -> Unit,
    onViewHistory: (FileDiff) -> Unit,
    onFileSelected: (FileDiff) -> Unit
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 28.dp.toPx() }
    var revealOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDraggingPeek by remember { mutableStateOf(false) }
    val animatedRevealOffsetPx by animateFloatAsState(
        targetValue = revealOffsetPx,
        animationSpec = if (isDraggingPeek) snap() else tween(durationMillis = 220),
        label = "changed_files_peek_return"
    )

    val listState = rememberLazyListState()
    val treeState = rememberLazyListState()
    val canPullDown = if (showTree) {
        treeState.firstVisibleItemIndex == 0 && treeState.firstVisibleItemScrollOffset == 0
    } else {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }

    LaunchedEffect(showTree) {
        isDraggingPeek = false
        revealOffsetPx = 0f
    }

    val peekNestedScroll = remember(showTree, canPullDown) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero

                val dy = available.y
                if (dy > 0f && canPullDown) {
                    isDraggingPeek = true
                    val prev = revealOffsetPx
                    revealOffsetPx = (prev + dy * 0.6f).coerceIn(0f, maxRevealPx)
                    return Offset(0f, revealOffsetPx - prev)
                }

                if (dy < 0f && revealOffsetPx > 0f) {
                    isDraggingPeek = true
                    val prev = revealOffsetPx
                    revealOffsetPx = (prev + dy).coerceAtLeast(0f)
                    return Offset(0f, revealOffsetPx - prev)
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                if (available.y < 0f && revealOffsetPx > 0f) {
                    isDraggingPeek = true
                    val prev = revealOffsetPx
                    revealOffsetPx = (prev + available.y).coerceAtLeast(0f)
                    return Offset(0f, revealOffsetPx - prev)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isDraggingPeek = false
                revealOffsetPx = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(peekNestedScroll)
                .graphicsLayer { translationY = animatedRevealOffsetPx }
        ) {
            if (showTree) {
                ChangedFilesTreeView(
                    fileDiffs = fileDiffs,
                    hasParentCommit = hasParentCommit,
                    contextMenuTargetPath = contextMenuTargetPath,
                    restoreInProgress = restoreInProgress,
                    onContextMenuTargetChange = onContextMenuTargetChange,
                    onRestoreFromCommit = onRestoreFromCommit,
                    onRestoreFromParent = onRestoreFromParent,
                    onViewHistory = onViewHistory,
                    onFileSelected = onFileSelected,
                    listState = treeState
                )
            } else {
                FileListView(
                    fileDiffs = fileDiffs,
                    selectedFile = selectedFile,
                    hasParentCommit = hasParentCommit,
                    contextMenuTargetPath = contextMenuTargetPath,
                    restoreInProgress = restoreInProgress,
                    allowedExtensions = allowedExtensions,
                    allowedFileNames = allowedFileNames,
                    onContextMenuTargetChange = onContextMenuTargetChange,
                    onRestoreFromCommit = onRestoreFromCommit,
                    onRestoreFromParent = onRestoreFromParent,
                    onViewHistory = onViewHistory,
                    onFileSelected = onFileSelected,
                    listState = listState
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (showTree) Icons.Default.ViewList else Icons.Default.AccountTree,
                    contentDescription = if (showTree) stringResource(R.string.commit_detail_tab_files) else stringResource(R.string.commit_detail_tab_file_tree),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FileListView(
    fileDiffs: List<FileDiff>,
    selectedFile: FileDiff?,
    hasParentCommit: Boolean,
    contextMenuTargetPath: String?,
    restoreInProgress: Boolean,
    allowedExtensions: Set<String>,
    allowedFileNames: Set<String>,
    onContextMenuTargetChange: (String?) -> Unit,
    onRestoreFromCommit: (FileDiff) -> Unit,
    onRestoreFromParent: (FileDiff) -> Unit,
    onViewHistory: (FileDiff) -> Unit,
    onFileSelected: (FileDiff) -> Unit,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(fileDiffs) { diff ->
            val fileName = diff.path.substringAfterLast('/')
            val isPreviewSupported = isPreviewSupported(
                fileName = fileName,
                allowedExtensions = allowedExtensions,
                allowedFileNames = allowedFileNames
            )

            FileItem(
                diff = diff,
                isSelected = selectedFile == diff,
                isPreviewSupported = isPreviewSupported,
                hasParentCommit = hasParentCommit,
                contextMenuTargetPath = contextMenuTargetPath,
                restoreInProgress = restoreInProgress,
                onContextMenuTargetChange = onContextMenuTargetChange,
                onRestoreFromCommit = onRestoreFromCommit,
                onRestoreFromParent = onRestoreFromParent,
                onViewHistory = onViewHistory,
                onClick = { onFileSelected(diff) }
            )
        }
    }
}

@Composable
fun ChangedFilesTreeView(
    fileDiffs: List<FileDiff>,
    hasParentCommit: Boolean,
    contextMenuTargetPath: String?,
    restoreInProgress: Boolean,
    onContextMenuTargetChange: (String?) -> Unit,
    onRestoreFromCommit: (FileDiff) -> Unit,
    onRestoreFromParent: (FileDiff) -> Unit,
    onViewHistory: (FileDiff) -> Unit,
    onFileSelected: (FileDiff) -> Unit,
    listState: LazyListState
) {
    val changedTree = remember(fileDiffs) {
        collapseSingleChildDirectories(buildChangedFilesTree(fileDiffs), isRoot = true)
    }
    val diffByPath = remember(fileDiffs) { fileDiffs.associateBy { it.path } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(changedTree.children) { node ->
            FileTreeNodeItem(
                node = node,
                level = 0,
                isFiltering = false,
                hasParentCommit = hasParentCommit,
                contextMenuTargetPath = contextMenuTargetPath,
                restoreInProgress = restoreInProgress,
                onFileClicked = { fileNode ->
                    onContextMenuTargetChange(null)
                    diffByPath[fileNode.path]?.let(onFileSelected)
                },
                onFileLongPress = { fileNode ->
                    if (!restoreInProgress) {
                        onContextMenuTargetChange(fileNode.path)
                    }
                },
                onDismissContextMenu = { onContextMenuTargetChange(null) },
                onRestoreFromCommit = { fileNode ->
                    diffByPath[fileNode.path]?.let(onRestoreFromCommit)
                },
                onRestoreFromParent = { fileNode ->
                    diffByPath[fileNode.path]?.let(onRestoreFromParent)
                },
                onViewHistory = { fileNode ->
                    diffByPath[fileNode.path]?.let(onViewHistory)
                },
                fileTrailingIcon = { fileNode ->
                    val status = diffByPath[fileNode.path]?.status
                    if (status != null) {
                        Icon(
                            imageVector = fileStatusIcon(status),
                            contentDescription = status.toLocalizedString(),
                            modifier = Modifier.size(16.dp),
                            tint = fileStatusColor(status)
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    diff: FileDiff,
    isSelected: Boolean,
    isPreviewSupported: Boolean,
    hasParentCommit: Boolean,
    contextMenuTargetPath: String?,
    restoreInProgress: Boolean,
    onContextMenuTargetChange: (String?) -> Unit,
    onRestoreFromCommit: (FileDiff) -> Unit,
    onRestoreFromParent: (FileDiff) -> Unit,
    onViewHistory: (FileDiff) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    onContextMenuTargetChange(null)
                    onClick()
                },
                onLongClick = {
                    if (!restoreInProgress) {
                        onContextMenuTargetChange(diff.path)
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (diff.status) {
                        FileStatus.ADDED -> Icons.Default.AddCircle
                        FileStatus.MODIFIED -> Icons.Default.Edit
                        FileStatus.DELETED -> Icons.Default.RemoveCircle
                        FileStatus.RENAMED -> Icons.Default.DriveFileRenameOutline
                    },
                    contentDescription = null,
                    tint = when (diff.status) {
                        FileStatus.ADDED -> Color(0xFF4CAF50)
                        FileStatus.MODIFIED -> Color(0xFFFF9800)
                        FileStatus.DELETED -> Color(0xFFF44336)
                        FileStatus.RENAMED -> Color(0xFF2196F3)
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = diff.path,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!isPreviewSupported) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.commit_detail_preview_not_available),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = diff.status.toLocalizedString(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (diff.oldPath != null && diff.oldPath != diff.path) {
                            Text(
                                text = stringResource(R.string.commit_detail_from, diff.oldPath),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "+${diff.additions}",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "-${diff.deletions}",
                        fontSize = 12.sp,
                        color = Color(0xFFF44336)
                    )
                }
            }

            DropdownMenu(
                expanded = contextMenuTargetPath == diff.path,
                onDismissRequest = { onContextMenuTargetChange(null) }
            ) {
                FileActionsDropdownContent(
                    fileName = diff.path.substringAfterLast('/'),
                    filePath = diff.path,
                    hasParentCommit = hasParentCommit,
                    restoreInProgress = restoreInProgress,
                    onDismissMenu = { onContextMenuTargetChange(null) },
                    onRestoreFromCommit = { onRestoreFromCommit(diff) },
                    onRestoreFromParent = { onRestoreFromParent(diff) },
                    onViewHistory = { onViewHistory(diff) }
                )
            }
        }
    }
}

@Composable
private fun FileActionsDropdownContent(
    fileName: String,
    filePath: String,
    hasParentCommit: Boolean,
    restoreInProgress: Boolean,
    onDismissMenu: () -> Unit,
    onRestoreFromCommit: () -> Unit,
    onRestoreFromParent: () -> Unit,
    onViewHistory: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_restore_commit)) },
        enabled = !restoreInProgress,
        onClick = {
            onDismissMenu()
            onRestoreFromCommit()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_restore_parent)) },
        enabled = hasParentCommit && !restoreInProgress,
        onClick = {
            onDismissMenu()
            onRestoreFromParent()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_view_history)) },
        onClick = {
            onDismissMenu()
            onViewHistory()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_copy_file_name)) },
        onClick = {
            clipboard.setPrimaryClip(ClipData.newPlainText("file_name", fileName))
            onDismissMenu()
            Toast.makeText(
                context,
                context.getString(R.string.commit_detail_toast_copied_file_name),
                Toast.LENGTH_SHORT
            ).show()
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.commit_detail_menu_copy_file_path)) },
        onClick = {
            clipboard.setPrimaryClip(ClipData.newPlainText("file_path", filePath))
            onDismissMenu()
            Toast.makeText(
                context,
                context.getString(R.string.commit_detail_toast_copied_file_path),
                Toast.LENGTH_SHORT
            ).show()
        }
    )
}

private fun buildChangedFilesTree(fileDiffs: List<FileDiff>): FileTreeNode {
    data class MutableNode(
        val name: String,
        val path: String,
        val children: MutableMap<String, MutableNode> = linkedMapOf(),
        var isFile: Boolean = false
    )

    val root = MutableNode(name = "", path = "")

    fileDiffs.forEach { diff ->
        val segments = diff.path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach

        var current = root
        val pathBuilder = StringBuilder()
        segments.forEachIndexed { index, segment ->
            if (pathBuilder.isNotEmpty()) pathBuilder.append('/')
            pathBuilder.append(segment)
            val currentPath = pathBuilder.toString()
            val child = current.children.getOrPut(segment) {
                MutableNode(name = segment, path = currentPath)
            }
            if (index == segments.lastIndex) {
                child.isFile = true
            }
            current = child
        }
    }

    fun toImmutable(node: MutableNode): FileTreeNode {
        val type = if (node.isFile) FileTreeNodeType.FILE else FileTreeNodeType.DIRECTORY
        val sortedChildren = node.children.values
            .sortedWith(
                compareBy<MutableNode> { it.isFile }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .map(::toImmutable)

        return FileTreeNode(
            name = node.name,
            path = node.path,
            type = type,
            children = sortedChildren
        )
    }

    return FileTreeNode(
        name = "changed",
        path = "",
        type = FileTreeNodeType.DIRECTORY,
        children = root.children.values
            .sortedWith(
                compareBy<MutableNode> { it.isFile }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .map(::toImmutable)
    )
}

@Composable
fun CommitInfoView(commit: Commit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Commit message
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.commit_detail_message),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = commit.message,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (commit.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.commit_detail_description),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = commit.description,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            InfoCard(
                title = stringResource(R.string.commit_detail_commit_hash),
                content = commit.hash,
                icon = Icons.Default.Tag
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.commit_detail_author),
                content = "${commit.author} <${commit.email}>",
                icon = Icons.Default.Person
            )
        }

        item {
            InfoCard(
                title = stringResource(R.string.commit_detail_date),
                content = formatDate(commit.timestamp),
                icon = Icons.Default.DateRange
            )
        }

        /*if (commit.branch != null) {
            item {
                InfoCard(
                    title = "Branch",
                    content = commit.branch,
                    icon = Icons.Default.AccountTree
                )
            }
        }*/

        if (commit.tags.isNotEmpty()) {
            item {
                InfoCard(
                    title = stringResource(R.string.commit_detail_tags),
                    content = commit.tags.joinToString(", "),
                    icon = Icons.Default.LocalOffer
                )
            }
        }

        if (commit.parents.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ForkRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.commit_detail_parents, commit.parents.size),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        commit.parents.forEach { parent ->
                            Text(
                                text = parent,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = content,
                        fontFamily = if (title == "Commit Hash") FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatHistoryDate(timestamp: Long): String {
    if (timestamp <= 0L) return "--/--/-- --:--"
    val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        fileName.endsWith(".kt") -> Icons.Default.Code
        fileName.endsWith(".java") -> Icons.Default.Code
        fileName.endsWith(".xml") -> Icons.Default.Language
        fileName.endsWith(".json") -> Icons.Default.DataObject
        fileName.endsWith(".md") -> Icons.Default.Article
        fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") -> Icons.Default.Build
        fileName.endsWith(".properties") -> Icons.Default.Settings
        fileName.endsWith(".txt") -> Icons.Default.TextSnippet
        fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> Icons.Default.Image
        fileName == "AndroidManifest.xml" -> Icons.Default.Android
        fileName == "README.md" -> Icons.Default.Info
        fileName == ".gitignore" -> Icons.Default.VisibilityOff
        else -> Icons.Default.InsertDriveFile
    }
}

fun getFileIconColor(fileName: String): Color {
    return when {
        fileName.endsWith(".kt") -> Color(0xFF7C4DFF)
        fileName.endsWith(".java") -> Color(0xFFFF7043)
        fileName.endsWith(".xml") -> Color(0xFF4CAF50)
        fileName.endsWith(".json") -> Color(0xFFFF9800)
        fileName.endsWith(".md") -> Color(0xFF2196F3)
        fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") -> Color(0xFF4CAF50)
        fileName.endsWith(".properties") -> Color(0xFF9C27B0)
        fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> Color(0xFFE91E63)
        fileName == "AndroidManifest.xml" -> Color(0xFF4CAF50)
        else -> Color(0xFF757575)
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}

@Composable
fun FileTreeView(
    commit: Commit,
    repository: Repository?,
    gitRepository: IGitRepository,
    viewModel: CommitDetailViewModel,
    onRequestRestoreToCommit: (fileName: String, filePath: String) -> Unit,
    onRequestRestoreToParent: (fileName: String, filePath: String) -> Unit,
    onRequestViewHistory: (fileName: String, filePath: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pure UI state stays local
    var selectedFileForViewing by remember { mutableStateOf<FileTreeNode?>(null) }
    var pendingFileAction by remember { mutableStateOf<FileTreeNode?>(null) }
    var isExternalOpening by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = remember { AppSettingsManager(context) }
    var previewExtensions by remember { mutableStateOf(settingsManager.getPreviewExtensions()) }
    var previewFileNames by remember { mutableStateOf(settingsManager.getPreviewFileNames()) }

    DisposableEffect(settingsManager) {
        val listener = settingsManager.registerPreviewSettingsListener {
            previewExtensions = settingsManager.getPreviewExtensions()
            previewFileNames = settingsManager.getPreviewFileNames()
        }
        onDispose {
            settingsManager.unregisterPreviewSettingsListener(listener)
        }
    }

    val normalizedPreviewExtensions = remember(previewExtensions) {
        previewExtensions.mapNotNull { normalizeExtensionToken(it) }.toSet()
    }

    val normalizedPreviewFileNames = remember(previewFileNames) {
        previewFileNames.mapNotNull { normalizeFileNameToken(it) }.toSet()
    }

    val filterTokens = remember(uiState.filterQuery) {
        uiState.filterQuery.split(' ', ',', ';')
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) null else trimmed.lowercase(Locale.ROOT)
            }
    }

    val displayedTree = remember(uiState.fileTree, filterTokens) {
        val root = uiState.fileTree ?: return@remember null
        val tree = if (filterTokens.isEmpty()) root else filterFileTree(root, filterTokens)
        tree?.let { collapseSingleChildDirectories(it, isRoot = true) }
    }

    // Trigger tree loading when this composable first enters composition
    LaunchedEffect(Unit) {
        viewModel.loadFileTree()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uiState.showFilterBar) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.commit_detail_file_tree_project_files),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (uiState.isLoadingTree) {
                            Text(
                                text = stringResource(R.string.commit_detail_file_tree_loading),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.commit_detail_file_tree_commit_info, commit.hash.take(7), uiState.fileTree?.children?.sumOf { countFiles(it) } ?: 0),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleFilterBar() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.commit_detail_file_tree_search_files))
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.filterQuery,
                        onValueChange = { viewModel.setFilterQuery(it) },
                        label = { Text(stringResource(R.string.commit_detail_file_tree_filter)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.filterQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setFilterQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.commit_detail_file_tree_clear_filter))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.closeFilterBar() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.commit_detail_file_tree_close_search))
                    }
                }
            }
        }

        // File tree content
        if (uiState.isLoadingTree) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val treeToRender = displayedTree
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (treeToRender?.children?.isNotEmpty() == true) {
                    items(treeToRender.children) { node ->
                        FileTreeNodeItem(
                            node = node,
                            level = 0,
                            isFiltering = filterTokens.isNotEmpty(),
                            hasParentCommit = commit.parents.isNotEmpty(),
                            contextMenuTargetPath = uiState.contextMenuTargetPath,
                            restoreInProgress = uiState.isRestoringFile,
                            onFileClicked = {
                                viewModel.setContextMenuTarget(null)
                                pendingFileAction = it
                            },
                            onFileLongPress = { fileNode ->
                                if (!uiState.isRestoringFile) {
                                    viewModel.setContextMenuTarget(fileNode.path)
                                }
                            },
                            onDismissContextMenu = { viewModel.setContextMenuTarget(null) },
                            onRestoreFromCommit = { fileNode ->
                                viewModel.setContextMenuTarget(null)
                                onRequestRestoreToCommit(fileNode.name, fileNode.path)
                            },
                            onRestoreFromParent = { fileNode ->
                                if (commit.parents.isEmpty()) {
                                    viewModel.setContextMenuTarget(null)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.commit_detail_toast_no_parent),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@FileTreeNodeItem
                                }
                                viewModel.setContextMenuTarget(null)
                                onRequestRestoreToParent(fileNode.name, fileNode.path)
                            },
                            onViewHistory = { fileNode ->
                                viewModel.setContextMenuTarget(null)
                                onRequestViewHistory(fileNode.name, fileNode.path)
                            }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (filterTokens.isEmpty()) {
                                        stringResource(R.string.commit_detail_file_tree_no_files)
                                    } else {
                                        stringResource(R.string.commit_detail_file_tree_no_match)
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingFileAction?.let { file ->
        val allowPreview = isPreviewSupported(
            fileName = file.name,
            allowedExtensions = normalizedPreviewExtensions,
            allowedFileNames = normalizedPreviewFileNames
        )
        FileOpenOptionsDialog(
            file = file,
            canPreviewInApp = allowPreview,
            isExternalLoading = isExternalOpening,
            onOpenInApp = {
                selectedFileForViewing = file
                pendingFileAction = null
            },
            onOpenExternal = {
                if (!isExternalOpening) {
                    isExternalOpening = true
                    scope.launch {
                        val opened = try {
                            openFileInExternalApp(
                                context = context,
                                gitRepository = gitRepository,
                                commit = commit,
                                file = file,
                                repository = repository
                            )
                        } finally {
                            isExternalOpening = false
                        }
                        if (opened) {
                            pendingFileAction = null
                        }
                    }
                }
            },
            onDismiss = {
                if (!isExternalOpening) {
                    pendingFileAction = null
                }
            }
        )
    }

    // File viewer dialog
    selectedFileForViewing?.let { file ->
        FileViewerDialog(
            file = file,
            commit = commit,
            repository = repository,
            gitRepository = gitRepository,
            onDismiss = { selectedFileForViewing = null }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeNodeItem(
    node: FileTreeNode,
    level: Int,
    isFiltering: Boolean,
    hasParentCommit: Boolean,
    contextMenuTargetPath: String?,
    restoreInProgress: Boolean,
    onFileClicked: (FileTreeNode) -> Unit,
    onFileLongPress: (FileTreeNode) -> Unit,
    onDismissContextMenu: () -> Unit,
    onRestoreFromCommit: (FileTreeNode) -> Unit,
    onRestoreFromParent: (FileTreeNode) -> Unit,
    onViewHistory: (FileTreeNode) -> Unit,
    fileMetaContent: (@Composable (FileTreeNode) -> Unit)? = null,
    fileTrailingIcon: (@Composable (FileTreeNode) -> Unit)? = null
) {
    var expanded by remember(isFiltering) { mutableStateOf(if (isFiltering) true else level < 2) } // Раскрыты первые 2 уровня по умолчанию

    Column {
        // Node item
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                onDismissContextMenu()
                                if (node.type == FileTreeNodeType.DIRECTORY) {
                                    expanded = !expanded
                                } else {
                                    onFileClicked(node)
                                }
                            },
                            onLongClick = {
                                if (node.type == FileTreeNodeType.FILE && !restoreInProgress) {
                                    onFileLongPress(node)
                                }
                            }
                        )
                        .padding(
                            start = (level * 20).dp + 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                            end = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.type == FileTreeNodeType.DIRECTORY) {
                        Icon(
                            if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFFFFB74D)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(24.dp))
                        Icon(
                            getFileIcon(node.name),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = getFileIconColor(node.name)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.name,
                            fontSize = 15.sp,
                            fontWeight = if (node.type == FileTreeNodeType.DIRECTORY) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (node.type == FileTreeNodeType.DIRECTORY)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )

                        if (node.type == FileTreeNodeType.FILE && node.size != null) {
                            Text(
                                text = formatFileSize(node.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (node.type == FileTreeNodeType.FILE) {
                            fileMetaContent?.invoke(node)
                        } else if (node.type == FileTreeNodeType.DIRECTORY) {
                            val filesCount = countFiles(node)
                            Text(
                                text = if (filesCount == 1)
                                    stringResource(R.string.commit_detail_file_tree_file_count, filesCount)
                                else
                                    stringResource(R.string.commit_detail_file_tree_files_count, filesCount),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (node.type == FileTreeNodeType.FILE) {
                        if (fileTrailingIcon != null) {
                            fileTrailingIcon(node)
                        } else {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.commit_detail_file_tree_view_file),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (node.type == FileTreeNodeType.FILE) {
                    DropdownMenu(
                        expanded = contextMenuTargetPath == node.path,
                        onDismissRequest = onDismissContextMenu
                    ) {
                        FileActionsDropdownContent(
                            fileName = node.name,
                            filePath = node.path,
                            hasParentCommit = hasParentCommit,
                            restoreInProgress = restoreInProgress,
                            onDismissMenu = onDismissContextMenu,
                            onRestoreFromCommit = { onRestoreFromCommit(node) },
                            onRestoreFromParent = { onRestoreFromParent(node) },
                            onViewHistory = { onViewHistory(node) }
                        )
                    }
                }
            }
        }

        // Children (if directory is expanded)
        if (node.type == FileTreeNodeType.DIRECTORY && expanded) {
            node.children.forEach { child ->
                FileTreeNodeItem(
                    node = child,
                    level = level + 1,
                    isFiltering = isFiltering,
                    hasParentCommit = hasParentCommit,
                    contextMenuTargetPath = contextMenuTargetPath,
                    restoreInProgress = restoreInProgress,
                    onFileClicked = onFileClicked,
                    onFileLongPress = onFileLongPress,
                    onDismissContextMenu = onDismissContextMenu,
                    onRestoreFromCommit = onRestoreFromCommit,
                    onRestoreFromParent = onRestoreFromParent,
                    onViewHistory = onViewHistory,
                    fileMetaContent = fileMetaContent,
                    fileTrailingIcon = fileTrailingIcon
                )
            }
        }
    }
}

private fun fileStatusIcon(status: FileStatus): androidx.compose.ui.graphics.vector.ImageVector = when (status) {
    FileStatus.ADDED -> Icons.Default.AddCircle
    FileStatus.MODIFIED -> Icons.Default.Edit
    FileStatus.DELETED -> Icons.Default.RemoveCircle
    FileStatus.RENAMED -> Icons.Default.DriveFileRenameOutline
}

private fun fileStatusColor(status: FileStatus): Color = when (status) {
    FileStatus.ADDED -> Color(0xFF4CAF50)
    FileStatus.MODIFIED -> Color(0xFFFF9800)
    FileStatus.DELETED -> Color(0xFFF44336)
    FileStatus.RENAMED -> Color(0xFF2196F3)
}

@Composable
fun FileOpenOptionsDialog(
    file: FileTreeNode,
    canPreviewInApp: Boolean,
    isExternalLoading: Boolean,
    onOpenInApp: () -> Unit,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!isExternalLoading) {
                onDismiss()
            }
        }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.commit_detail_dialog_open_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = file.path,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!canPreviewInApp) {
                        Text(
                            text = stringResource(R.string.commit_detail_dialog_preview_unavailable),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canPreviewInApp) {
                    Button(
                        onClick = onOpenInApp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.commit_detail_dialog_open_preview))
                    }
                }

                Button(
                    onClick = onOpenExternal,
                    enabled = !isExternalLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExternalLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.commit_detail_dialog_opening))
                    } else {
                        Text(stringResource(R.string.commit_detail_dialog_open_external))
                    }
                }

                TextButton(
                    onClick = {
                        if (!isExternalLoading) {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.commit_detail_dialog_cancel))
                }
            }
        }
    }
}

@Composable
fun FileViewerDialog(
    file: FileTreeNode,
    commit: Commit,
    repository: Repository?,
    gitRepository: IGitRepository,
    onDismiss: () -> Unit
) {
    var fileContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(file, repository) {
        isLoading = true
        loadError = null
        try {
            fileContent = if (repository != null) {
                gitRepository.getFileContent(commit, file.path, repository)
            } else {
                gitRepository.getFileContent(commit, file.path)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load file content")
            loadError = context.getString(R.string.commit_detail_viewer_load_error, e.message ?: "Unknown error")
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                getFileIcon(file.name),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = getFileIconColor(file.name)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = file.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = file.path,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                if (file.size != null) {
                                    Text(
                                        text = formatFileSize(file.size),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    // TODO: Добавить копирование содержимого в буфер обмена
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.commit_detail_viewer_copy_content),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.commit_detail_viewer_close),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.commit_detail_viewer_loading),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    loadError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.commit_detail_viewer_load_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = loadError!!,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    fileContent != null -> {
                        SyntaxHighlightedFileContent(
                            content = fileContent!!,
                            fileName = file.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.commit_detail_viewer_load_failed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyntaxHighlightedFileContent(
    content: String,
    fileName: String,
    modifier: Modifier = Modifier
) {
    var renderError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (renderError != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.commit_detail_viewer_render_failed),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = renderError!!,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val isCodeFile = isCodeFile(fileName)
    val lines = remember(content) {
        try {
            content.split('\n')
        } catch (e: Exception) {
            Timber.e(e, "Failed to split content")
            renderError = e.message ?: "Unknown error"
            emptyList()
        }
    }
    val density = LocalDensity.current
    val textStyle = remember {
        TextStyle(
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val annotatedLines = remember(lines, fileName, isCodeFile) {
        try {
            lines.map { line ->
                if (isCodeFile) buildHighlightedAnnotatedString(line, fileName) else AnnotatedString(line)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to highlight syntax")
            renderError = e.message ?: "Unknown error"
            emptyList()
        }
    }
    val maxContentWidthPx = remember(annotatedLines) {
        try {
            annotatedLines.maxOfOrNull { annotatedLine ->
                textMeasurer.measure(annotatedLine, style = textStyle).size.width.toFloat()
            } ?: 0f
        } catch (e: Exception) {
            Timber.e(e, "Failed to measure text")
            renderError = e.message ?: "Unknown error"
            0f
        }
    }
    val structuralWidthPx = with(density) {
        (CodeLineHorizontalPadding * 2 + CodeLineNumberWidth + CodeLineContentSpacing).toPx()
    }
    val maxContentWidthDp = remember(maxContentWidthPx, structuralWidthPx) {
        with(density) { (maxContentWidthPx + structuralWidthPx).toDp() }
    }

    Column(modifier = modifier) {
        // File type indicator
        if (isCodeFile) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.commit_detail_viewer_syntax_enabled, getFileLanguage(fileName)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val lineWidth = remember(maxWidth, maxContentWidthDp) {
                if (maxContentWidthDp > maxWidth) maxContentWidthDp else maxWidth
            }
            val horizontalScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn(
                    modifier = Modifier.width(lineWidth)
                ) {
                    itemsIndexed(annotatedLines) { index, annotatedLine ->
                        CodeLine(
                            lineNumber = index + 1,
                            content = annotatedLine,
                            textStyle = textStyle,
                            lineWidth = lineWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeLine(
    lineNumber: Int,
    content: AnnotatedString,
    textStyle: TextStyle,
    lineWidth: Dp
) {
    val backgroundColor = if (lineNumber % 2 == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .width(lineWidth)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Text(
                text = lineNumber.toString().padStart(4, ' '),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            SelectionContainer {
                Text(
                    text = content,
                    style = textStyle
                )
            }
        }
    }
}

fun buildHighlightedAnnotatedString(
    text: String,
    fileName: String
): AnnotatedString {
    return buildAnnotatedString {
        when {
            fileName.endsWith(".kt") -> highlightKotlin(text)
            fileName.endsWith(".java") -> highlightJava(text)
            fileName.endsWith(".xml") -> highlightXml(text)
            fileName.endsWith(".json") -> highlightJson(text)
            fileName.endsWith(".md") -> highlightMarkdown(text)
            fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") -> highlightGradle(text)
            else -> append(text)
        }
    }
}

// Helper functions
fun countFiles(node: FileTreeNode): Int {
    if (node.type == FileTreeNodeType.FILE) return 1
    return node.children.sumOf { countFiles(it) }
}

fun filterFileTree(node: FileTreeNode, tokens: List<String>): FileTreeNode? {
    if (tokens.isEmpty()) return node

    val matchesCurrent = matchesFilter(node, tokens)

    return if (node.type == FileTreeNodeType.DIRECTORY) {
        if (matchesCurrent) {
            node
        } else {
            val filteredChildren = node.children.mapNotNull { child -> filterFileTree(child, tokens) }
            if (filteredChildren.isNotEmpty()) {
                node.copy(children = filteredChildren)
            } else {
                null
            }
        }
    } else {
        if (matchesCurrent) node else null
    }
}

@Composable
fun HistoryFileDiffDialog(
    commit: Commit,
    filePath: String,
    fileDiff: FileDiff?,
    isLoading: Boolean,
    error: String?,
    allowedExtensions: Set<String>,
    allowedFileNames: Set<String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.commit_detail_diff_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = filePath,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.commit_detail_diff_commit, commit.message),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatHistoryDate(commit.timestamp)} • ${commit.author}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.commit_detail_diff_close))
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.commit_detail_diff_loading),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.commit_detail_diff_error_hint),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    fileDiff != null -> {
                        DiffView(
                            selectedFile = fileDiff,
                            allowedExtensions = allowedExtensions,
                            allowedFileNames = allowedFileNames
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.commit_detail_diff_unavailable),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileHistoryDialog(
    file: FileTreeNode,
    commit: Commit,
    history: List<Commit>,
    isLoading: Boolean,
    error: String?,
    isDiffLoading: Boolean,
    selectedHistoryCommit: Commit?,
    onDismiss: () -> Unit,
    onSelectCommit: (Commit) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.commit_detail_history_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = file.path.ifEmpty { file.name },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.commit_detail_history_up_to, commit.hash.take(7)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.commit_detail_history_close))
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.commit_detail_history_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.commit_detail_history_error_hint),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    history.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.commit_detail_history_no_changes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(history) { historyCommit ->
                                val isSelected = selectedHistoryCommit?.hash == historyCommit.hash
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectCommit(historyCommit) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    },
                                    tonalElevation = if (isSelected) 4.dp else 0.dp,
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = historyCommit.message,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatHistoryDate(historyCommit.timestamp)} • ${historyCommit.author}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isDiffLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(R.string.commit_detail_history_loading_diff),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun collapseSingleChildDirectories(node: FileTreeNode, isRoot: Boolean = false): FileTreeNode {
    if (node.type == FileTreeNodeType.FILE) return node

    val collapsedChildren = node.children.map { child ->
        collapseSingleChildDirectories(child, isRoot = false)
    }

    if (isRoot) {
        return node.copy(children = collapsedChildren)
    }

    if (collapsedChildren.size == 1 && collapsedChildren.first().type == FileTreeNodeType.DIRECTORY) {
        val child = collapsedChildren.first()
        val mergedName = sequenceOf(node.name, child.name)
            .filter { it.isNotEmpty() }
            .joinToString("/")
        val mergedPath = if (child.path.isEmpty()) node.path else child.path
        return node.copy(
            name = if (mergedName.isEmpty()) child.name else mergedName,
            path = if (mergedPath.isEmpty()) node.path else mergedPath,
            children = child.children,
            lastModified = child.lastModified
        )
    }

    return node.copy(children = collapsedChildren)
}

private fun matchesFilter(node: FileTreeNode, tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true
    val nameTarget = node.name.lowercase(Locale.ROOT)
    val pathTarget = node.path.lowercase(Locale.ROOT)
    return tokens.all { token ->
        pathTarget.contains(token) || nameTarget.contains(token)
    }
}

fun isCodeFile(fileName: String): Boolean {
    return fileName.endsWith(".kt") ||
           fileName.endsWith(".java") ||
           fileName.endsWith(".xml") ||
           fileName.endsWith(".json") ||
           fileName.endsWith(".md") ||
           fileName.endsWith(".gradle") ||
           fileName.endsWith(".gradle.kts") ||
           fileName.endsWith(".properties") ||
           fileName.endsWith(".yml") ||
           fileName.endsWith(".yaml")
}

fun isPreviewSupported(
    fileName: String,
    allowedExtensions: Set<String>,
    allowedFileNames: Set<String>
): Boolean {
    val lowerName = fileName.lowercase(Locale.ROOT)
    if (allowedFileNames.contains(lowerName)) {
        return true
    }

    val extension = lowerName.substringAfterLast('.', "")
    if (extension.isNotEmpty() && allowedExtensions.contains(extension)) {
        return true
    }

    if (allowedExtensions.any { lowerName.endsWith(".$it") }) {
        return true
    }

    return false
}

suspend fun openFileInExternalApp(
    context: Context,
    gitRepository: IGitRepository,
    commit: Commit,
    file: FileTreeNode,
    repository: Repository?
): Boolean {
    val bytes = if (repository != null) {
        gitRepository.getFileContentBytes(commit, file.path, repository)
    } else {
        gitRepository.getFileContentBytes(commit, file.path)
    }

    if (bytes == null) {
        Toast.makeText(context, context.getString(R.string.commit_detail_toast_load_failed), Toast.LENGTH_SHORT).show()
        return false
    }

    val exportedFile = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "commit_files")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val sanitizedName = sanitizeFileName("${commit.hash.take(7)}_${file.path}")
        val outputFile = File(cacheDir, sanitizedName)
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        outputFile
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exportedFile
    )

    val mimeType = guessMimeType(exportedFile.name)

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(viewIntent, "Open with")
    if (context !is Activity) {
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.commit_detail_toast_open_failed), Toast.LENGTH_SHORT).show()
        false
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.commit_detail_toast_open_error), Toast.LENGTH_SHORT).show()
        false
    }
}

private fun sanitizeFileName(raw: String): String {
    val replacedSeparators = raw.replace('/', '_').replace('\\', '_')
    val cleaned = replacedSeparators.replace(Regex("[^A-Za-z0-9._-]"), "_")
    if (cleaned.isEmpty()) {
        return "file"
    }

    val maxLength = 200
    if (cleaned.length <= maxLength) {
        return cleaned
    }

    val extension = cleaned.substringAfterLast('.', "")
    return if (extension.isNotEmpty() && extension.length < maxLength) {
        val base = cleaned.substring(0, maxLength - extension.length - 1)
        "$base.$extension"
    } else {
        cleaned.substring(0, maxLength)
    }
}

private fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "")
    if (extension.isNotEmpty()) {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
        if (mime != null) {
            return mime
        }
        return "application/octet-stream"
    }
    return "text/plain"
}

private fun normalizeExtensionToken(value: String): String? {
    val trimmed = value.trim().lowercase(Locale.ROOT)
    if (trimmed.isEmpty()) return null
    return trimmed.removePrefix(".")
}

private fun normalizeFileNameToken(value: String): String? {
    val trimmed = value.trim().lowercase(Locale.ROOT)
    return trimmed.takeIf { it.isNotEmpty() }
}

fun getFileLanguage(fileName: String): String {
    return when {
        fileName.endsWith(".kt") -> "Kotlin"
        fileName.endsWith(".java") -> "Java"
        fileName.endsWith(".xml") -> "XML"
        fileName.endsWith(".json") -> "JSON"
        fileName.endsWith(".md") -> "Markdown"
        fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") -> "Gradle"
        fileName.endsWith(".properties") -> "Properties"
        fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> "YAML"
        else -> "Text"
    }
}

// Syntax highlighting extensions
fun androidx.compose.ui.text.AnnotatedString.Builder.highlightKotlin(text: String) {
    val keywords = setOf("class", "fun", "val", "var", "if", "else", "when", "for", "while", "return", "import", "package", "private", "public", "internal", "protected", "override", "open", "abstract", "final", "companion", "object", "data", "sealed", "interface", "enum", "annotation", "suspend", "inline", "crossinline", "noinline", "reified", "lateinit", "lazy", "delegate", "by", "in", "out", "is", "as", "typealias", "this", "super", "null", "true", "false")
    highlightCode(text, keywords, Color(0xFF7C4DFF), Color(0xFF4CAF50), Color(0xFF9E9E9E))
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightJava(text: String) {
    val keywords = setOf("class", "public", "private", "protected", "static", "final", "abstract", "interface", "extends", "implements", "import", "package", "if", "else", "for", "while", "do", "switch", "case", "default", "return", "break", "continue", "try", "catch", "finally", "throw", "throws", "new", "this", "super", "null", "true", "false", "void", "int", "String", "boolean", "long", "double", "float", "char", "byte", "short")
    highlightCode(text, keywords, Color(0xFFFF7043), Color(0xFF4CAF50), Color(0xFF9E9E9E))
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightXml(text: String) {
    // Простая подсветка XML
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '<' -> {
                val endIndex = text.indexOf('>', i)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = Color(0xFF2196F3))) {
                        append(text.substring(i, endIndex + 1))
                    }
                    i = endIndex + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("<!--", i) -> {
                val endIndex = text.indexOf("-->", i + 4)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = Color(0xFF9E9E9E))) {
                        append(text.substring(i, endIndex + 3))
                    }
                    i = endIndex + 3
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightJson(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '"' -> {
                val endIndex = text.indexOf('"', i + 1)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = Color(0xFF4CAF50))) {
                        append(text.substring(i, endIndex + 1))
                    }
                    i = endIndex + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i].isDigit() -> {
                var j = i
                while (j < text.length && (text[j].isDigit() || text[j] == '.')) j++
                withStyle(SpanStyle(color = Color(0xFFFF9800))) {
                    append(text.substring(i, j))
                }
                i = j
            }
            text.startsWith("true", i) || text.startsWith("false", i) || text.startsWith("null", i) -> {
                val word = when {
                    text.startsWith("true", i) -> "true"
                    text.startsWith("false", i) -> "false"
                    else -> "null"
                }
                withStyle(SpanStyle(color = Color(0xFF7C4DFF))) {
                    append(word)
                }
                i += word.length
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("# ", i) -> {
                val endIndex = text.indexOf('\n', i)
                val end = if (endIndex != -1) endIndex else text.length
                withStyle(SpanStyle(color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            text.startsWith("```", i) -> {
                val endIndex = text.indexOf("```", i + 3)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = Color(0xFF9E9E9E), background = Color(0xFFF5F5F5))) {
                        append(text.substring(i, endIndex + 3))
                    }
                    i = endIndex + 3
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val endIndex = text.indexOf('`', i + 1)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = Color(0xFFE91E63), background = Color(0xFFF5F5F5))) {
                        append(text.substring(i, endIndex + 1))
                    }
                    i = endIndex + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightGradle(text: String) {
    val keywords = setOf("plugins", "dependencies", "implementation", "api", "testImplementation", "androidTestImplementation", "android", "compileSdk", "minSdk", "targetSdk", "versionCode", "versionName", "buildTypes", "debug", "release", "defaultConfig", "sourceSets", "repositories", "maven", "google", "mavenCentral", "apply", "plugin", "id", "version")
    highlightCode(text, keywords, Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9E9E9E))
}

fun androidx.compose.ui.text.AnnotatedString.Builder.highlightCode(
    text: String,
    keywords: Set<String>,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color
) {
    var i = 0
    while (i < text.length) {
        when {
            // String literals
            text[i] == '"' -> {
                val endIndex = text.indexOf('"', i + 1)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = stringColor)) {
                        append(text.substring(i, endIndex + 1))
                    }
                    i = endIndex + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Single line comments
            text.startsWith("//", i) -> {
                val endIndex = text.indexOf('\n', i)
                val end = if (endIndex != -1) endIndex else text.length
                withStyle(SpanStyle(color = commentColor)) {
                    append(text.substring(i, end))
                }
                i = end
            }
            // Multi-line comments
            text.startsWith("/*", i) -> {
                val endIndex = text.indexOf("*/", i + 2)
                if (endIndex != -1) {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(text.substring(i, endIndex + 2))
                    }
                    i = endIndex + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Keywords
            text[i].isLetter() -> {
                var j = i
                while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                val word = text.substring(i, j)
                if (keywords.contains(word)) {
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                i = j
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun FileStatus.toLocalizedString(): String {
    return when (this) {
        FileStatus.ADDED -> stringResource(R.string.file_status_added)
        FileStatus.MODIFIED -> stringResource(R.string.file_status_modified)
        FileStatus.DELETED -> stringResource(R.string.file_status_deleted)
        FileStatus.RENAMED -> stringResource(R.string.file_status_renamed)
    }
}
