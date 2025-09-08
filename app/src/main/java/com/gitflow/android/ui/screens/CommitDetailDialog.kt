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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailDialog(
    commit: Commit,
    gitRepository: MockGitRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val fileDiffs = remember { gitRepository.getCommitDiffs(commit) }

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

                        // Tabs
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Changes (${fileDiffs.size})") }
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
                        }
                    }
                }

                // Tab content
                when (selectedTab) {
                    0 -> DiffView(fileDiffs)
                    1 -> FileListView(fileDiffs)
                    2 -> CommitInfoView(commit)
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
                Text(
                    text = line.lineNumber?.toString() ?: "",
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