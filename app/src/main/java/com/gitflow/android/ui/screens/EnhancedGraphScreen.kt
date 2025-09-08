package com.gitflow.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.MockGitRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/** Минимальный и рабочий экран графа. Исправления:
 * 1) Клики по коммиту открывают диалог деталей.
 * 2) Ветка не «появляется из пустоты»: рисуется BRANCH от родителя к первому коммиту ветки,
 *    а в строке коммита показывается бейдж «from <hash> • <branch>».
 * 3) Вся геометрия в dp→px внутри DrawScope, линии совпадают с точками на любой плотности.
 */

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
    var showCommitDetail by remember { mutableStateOf(false) }

    val graphData = remember(commits) { buildGraphData(commits) }

    Box(Modifier.fillMaxSize()) {
        GraphCanvas(
            graphData = graphData,
            onCommitClick = { commit ->
                selectedCommit = commit
                showCommitDetail = true
            }
        )
    }

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

/* ============================ Canvas / Rows ============================ */

@Composable
private fun GraphCanvas(
    graphData: GraphData,
    onCommitClick: (Commit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
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
                onClick = { onCommitClick(commit) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GraphCommitRow(
    commit: Commit,
    nodeData: NodePosition,
    connections: List<Connection>,
    forkInfo: ForkInfo?,
    maxLanes: Int,
    onClick: () -> Unit
) {
    val nodeColor = laneColor(nodeData.lane)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая колонка с графом
        Box(
            modifier = Modifier
                .width((maxOf(60, (maxLanes + 1) * LaneStepDp.value.toInt())).dp)
                .fillMaxHeight()
                .drawBehind {
                    // Все соединения для текущей строки
                    connections.forEach { connection ->
                        drawConnection(
                            connection = connection,
                            colorFromLane = laneColor(connection.fromLane)
                        )
                    }
                }
        ) {
            // Узел
            Box(
                modifier = Modifier
                    .offset(x = (nodeData.lane * LaneStepDp + NodeCenterOffsetDp))
                    .align(Alignment.CenterStart)
            ) {
                // Кольцо для merge-коммитов
                /*if (commit.parents.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(3.dp, nodeColor.copy(alpha = 0.5f), CircleShape)
                    )
                }*/
                // Основная точка
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = (-10).dp, y = 0.dp)
                        .clip(CircleShape)
                        .background(nodeColor)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }

        // Правая информационная часть
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = commit.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // hash
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        commit.hash.take(7),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // время
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(timeAgo(commit.timestamp), fontSize = 11.sp)
                    }
                }

                // бейдж ветки
                commit.branch?.let { b ->
                    Badge(
                        text = b,
                        icon = Icons.Default.AccountTree,
                        background = nodeColor.copy(alpha = 0.15f),
                        foreground = nodeColor
                    )
                }

                // бейджи тегов
                commit.tags.forEach { t ->
                    Badge(
                        text = t,
                        icon = Icons.Default.LocalOffer,
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                        foreground = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                // «из какой ветки/коммита» если это старт новой ветки
                if (forkInfo != null) {
                    Badge(
                        text = "from ${forkInfo.parentHash.take(7)}" +
                                (forkInfo.parentBranch?.let { " • $it" } ?: ""),
                        icon = Icons.Default.AccountTree,
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        foreground = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ============================ Drawing ============================ */

private val LaneStepDp: Dp = 24.dp
private val NodeCenterOffsetDp: Dp = 16.dp
private val RowHeight: Dp = 56.dp

private fun DrawScope.drawConnection(
    connection: Connection,
    colorFromLane: Color
) {
    val stepPx = LaneStepDp.toPx()
    val centerPx = NodeCenterOffsetDp.toPx()
    val strokePx = 2.dp.toPx()

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
                val dx = abs(x2 - x1)
                cubicTo(
                    x1, dy * 0.4f,
                    x2, dy - dx * 0.2f,
                    x2, yMid
                )
            }
            // цвет по родителю
            drawPath(path, laneColor(connection.fromLane), style = Stroke(width = strokePx, cap = StrokeCap.Round))
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

private enum class ConnectionType { STRAIGHT, MERGE, BRANCH }

private data class ForkInfo(
    val parentHash: String,
    val parentLane: Int,
    val parentBranch: String?
)

private fun buildGraphData(commits: List<Commit>): GraphData {
    if (commits.isEmpty()) {
        return GraphData(emptyList(), emptyMap(), emptyMap(), emptyMap(), 0)
    }

    val byHash = commits.associateBy { it.hash }
    val nodePositions = mutableMapOf<String, NodePosition>()
    val connections = mutableMapOf<String, MutableList<Connection>>()
    val forkFrom = mutableMapOf<String, ForkInfo>()

    // Активные линии: индекс -> hash коммита, который продолжается в этой линии
    val activeLanes = mutableListOf<String?>()
    var maxLanes = 0

    // Инициализация: первый коммит всегда в lane 0
    if (commits.isNotEmpty()) {
        activeLanes.add(commits.first().hash)
    }

    commits.forEachIndexed { row, commit ->
        val list = mutableListOf<Connection>()
        
        // Найти lane для текущего коммита
        var lane = activeLanes.indexOf(commit.hash)
        var isNewBranch = false
        
        if (lane == -1) {
            // Коммит не найден в активных линиях - это новая ветка
            lane = activeLanes.indexOf(null)
            if (lane == -1) {
                lane = activeLanes.size
                activeLanes.add(null)
            }
            isNewBranch = true
        }

        // Сохранить состояние до изменений
        val lanesBefore = activeLanes.toList()
        
        // Рисуем продолжающиеся линии
        for (i in lanesBefore.indices) {
            if (lanesBefore[i] != null && i != lane) {
                list.add(Connection(fromLane = i, toLane = i, type = ConnectionType.STRAIGHT))
            }
        }
        
        val mainParent = commit.parents.firstOrNull()
        
        // Если это новая ветка, находим откуда она ответвляется
        if (isNewBranch && mainParent != null) {
            val parentLane = lanesBefore.indexOf(mainParent)
            if (parentLane != -1) {
                // Рисуем ветвление
                list.add(Connection(fromLane = parentLane, toLane = lane, type = ConnectionType.BRANCH))
                val parentBranch = byHash[mainParent]?.branch
                forkFrom[commit.hash] = ForkInfo(
                    parentHash = mainParent, 
                    parentLane = parentLane, 
                    parentBranch = parentBranch
                )
            }
        }

        // Убираем текущий коммит из активных линий
        activeLanes[lane] = null
        
        // Добавляем основного родителя
        if (mainParent != null && byHash.containsKey(mainParent)) {
            activeLanes[lane] = mainParent
        }
        
        // Обработка merge коммитов
        commit.parents.drop(1).forEach { parentHash ->
            if (byHash.containsKey(parentHash)) {
                var parentLane = activeLanes.indexOf(parentHash)
                if (parentLane == -1) {
                    parentLane = activeLanes.indexOf(null)
                    if (parentLane == -1) {
                        parentLane = activeLanes.size
                        activeLanes.add(null)
                    }
                    activeLanes[parentLane] = parentHash
                }
                list.add(Connection(fromLane = lane, toLane = parentLane, type = ConnectionType.MERGE))
            }
        }

        // Убираем пустые линии справа
        while (activeLanes.isNotEmpty() && activeLanes.last() == null) {
            activeLanes.removeLast()
        }

        nodePositions[commit.hash] = NodePosition(lane = lane, row = row)
        connections[commit.hash] = list
        maxLanes = maxOf(maxLanes, activeLanes.size)
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
    foreground: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(icon, null, Modifier.size(12.dp), tint = foreground)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 11.sp, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
