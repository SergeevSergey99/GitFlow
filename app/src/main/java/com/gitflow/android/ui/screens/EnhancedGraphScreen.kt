package com.gitflow.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.gitflow.android.data.models.*
import com.gitflow.android.data.repository.MockGitRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedGraphView(
    repository: Repository?,
    gitRepository: MockGitRepository
) {
    if (repository == null) {
        EmptyStateMessage("Select a repository to view commits")
        return
    }

    var commits by remember { mutableStateOf(gitRepository.getCommits(repository)) }
    var selectedCommit by remember { mutableStateOf<Commit?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var showCommitDetail by remember { mutableStateOf(false) }

    // Filters
    var selectedBranch by remember { mutableStateOf<String?>(null) }
    var selectedAuthor by remember { mutableStateOf<String?>(null) }
    var dateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Graph data
    val graphData = remember(commits, selectedBranch, selectedAuthor, dateRange, searchQuery) {
        val filteredCommits = filterCommits(commits, selectedBranch, selectedAuthor, dateRange, searchQuery)
        buildGraphData(filteredCommits)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and filter bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search commits...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = { showFilters = !showFilters }) {
                                Badge(
                                    containerColor = if (hasActiveFilters(selectedBranch, selectedAuthor, dateRange))
                                        MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Filter chips
                AnimatedVisibility(visible = showFilters) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Branch filter
                        item {
                            FilterChipWithMenu(
                                label = selectedBranch ?: "All branches",
                                icon = Icons.Default.AccountTree,
                                options = extractBranches(commits),
                                selectedOption = selectedBranch,
                                onOptionSelected = { selectedBranch = it }
                            )
                        }

                        // Author filter
                        item {
                            FilterChipWithMenu(
                                label = selectedAuthor ?: "All authors",
                                icon = Icons.Default.Person,
                                options = extractAuthors(commits),
                                selectedOption = selectedAuthor,
                                onOptionSelected = { selectedAuthor = it }
                            )
                        }

                        // Date filter
                        item {
                            FilterChip(
                                selected = dateRange != null,
                                onClick = { /* Show date picker */ },
                                label = {
                                    Text(
                                        if (dateRange != null) "Custom date"
                                        else "All time"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        // Clear filters
                        if (hasActiveFilters(selectedBranch, selectedAuthor, dateRange)) {
                            item {
                                AssistChip(
                                    onClick = {
                                        selectedBranch = null
                                        selectedAuthor = null
                                        dateRange = null
                                        searchQuery = ""
                                    },
                                    label = { Text("Clear all") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Stats bar
        if (graphData.commits.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = graphData.commits.size.toString(),
                        label = "Commits"
                    )
                    StatItem(
                        value = graphData.branches.size.toString(),
                        label = "Branches"
                    )
                    StatItem(
                        value = graphData.authors.size.toString(),
                        label = "Contributors"
                    )
                    StatItem(
                        value = graphData.maxLane.toString(),
                        label = "Max lanes"
                    )
                }
            }
        }

        // Graph visualization
        Box(modifier = Modifier.weight(1f)) {
            GraphCanvas(
                graphData = graphData,
                selectedCommit = selectedCommit,
                onCommitClick = { commit ->
                    selectedCommit = commit
                    showCommitDetail = true
                }
            )
        }
    }

    // Commit detail dialog
    if (showCommitDetail && selectedCommit != null) {
        CommitDetailDialog(
            commit = selectedCommit!!,
            gitRepository = gitRepository,
            onDismiss = {
                showCommitDetail = false
                selectedCommit = null
            }
        )
    }
}

@Composable
fun GraphCanvas(
    graphData: GraphData,
    selectedCommit: Commit?,
    onCommitClick: (Commit) -> Unit
) {
    val lazyListState = rememberLazyListState()
    var scale by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 2f)
                }
            }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(graphData.commits) { index, commit ->
                val nodeData = graphData.nodePositions[commit.hash]!!

                GraphCommitRow(
                    commit = commit,
                    nodeData = nodeData,
                    connections = graphData.connections[commit.hash] ?: emptyList(),
                    isSelected = selectedCommit?.hash == commit.hash,
                    scale = scale,
                    maxLanes = graphData.maxLane,
                    onClick = { onCommitClick(commit) }
                )
            }
        }

        // Zoom controls
        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Column {
                IconButton(
                    onClick = { scale = (scale * 1.2f).coerceIn(0.5f, 2f) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                }
                IconButton(
                    onClick = { scale = (scale / 1.2f).coerceIn(0.5f, 2f) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                }
                IconButton(
                    onClick = { scale = 1f },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset zoom")
                }
            }
        }
    }
}

@Composable
fun GraphCommitRow(
    commit: Commit,
    nodeData: NodePosition,
    connections: List<Connection>,
    isSelected: Boolean,
    scale: Float,
    maxLanes: Int,
    onClick: () -> Unit
) {
    val branchColors = listOf(
        Color(0xFF4CAF50), // Green
        Color(0xFF2196F3), // Blue
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFFC107), // Amber
        Color(0xFF795548)  // Brown
    )

    val nodeColor = branchColors[nodeData.lane % branchColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((80 * scale).dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Graph visualization with connections
        Box(
            modifier = Modifier
                //.width((max(200, (nodeData.lane + 1) * 40) * scale).dp)
                .width((max(200, maxLanes * 40) * scale).dp)
                .fillMaxHeight()
                .drawBehind {
                    // Draw connections to other commits
                    connections.forEach { connection ->
                        drawConnection(
                            connection = connection,
                            nodePosition = nodeData,
                            branchColors = branchColors,
                            scale = scale
                        )
                    }
                }
        ) {
            // Commit node
            Box(
                modifier = Modifier
                    .offset(x = ((nodeData.lane * 30 + 10) * scale).dp)
                    .align(Alignment.CenterStart)
            ) {
                // Outer ring for merge commits
                if (commit.parents.size > 1) {
                    Box(
                        modifier = Modifier
                            .size((28 * scale).dp)
                            .clip(CircleShape)
                            .border(
                                width = (3 * scale).dp,
                                color = nodeColor.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }

                // Main node
                Box(
                    modifier = Modifier
                        .size((20 * scale).dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(nodeColor)
                        .border(
                            width = (2 * scale).dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            shape = CircleShape
                        )
                )
            }
        }

        // Commit information
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = (8 * scale).dp)
        ) {
            // Commit message
            Text(
                text = commit.message,
                fontSize = (14 * scale).sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height((4 * scale).dp))

            // Commit metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)
            ) {
                // Hash
                Surface(
                    shape = RoundedCornerShape((4 * scale).dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = commit.hash.take(7),
                        fontSize = (11 * scale).sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = (6 * scale).dp, vertical = (2 * scale).dp)
                    )
                }

                // Author
                Row(
                    horizontalArrangement = Arrangement.spacedBy((4 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size((12 * scale).dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = commit.author,
                        fontSize = (11 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time
                Text(
                    text = getTimeAgo(commit.timestamp),
                    fontSize = (11 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Branch/Tag badges
            if (commit.branch != null || commit.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height((4 * scale).dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)
                ) {
                    commit.branch?.let { branch ->
                        Badge(
                            text = branch,
                            icon = Icons.Default.AccountTree,
                            backgroundColor = nodeColor.copy(alpha = 0.2f),
                            textColor = nodeColor,
                            scale = scale
                        )
                    }

                    commit.tags.forEach { tag ->
                        Badge(
                            text = tag,
                            icon = Icons.Default.LocalOffer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            scale = scale
                        )
                    }
                }
            }
        }
    }
}

fun DrawScope.drawConnection(
    connection: Connection,
    nodePosition: NodePosition, // не нужен для X
    branchColors: List<Color>,
    scale: Float
) {
    val step = 30; val center = 20
    val startX = (connection.fromLane * step + center) * scale
    val endX   = (connection.toLane   * step + center) * scale
    val startY = size.height / 2f
    val endY   = when (connection.type) {
        ConnectionType.STRAIGHT -> size.height               // вертикаль в пределах строки
        ConnectionType.MERGE, ConnectionType.BRANCH -> size.height * 1.5f // до центра следующей строки
        else -> size.height
    }
    val color = branchColors[connection.fromLane % branchColors.size]

    when (connection.type) {
        ConnectionType.STRAIGHT -> drawLine(color, Offset(startX, 0f), Offset(startX, size.height), 2f * scale)
        ConnectionType.MERGE, ConnectionType.BRANCH -> {
            val p = Path().apply {
                moveTo(startX, startY)
                cubicTo(startX, startY + (endY - startY)*.3f, endX, startY + (endY - startY)*.7f, endX, endY)
            }
            drawPath(p, color, style = Stroke(width = 2f * scale, cap = StrokeCap.Round))
        }
        else -> {}
    }
}


@Composable
fun Badge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    textColor: Color,
    scale: Float
) {
    Surface(
        shape = RoundedCornerShape((4 * scale).dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (6 * scale).dp, vertical = (2 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((2 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size((10 * scale).dp),
                tint = textColor
            )
            Text(
                text = text,
                fontSize = (10 * scale).sp,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipWithMenu(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedOption != null,
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onOptionSelected(null)
                    expanded = false
                }
            )
            Divider()
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedOption == option) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(option)
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helper functions
fun filterCommits(
    commits: List<Commit>,
    branch: String?,
    author: String?,
    dateRange: Pair<Long, Long>?,
    searchQuery: String
): List<Commit> {
    return commits.filter { commit ->
        val matchesBranch = branch == null || commit.branch == branch
        val matchesAuthor = author == null || commit.author == author
        val matchesDate = dateRange == null ||
                (commit.timestamp >= dateRange.first && commit.timestamp <= dateRange.second)
        val matchesSearch = searchQuery.isEmpty() ||
                commit.message.contains(searchQuery, ignoreCase = true) ||
                commit.hash.contains(searchQuery, ignoreCase = true) ||
                commit.author.contains(searchQuery, ignoreCase = true)

        matchesBranch && matchesAuthor && matchesDate && matchesSearch
    }
}

fun hasActiveFilters(branch: String?, author: String?, dateRange: Pair<Long, Long>?): Boolean {
    return branch != null || author != null || dateRange != null
}

fun extractBranches(commits: List<Commit>): List<String> {
    return commits.mapNotNull { it.branch }.distinct().sorted()
}

fun extractAuthors(commits: List<Commit>): List<String> {
    return commits.map { it.author }.distinct().sorted()
}

fun buildGraphData(commits: List<Commit>): GraphData {
    val nodePositions = mutableMapOf<String, NodePosition>()
    val connections = mutableMapOf<String, List<Connection>>()

    // active[i] = хеш коммита, который должен встретиться ниже по этой дорожке
    val active = mutableListOf<String?>()
    var maxLanes = 0

    commits.forEachIndexed { row, c ->
        // lane для текущего коммита = где он «ожидается»
        var lane = active.indexOf(c.hash)
        if (lane == -1) {
            lane = active.indexOf(null).takeIf { it != -1 } ?: run { active.add(null); active.lastIndex }
        }
        nodePositions[c.hash] = NodePosition(lane = lane, row = row)

        val rowConns = mutableListOf<Connection>()

        // непрерывные вертикали для всех живых дорожек
        for (i in active.indices) if (active[i] != null) {
            rowConns += Connection(fromLane = i, toLane = i, type = ConnectionType.STRAIGHT)
        }

        // основной родитель идёт по той же полосе
        active[lane] = c.parents.firstOrNull()

        // дополнительные родители: ведём MERGE в их полосы
        c.parents.drop(1).forEach { p ->
            var t = active.indexOf(p)
            if (t == -1) {
                t = active.indexOf(null).takeIf { it != -1 } ?: run { active.add(null); active.lastIndex }
            }
            active[t] = p
            rowConns += Connection(fromLane = lane, toLane = t, type = ConnectionType.MERGE)
        }

        connections[c.hash] = rowConns
        maxLanes = max(maxLanes, active.size)
    }

    return GraphData(
        commits = commits,
        nodePositions = nodePositions,
        connections = connections,
        branches = extractBranches(commits),
        authors = extractAuthors(commits),
        maxLane = maxLanes
    )
}

// Data classes for graph
data class GraphData(
    val commits: List<Commit>,
    val nodePositions: Map<String, NodePosition>,
    val connections: Map<String, List<Connection>>,
    val branches: List<String>,
    val authors: List<String>,
    val maxLane: Int
)

data class NodePosition(
    val lane: Int,
    val row: Int
)

data class Connection(
    val fromLane: Int,
    val toLane: Int,
    val type: ConnectionType
)

enum class ConnectionType {
    STRAIGHT, MERGE, BRANCH, PARENT, CHILD
}

fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}