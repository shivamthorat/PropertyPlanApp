package com.example.propertyplan.ui.canvas.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.propertyplan.model.ItemType
import com.example.propertyplan.model.PlacedItem

fun DrawScope.drawItem(item: PlacedItem, scale: Float) {
    rotate(item.rotation, pivot = Offset(item.x, item.y)) {
        val topLeft = Offset(item.x - item.sizeW / 2f, item.y - item.sizeH / 2f)
        val size = Size(item.sizeW, item.sizeH)
        val color = when (item.type) {
            ItemType.DOOR   -> Color(0xFF90CAF9)
            ItemType.WINDOW -> Color(0xFF4DD0E1)
            ItemType.STAIRS -> Color(0xFFA5D6A7)
        }
        drawRect(color.copy(alpha = 0.85f), topLeft = topLeft, size = size)
        if (item.type == ItemType.STAIRS) {
            val steps = (item.steps ?: 12).coerceAtLeast(2)
            val stepGap = item.sizeH / steps
            repeat(steps) { i ->
                val y = topLeft.y + i * stepGap
                drawLine(Color.White, Offset(topLeft.x, y), Offset(topLeft.x + item.sizeW, y), strokeWidth = 1f / scale)
            }
        }
    }
}
