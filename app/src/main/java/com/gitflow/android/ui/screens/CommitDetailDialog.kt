package com.gitflow.android.ui.screens

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.MockGitRepository
import com.gitflow.android.data.repository.RealGitRepository
import java.text.SimpleDateFormat
import java.util.*

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
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Commit Details",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = commit.hash,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        // Commit info
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Author info
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = commit.author.first().uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = commit.author,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = commit.email,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatDate(commit.timestamp),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Commit message
                                Text(
                                    text = commit.message,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                if (commit.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = commit.description,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Stats
                                Spacer(modifier = Modifier.height(12.dp))
                                if (isLoadingDiffs) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        }

                        // Tabs
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Changes (${if (isLoadingDiffs) "..." else fileDiffs.size})") }
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
                    }
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
                        DiffView(fileDiffs)
                    }
                    1 -> if (isLoadingDiffs) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        FileListView(fileDiffs)
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
fun DiffView(fileDiffs: List<FileDiff>) {
    var selectedFile by remember { mutableStateOf(fileDiffs.firstOrNull()) }
    var showSideBySide by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // File selector
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(fileDiffs) { diff ->
                FilterChip(
                    selected = selectedFile == diff,
                    onClick = { selectedFile = diff },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (diff.status) {
                                    FileStatus.ADDED -> Icons.Default.Add
                                    FileStatus.MODIFIED -> Icons.Default.Edit
                                    FileStatus.DELETED -> Icons.Default.Delete
                                    FileStatus.RENAMED -> Icons.Default.DriveFileRenameOutline
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = when (diff.status) {
                                    FileStatus.ADDED -> Color(0xFF4CAF50)
                                    FileStatus.MODIFIED -> Color(0xFFFF9800)
                                    FileStatus.DELETED -> Color(0xFFF44336)
                                    FileStatus.RENAMED -> Color(0xFF2196F3)
                                }
                            )
                            Text(
                                text = diff.path.substringAfterLast("/"),
                                fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        }

        // View mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedFile?.path ?: "",
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
        selectedFile?.let { diff ->
            if (showSideBySide) {
                SideBySideDiffView(diff)
            } else {
                UnifiedDiffView(diff)
            }
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
fun FileListView(fileDiffs: List<FileDiff>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(fileDiffs) { diff ->
            FileItem(diff)
        }
    }
}

@Composable
fun FileItem(diff: FileDiff) {
    Card(
        modifier = Modifier.fillMaxWidth()
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

        if (commit.branch != null) {
            item {
                InfoCard(
                    title = "Branch",
                    content = commit.branch,
                    icon = Icons.Default.AccountTree
                )
            }
        }

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
    val scope = rememberCoroutineScope()

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
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (fileTree?.children?.isNotEmpty() == true) {
                    items(fileTree!!.children) { node ->
                        FileTreeNodeItem(
                            node = node,
                            level = 0,
                            onFileClicked = { selectedFileForViewing = it }
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
                                    text = "No files found in this commit",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
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
    onFileClicked: (FileTreeNode) -> Unit
) {
    var expanded by remember { mutableStateOf(level < 2) } // Раскрыты первые 2 уровня по умолчанию

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
                    onFileClicked = onFileClicked
                )
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
    val backgroundColor = MaterialTheme.colorScheme.surface
    val isCodeFile = isCodeFile(fileName)

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

        // Content with line numbers and horizontal scroll
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            val lines = content.split('\n')
            itemsIndexed(lines) { index, line ->
                CodeLine(
                    lineNumber = index + 1,
                    content = line,
                    fileName = fileName,
                    isCodeFile = isCodeFile
                )
            }
        }
    }
}

@Composable
fun CodeLine(
    lineNumber: Int,
    content: String,
    fileName: String,
    isCodeFile: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (lineNumber % 2 == 0)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        // Line number
        Text(
            text = lineNumber.toString().padStart(4, ' '),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(48.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Content with basic syntax highlighting
        SelectionContainer {
            if (isCodeFile) {
                HighlightedText(
                    text = content,
                    fileName = fileName
                )
            } else {
                Text(
                    text = content,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    fileName: String
) {
    val highlightedText = buildAnnotatedString {
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

    Text(
        text = highlightedText,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp
    )
}

// Helper functions
fun countFiles(node: FileTreeNode): Int {
    if (node.type == FileTreeNodeType.FILE) return 1
    return node.children.sumOf { countFiles(it) }
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
