package com.gitflow.android.ui.config

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

/**
 * Конфигурация для настройки внешнего вида git-графа
 */
data class GraphConfig(
    // Размеры узлов
    val nodeSize: Dp = 20.dp,
    val nodeBorderWidth: Dp = 2.dp,

    // Расстояния между элементами
    val laneStep: Dp = 24.dp,
    val nodeCenterOffset: Dp = 16.dp,
    val rowHeight: Dp = 56.dp,
    val rowPadding: Dp = 6.dp,

    // Линии соединений
    val lineStrokeWidth: Dp = 2.dp,

    // Информационная часть
    val infoMinWidth: Dp = 300.dp,
    val infoStartPadding: Dp = 8.dp,
    val badgeSpacing: Dp = 8.dp,
    val textSpacing: Dp = 2.dp,

    // Бейджи
    val badgeCornerRadius: Dp = 4.dp,
    val badgeHorizontalPadding: Dp = 6.dp,
    val badgeVerticalPadding: Dp = 2.dp,
    val badgeIconSize: Dp = 12.dp,
    val badgeIconSpacing: Dp = 4.dp,

    // Минимальная ширина области графа
    val graphMinWidth: Dp = 60.dp
) {
    companion object {
        /**
         * Конфигурация по умолчанию
         */
        val Default = GraphConfig()

        /**
         * Компактная конфигурация для небольших экранов
         */
        val Compact = GraphConfig(
            nodeSize = 16.dp,
            nodeBorderWidth = 1.5.dp,
            laneStep = 20.dp,
            nodeCenterOffset = 14.dp,
            rowHeight = 48.dp,
            rowPadding = 4.dp,
            lineStrokeWidth = 1.5.dp,
            infoMinWidth = 250.dp,
            infoStartPadding = 6.dp,
            badgeSpacing = 6.dp
        )

        /**
         * Крупная конфигурация для больших экранов
         */
        val Large = GraphConfig(
            nodeSize = 24.dp,
            nodeBorderWidth = 3.dp,
            laneStep = 28.dp,
            nodeCenterOffset = 18.dp,
            rowHeight = 64.dp,
            rowPadding = 8.dp,
            lineStrokeWidth = 2.5.dp,
            infoMinWidth = 350.dp,
            infoStartPadding = 10.dp,
            badgeSpacing = 10.dp
        )

        /**
         * Конфигурация для широких графов с большим количеством веток
         */
        val Wide = GraphConfig(
            nodeSize = 18.dp,
            nodeBorderWidth = 2.dp,
            laneStep = 20.dp,
            nodeCenterOffset = 14.dp,
            rowHeight = 52.dp,
            rowPadding = 6.dp,
            lineStrokeWidth = 1.5.dp,
            infoMinWidth = 400.dp,
            infoStartPadding = 8.dp,
            badgeSpacing = 8.dp
        )
    }

    /**
     * Вычисляет ширину области графа в зависимости от количества полос
     */
    fun getGraphWidth(maxLanes: Int): Dp {
        return maxOf(graphMinWidth, (maxLanes + 1) * laneStep + nodeCenterOffset)
    }
}
