package com.gitflow.android.ui.config

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

data class GraphConfig(
    val rowHeight: Dp,
    val rowPadding: Dp,
    val laneStep: Dp,
    val nodeSize: Dp,
    val nodeCenterOffset: Dp,
    val nodeBorderWidth: Dp,
    val lineStrokeWidth: Dp,
    val infoMinWidth: Dp,
    val infoStartPadding: Dp,
    val textSpacing: Dp,
    val badgeSpacing: Dp,
    val badgeCornerRadius: Dp,
    val badgeHorizontalPadding: Dp,
    val badgeVerticalPadding: Dp,
    val badgeIconSize: Dp,
    val badgeIconSpacing: Dp
) {
    fun getGraphWidth(maxLanes: Int): Dp {
        return maxLanes * laneStep + nodeCenterOffset + nodeSize
    }

    companion object {
        val Default = GraphConfig(
            rowHeight = 64.dp,
            rowPadding = 16.dp,
            laneStep = 32.dp,
            nodeSize = 10.dp,
            nodeCenterOffset = 16.dp,
            nodeBorderWidth = 2.dp,
            lineStrokeWidth = 3.dp,
            infoMinWidth = 300.dp,
            infoStartPadding = 12.dp,
            textSpacing = 4.dp,
            badgeSpacing = 8.dp,
            badgeCornerRadius = 4.dp,
            badgeHorizontalPadding = 6.dp,
            badgeVerticalPadding = 2.dp,
            badgeIconSize = 12.dp,
            badgeIconSpacing = 4.dp
        )

        val Compact = GraphConfig(
            rowHeight = 48.dp,
            rowPadding = 12.dp,
            laneStep = 24.dp,
            nodeSize = 8.dp,
            nodeCenterOffset = 12.dp,
            nodeBorderWidth = 1.5.dp,
            lineStrokeWidth = 2.dp,
            infoMinWidth = 250.dp,
            infoStartPadding = 8.dp,
            textSpacing = 2.dp,
            badgeSpacing = 6.dp,
            badgeCornerRadius = 3.dp,
            badgeHorizontalPadding = 4.dp,
            badgeVerticalPadding = 1.dp,
            badgeIconSize = 10.dp,
            badgeIconSpacing = 3.dp
        )

        val Large = GraphConfig(
            rowHeight = 80.dp,
            rowPadding = 20.dp,
            laneStep = 40.dp,
            nodeSize = 12.dp,
            nodeCenterOffset = 20.dp,
            nodeBorderWidth = 2.5.dp,
            lineStrokeWidth = 4.dp,
            infoMinWidth = 400.dp,
            infoStartPadding = 16.dp,
            textSpacing = 6.dp,
            badgeSpacing = 10.dp,
            badgeCornerRadius = 6.dp,
            badgeHorizontalPadding = 8.dp,
            badgeVerticalPadding = 3.dp,
            badgeIconSize = 14.dp,
            badgeIconSpacing = 5.dp
        )

        val Wide = GraphConfig(
            rowHeight = 64.dp,
            rowPadding = 16.dp,
            laneStep = 48.dp,
            nodeSize = 10.dp,
            nodeCenterOffset = 24.dp,
            nodeBorderWidth = 2.dp,
            lineStrokeWidth = 3.dp,
            infoMinWidth = 350.dp,
            infoStartPadding = 12.dp,
            textSpacing = 4.dp,
            badgeSpacing = 8.dp,
            badgeCornerRadius = 4.dp,
            badgeHorizontalPadding = 6.dp,
            badgeVerticalPadding = 2.dp,
            badgeIconSize = 12.dp,
            badgeIconSpacing = 4.dp
        )
    }
}
