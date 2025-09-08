package com.gitflow.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.gitflow.android.data.models.Commit
import com.gitflow.android.data.models.Repository
import com.gitflow.android.data.repository.MockGitRepository
import kotlin.math.abs
import kotlin.math.max

/**
 * Commit-graph renderer with:
 * - dp→px inside DrawScope (точное совпадение точек и линий на любой плотности);
 * - корректное завершение дорожек без «бесконечных» вертикалей;
 * - явные FORK-кривые при старте новой ветки из родителя;
 * - кликабельные строки коммитов.
 */

@Composable
fun EnhancedGraphView(
    repository: Repository?,
    gitRepository: MockGitRepository,
    onCommitClick: (Commit) -> Unit = {}
) {
    if (repository == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a repository to view commits")
        }
        return
    }

    val commits by remember(repository.id) {
        mutableStateOf(gitRepository.getCommits(repository))
    }

    val rows by remember(commits) {
        mutableStateOf(buildGraphData(commits))
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { _, r -> r.commit.hash }) { _, row ->
            GraphListRow(row, onCommitClick)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GraphListRow(row: GraphRow, onCommitClick: (Commit) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable { onCommitClick(row.commit) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // граф
        Canvas(
            modifier = Modifier
                .width((row.laneCount * LaneStep + GraphPadding).coerceAtLeast(40.dp))
                .fillMaxHeight()
        ) {
            drawRowConnections(row)
            drawNode(row)
        }

        // инфо
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = row.commit.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${row.commit.author} • ${timeAgo(row.commit.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/* ============================ Drawing ============================ */

private fun DrawScope.drawRowConnections(row: GraphRow) {
    val stepPx = LaneStep.toPx()
    val centerPx = CenterOffset.toPx()
    val strokePx = StrokeWidth.toPx()
    val midY = size.height / 2f

    // Вертикали
    row.verticalMask.forEachIndexed { i, active ->
        if (!active) return@forEachIndexed
        val x = i * stepPx + centerPx

        // если это новый форк — не рисуем верхнюю половину новой дорожки
        val startY = if (row.forkFromLane != null && i == row.lane) midY else 0f
        drawLine(
            color = laneColor(i),
            start = Offset(x, startY),
            end = Offset(x, size.height),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    // Кривые
    row.connections.forEach { c ->
        when (c.type) {
            ConnectionType.MERGE -> {
                val x1 = c.fromLane * stepPx + centerPx
                val x2 = c.toLane * stepPx + centerPx
                val dx = abs(x2 - x1)
                val path = Path().apply {
                    moveTo(x1, midY)
                    cubicTo(
                        x1, midY + dx * 0.25f,
                        x2, size.height - dx * 0.25f,
                        x2, size.height
                    )
                }
                drawPath(
                    path = path,
                    color = laneColor(c.fromLane),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            ConnectionType.FORK -> {
                val xParent = c.fromLane * stepPx + centerPx
                val xChild = c.toLane * stepPx + centerPx
                val dx = abs(xChild - xParent)
                val path = Path().apply {
                    moveTo(xParent, 0f)
                    cubicTo(
                        xParent, dx * 0.25f,
                        xChild, midY - dx * 0.25f,
                        xChild, midY
                    )
                }
                // цвет по родителю, чтобы визуально «выйти» из его дорожки
                drawPath(
                    path = path,
                    color = laneColor(c.fromLane),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun DrawScope.drawNode(row: GraphRow) {
    val stepPx = LaneStep.toPx()
    val centerPx = CenterOffset.toPx()
    val radiusPx = NodeRadius.toPx()
    val x = row.lane * stepPx + centerPx
    val y = size.height / 2f

    drawCircle(
        color = laneColor(row.lane),
        radius = radiusPx,
        center = Offset(x, y)
    )
}

/* ============================ Graph Model ============================ */

private val LaneStep: Dp = 28.dp
private val CenterOffset: Dp = 14.dp
private val GraphPadding: Dp = 14.dp
private val RowHeight: Dp = 44.dp
private val NodeRadius: Dp = 6.dp
private val StrokeWidth: Dp = 2.dp

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

private data class GraphRow(
    val commit: Commit,
    val lane: Int,
    val laneCount: Int,
    val verticalMask: List<Boolean>,   // какие дорожки рисуют вертикаль в этом ряду
    val connections: List<Connection>, // MERGE/FORK кривые
    val forkFromLane: Int?             // если старт новой ветки — из какой дорожки
)

private data class Connection(
    val fromLane: Int,
    val toLane: Int,
    val type: ConnectionType
)

private enum class ConnectionType { MERGE, FORK }

/**
 * Построение дорожек:
 * - удаляет дубликаты одного и того же hash в active;
 * - завершает пустые «хвосты»;
 * - ставит FORK при появлении нового lane от родителя.
 */
private fun buildGraphData(commits: List<Commit>): List<GraphRow> {
    if (commits.isEmpty()) return emptyList()

    val rows = mutableListOf<GraphRow>()
    val active = mutableListOf<String?>() // lane -> hash, который «ждём» ниже

    for (c in commits) {
        // lane для текущего коммита
        var lane = active.indexOf(c.hash)
        val laneWasNew = lane == -1
        if (laneWasNew) {
            lane = active.indexOf(null)
            if (lane == -1) {
                lane = active.size
                active += null
            }
        }

        // удалить дубликаты того же hash
        for (i in active.indices) if (i != lane && active[i] == c.hash) active[i] = null

        val activeBefore = active.toList()
        val connections = mutableListOf<Connection>()

        // основной родитель
        val mainParent = c.parents.firstOrNull()
        val parentLane = if (mainParent != null) activeBefore.indexOf(mainParent) else -1

        // если lane новый и есть родитель, то это FORK из parentLane -> lane
        val forkFromLane = if (laneWasNew && parentLane != -1) {
            connections += Connection(fromLane = parentLane, toLane = lane, type = ConnectionType.FORK)
            parentLane
        } else null

        // продолжение основной линии или конец
        if (mainParent != null) {
            active[lane] = mainParent
        } else {
            active[lane] = null
        }

        // дополнительные родители — MERGE из текущего lane в их lane к низу
        for (p in c.parents.drop(1)) {
            var t = active.indexOf(p)
            if (t == -1) {
                t = active.indexOf(null)
                if (t == -1) {
                    t = active.size
                    active += null
                }
            }
            connections += Connection(fromLane = lane, toLane = t, type = ConnectionType.MERGE)
            active[t] = p
        }

        // обрезка пустых хвостов
        while (active.isNotEmpty() && active.last() == null) active.removeLast()

        // вертикальные отрезки для этого ряда
        val verticalMask = MutableList(max(activeBefore.size, active.size)) { i ->
            // любая активная дорожка сверху рисует вертикаль
            val wasActive = i < activeBefore.size && activeBefore[i] != null
            // текущая дорожка продолжится вниз, если есть родитель
            val currentContinues = (i == lane && mainParent != null)
            wasActive || currentContinues
        }

        val laneCount = verticalMask.size.coerceAtLeast(lane + 1)
        rows += GraphRow(
            commit = c,
            lane = lane,
            laneCount = laneCount,
            verticalMask = verticalMask,
            connections = connections,
            forkFromLane = forkFromLane
        )
    }

    return rows
}

/* ============================ Utils ============================ */

fun timeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000L}d ago"
        else -> java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
