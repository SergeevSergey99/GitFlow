package com.gitflow.android.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.RealGitRepository
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
    gitRepository: RealGitRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var fileDiffs by remember { mutableStateOf<List<FileDiff>>(emptyList()) }
    var isLoadingDiffs by remember { mutableStateOf(true) }
    var selectedFile by remember { mutableStateOf<FileDiff?>(null) }
    val scope = rememberCoroutineScope()

    // Загружаем диффы при открытии диалога
    LaunchedEffect(commit, repository) {
        isLoadingDiffs = true
        android.util.Log.d("CommitDetailDialog", "Loading diffs for commit: ${commit.hash}")
        fileDiffs = if (repository != null) {
            gitRepository.getCommitDiffs(commit, repository)
        } else {
            gitRepository.getCommitDiffs(commit)
        }
        android.util.Log.d("CommitDetailDialog", "Loaded ${fileDiffs.size} file diffs")
        isLoadingDiffs = false
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
                            if (isLoadingDiffs) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatChip(
                                        icon = Icons.Default.Add,
                                        value = "+${fileDiffs.sumOf { it.additions }}",
                                        color = Color(0xFF4CAF50)
                                    )
                                    StatChip(
                                        icon = Icons.Default.Remove,
                                        value = "-${fileDiffs.sumOf { it.deletions }}",
                                        color = Color(0xFFF44336)
                                    )
                                    StatChip(
                                        icon = Icons.Default.Description,
                                        value = "${fileDiffs.size} files",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Changes") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Files") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Info") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("File Tree") }
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 -> if (isLoadingDiffs) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        DiffView(selectedFile)
                    }
                    1 -> if (isLoadingDiffs) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        FileListView(fileDiffs, selectedFile) { file ->
                            selectedFile = file
                            selectedTab = 0 // Переключаемся на вкладку Changes
                        }
                    }
                    2 -> CommitInfoView(commit)
                    3 -> FileTreeView(commit, repository, gitRepository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffView(selectedFile: FileDiff?) {
    var showSideBySide by remember { mutableStateOf(false) }

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
                    text = "Select a file in the Files tab to view changes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedFile.path,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showSideBySide = false },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ViewAgenda,
                        contentDescription = "Unified view",
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
                        contentDescription = "Side by side",
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
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(hunk.lines) { line ->
                DiffLineView(line)
            }
        }
    }
}

