package com.example.propertyplan.ui.canvas.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.propertyplan.ui.canvas.util.GRID
import com.example.propertyplan.ui.theme.UiColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

fun DrawScope.drawGridWith5thMajor(pan: Offset, scale: Float) {
    val worldLeft = -pan.x / scale
    val worldTop = -pan.y / scale
    val worldRight = worldLeft + size.width / scale
    val worldBottom = worldTop + size.height / scale

    val startGX = floor(worldLeft / GRID).toInt() - 1
    val endGX = ceil(worldRight / GRID).toInt() + 1
    val startGY = floor(worldTop / GRID).toInt() - 1
    val endGY = ceil(worldBottom / GRID).toInt() + 1

    val stroke = max(0.5f, 1f / scale)
    val strokeMajor = max(0.5f, 1.6f / scale)

    for (gx in startGX..endGX) {
        val x = gx * GRID
        val isMajor = gx % 5 == 0
        drawLine(
            color = if (isMajor) UiColors.Grid.Heavy else UiColors.Grid.Light,
            start = Offset(x, worldTop - GRID * 2),
            end = Offset(x, worldBottom + GRID * 2),
            strokeWidth = if (isMajor) strokeMajor else stroke
        )
    }
    for (gy in startGY..endGY) {
        val y = gy * GRID
        val isMajor = gy % 5 == 0
        drawLine(
            color = if (isMajor) UiColors.Grid.Heavy else UiColors.Grid.Light,
            start = Offset(worldLeft - GRID * 2, y),
            end = Offset(worldRight + GRID * 2, y),
            strokeWidth = if (isMajor) strokeMajor else stroke
        )
    }
}


