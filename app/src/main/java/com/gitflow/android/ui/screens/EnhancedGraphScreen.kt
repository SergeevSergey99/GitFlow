package com.gitflow.android.ui.screens

import androidx.compose.foundation.Canvas
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
 * Minimal, correct commit-graph renderer.
 * Fixes:
 * 1) All geometry in dp -> px inside DrawScope, so lines align with nodes on any density.
 * 2) Graph builder de-duplicates active lanes for the same hash and stops lanes at the last commit.
 */

@Composable
fun EnhancedGraphView(
    repository: Repository?,
    gitRepository: MockGitRepository
) {
    if (repository == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a repository to view commits")
        }
        return
    }

    // Source data
    val commits by remember(repository.id) {
        mutableStateOf(gitRepository.getCommits(repository))
    }

    // Build rows for graph
    val rows by remember(commits) {
        mutableStateOf(buildGraphData(commits))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(rows, key = { _, r -> r.commit.hash }) { _, row ->
            GraphListRow(row)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GraphListRow(row: GraphRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Graph column
        Canvas(
            modifier = Modifier
                .width((row.laneCount * LaneStep + GraphPadding).coerceAtLeast(40.dp))
                .fillMaxHeight()
        ) {
            drawRowConnections(row)
            drawNode(row)
        }

        // Commit info
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

    // Vertical lines for all active lanes in this row.
    row.verticalMask.forEachIndexed { i, active ->
        if (!active) return@forEachIndexed
        val x = i * stepPx + centerPx
        drawLine(
            color = laneColor(i),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    // Merge curves from current lane to target lanes
    row.connections.forEach { c ->
        when (c.type) {
            ConnectionType.MERGE -> {
                val x1 = c.fromLane * stepPx + centerPx
                val x2 = c.toLane * stepPx + centerPx
                val path = Path().apply {
                    moveTo(x1, midY)
                    // Smooth S-curve to bottom at target lane
                    val dx = abs(x2 - x1)
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
            ConnectionType.STRAIGHT -> {
                // Not used explicitly. Vertical lines handled above.
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
    // Stable palette cycling by lane index.
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
    val verticalMask: List<Boolean>,         // lanes with vertical line for this row
    val connections: List<Connection>        // merges starting at this row
)

private data class Connection(
    val fromLane: Int,
    val toLane: Int,
    val type: ConnectionType
)

private enum class ConnectionType { STRAIGHT, MERGE }

/**
 * Build a lane assignment and connection list per row.
 * - Uses only parent links.
 * - Removes duplicate occurrences of the same hash in active lanes.
 * - Stops lanes at the last commit.
 */
private fun buildGraphData(commits: List<Commit>): List<GraphRow> {
    if (commits.isEmpty()) return emptyList()

    val rows = mutableListOf<GraphRow>()
    val active = mutableListOf<String?>() // lane -> hash we are "waiting for" below

    for (c in commits) {
        // 1) Find or allocate lane for current commit
        var lane = active.indexOf(c.hash)
        if (lane == -1) {
            lane = active.indexOf(null)
            if (lane == -1) {
                lane = active.size
                active += null
            }
        }

        // 2) Remove any duplicates of the same hash in other lanes
        for (i in active.indices) {
            if (i != lane && active[i] == c.hash) active[i] = null
        }

        // Snapshot BEFORE updating with parents. This drives vertical lines in this row.
        val activeBefore = active.toList()

        // 3) Build connections for merges and update active "waiting" hashes
        val connections = mutableListOf<Connection>()

        if (c.parents.isNotEmpty()) {
            // Main parent continues straight down from current lane
            active[lane] = c.parents.first()
        } else {
            // No parents: current lane ends here
            active[lane] = null
        }

        // Other parents: MERGE arcs into their lanes
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

        // 4) Trim trailing null lanes to avoid "infinite" tails
        while (active.isNotEmpty() && active.last() == null) active.removeLast()

        // Compute which lanes draw a vertical segment across this row:
        // - any lane that was active before, or
        // - the current lane if it has at least one parent (continues downward)
        val verticalMask = MutableList(max(activeBefore.size, active.size)) { i ->
            (i < activeBefore.size && activeBefore[i] != null) || (i == lane && c.parents.isNotEmpty())
        }

        val laneCount = verticalMask.size.coerceAtLeast(lane + 1)
        rows += GraphRow(
            commit = c,
            lane = lane,
            laneCount = laneCount,
            verticalMask = verticalMask,
            connections = connections
        )
    }

    return rows
}

/* ============================ Utils ============================ */

public fun timeAgo(timestamp: Long): String {
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
