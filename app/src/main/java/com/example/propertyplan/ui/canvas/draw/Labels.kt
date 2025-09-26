package com.example.propertyplan.ui.canvas.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size

fun DrawScope.drawLabelAt(center: Offset, text: String, scale: Float) {
    val pad = 4f / scale
    val bg = Color(0xAA000000)
    val w = (text.length * 7f) / scale + pad * 2
    val h = 16f / scale + pad * 2
    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
    drawRect(bg, topLeft = tl, size = Size(w, h))
    drawContext.canvas.nativeCanvas.apply {
        val p = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (12f / scale)
            isAntiAlias = true
        }
        drawText(text, tl.x + pad, tl.y + h - pad * 1.2f, p)
    }
}

fun DrawScope.drawDimensionsRect(
    x: Float, y: Float, w: Float, h: Float, scale: Float
) {
    val midTop = Offset(x + w / 2f, y - 8f / scale)
    val midLeft = Offset(x - 8f / scale, y + h / 2f)
    drawLabelAt(midTop, "${w.format0()} px", scale)
    drawLabelAt(midLeft, "${h.format0()} px", scale)
}

private fun Float.format0(): String = String.format("%.0f", this)