@Composable
fun SideBySideDiffView(diff: FileDiff) {
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
                    text = "Old",
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                diff.hunks.forEach { hunk ->
                    items(hunk.lines.filter { it.type != LineType.ADDED }) { line ->
                        DiffLineView(line, compact = true)
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
                    text = "New",
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                diff.hunks.forEach { hunk ->
                    items(hunk.lines.filter { it.type != LineType.DELETED }) { line ->
                        DiffLineView(line, compact = true)
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
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun FileListView(
    fileDiffs: List<FileDiff>,
    selectedFile: FileDiff?,
    onFileSelected: (FileDiff) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(fileDiffs) { diff ->
            FileItem(
                diff = diff,
                isSelected = selectedFile == diff,
                onClick = { onFileSelected(diff) }
            )
        }
    }
}

@Composable
fun FileItem(
    diff: FileDiff,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
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
                Text(
                    text = diff.path,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = diff.status.name,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (diff.oldPath != null && diff.oldPath != diff.path) {
                        Text(
                            text = "from ${diff.oldPath}",
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
    }
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
                            text = "Message",
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
                        Text(
                            text = "Description",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                title = "Commit Hash",
                content = commit.hash,
                icon = Icons.Default.Tag
            )
        }

        item {
            InfoCard(
                title = "Author",
                content = "${commit.author} <${commit.email}>",
                icon = Icons.Default.Person
            )
        }

        item {
            InfoCard(
                title = "Date",
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
                    title = "Tags",
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
                                text = "Parents (${commit.parents.size})",
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
fun FileTreeView(commit: Commit, repository: Repository?, gitRepository: RealGitRepository) {
    var fileTree by remember { mutableStateOf<FileTreeNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFileForViewing by remember { mutableStateOf<FileTreeNode?>(null) }
    var pendingFileAction by remember { mutableStateOf<FileTreeNode?>(null) }
    var isExternalOpening by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var showFilterBar by remember { mutableStateOf(false) }
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

    val filterTokens = remember(filterQuery) {
        filterQuery.split(' ', ',', ';')
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) null else trimmed.lowercase(Locale.ROOT)
            }
    }

    val displayedTree = remember(fileTree, filterTokens) {
        val root = fileTree
        if (root == null) {
            null
        } else if (filterTokens.isEmpty()) {
            root
        } else {
            filterFileTree(root, filterTokens)
        }
    }

    // Загружаем дерево файлов при открытии
    LaunchedEffect(commit, repository) {
        isLoading = true
        fileTree = if (repository != null) {
            gitRepository.getCommitFileTree(commit, repository)
        } else {
            gitRepository.getCommitFileTree(commit)
        }
        isLoading = false
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
                if (!showFilterBar) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Project Files",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (isLoading) {
                            Text(
                                text = "Loading...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Commit: ${commit.hash.take(7)} • ${fileTree?.children?.sumOf { countFiles(it) } ?: 0} files",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showFilterBar = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search files")
                    }
                } else {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        label = { Text("Filter by name or path") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (filterQuery.isNotBlank()) {
                                IconButton(onClick = { filterQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear filter text")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            filterQuery = ""
                            showFilterBar = false
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
                    }
                }
            }
        }

        // File tree content
        if (isLoading) {
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
                            onFileClicked = { pendingFileAction = it }
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
                                        "No files found in this commit"
                                    } else {
                                        "No files match the filter"
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

@Composable
fun FileTreeNodeItem(
    node: FileTreeNode,
    level: Int,
    isFiltering: Boolean,
    onFileClicked: (FileTreeNode) -> Unit
) {
    var expanded by remember(isFiltering) { mutableStateOf(if (isFiltering) true else level < 2) } // Раскрыты первые 2 уровня по умолчанию

    Column {
        // Node item
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.type == FileTreeNodeType.DIRECTORY) {
                        expanded = !expanded
                    } else {
                        onFileClicked(node)
                    }
                },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
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
                    } else if (node.type == FileTreeNodeType.DIRECTORY) {
                        val filesCount = countFiles(node)
                        Text(
                            text = "$filesCount ${if (filesCount == 1) "file" else "files"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (node.type == FileTreeNodeType.FILE) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "View file",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
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
                    onFileClicked = onFileClicked
                )
            }
        }
    }
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
                        text = "Open file",
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
                            text = "In-app preview is not available for this file type. Update allowed types in Settings if needed.",
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
                        Text("Open in preview")
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
                        Text("Opening...")
                    } else {
                        Text("Open externally")
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
                    Text("Cancel")
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
    gitRepository: RealGitRepository,
    onDismiss: () -> Unit
) {
    var fileContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file, repository) {
        isLoading = true
        fileContent = if (repository != null) {
            gitRepository.getFileContent(commit, file.path, repository)
        } else {
            gitRepository.getFileContent(commit, file.path)
        }
        isLoading = false
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
                                    contentDescription = "Copy content",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Content
                if (isLoading) {
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
                                text = "Loading file content...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (fileContent != null) {
                    SyntaxHighlightedFileContent(
                        content = fileContent!!,
                        fileName = file.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
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
                                text = "Failed to load file content",
                                color = MaterialTheme.colorScheme.error
                            )
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
    val isCodeFile = isCodeFile(fileName)
    val lines = remember(content) { content.split('\n') }
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
        lines.map { line ->
            if (isCodeFile) buildHighlightedAnnotatedString(line, fileName) else AnnotatedString(line)
        }
    }
    val maxContentWidthPx = remember(annotatedLines) {
        annotatedLines.maxOfOrNull { annotatedLine ->
            textMeasurer.measure(annotatedLine, style = textStyle).size.width.toFloat()
        } ?: 0f
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
                        text = "Syntax highlighting enabled for ${getFileLanguage(fileName)}",
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
    gitRepository: RealGitRepository,
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
        Toast.makeText(context, "Failed to load file content", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "No application available to open this file", Toast.LENGTH_SHORT).show()
        false
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open file", Toast.LENGTH_SHORT).show()
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
