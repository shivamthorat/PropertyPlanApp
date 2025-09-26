package com.example.propertyplan.ui.canvas.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun DrawScope.drawScaleBar(
    ppm: Int,
    scale: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val targetPx = 100f
    val worldLenPx = targetPx / scale
    val meters = worldLenPx / ppm
    val nice = niceMeters(meters)
    val barWorldPx = nice * ppm
    val barScreenPx = barWorldPx * scale

    val margin = 12f
    val x0 = margin
    val y0 = canvasHeight - margin - 8f
    drawLine(Color.White, Offset(x0, y0), Offset(x0 + barScreenPx, y0), strokeWidth = 3f)
    drawLine(Color.White, Offset(x0, y0 - 6f), Offset(x0, y0 + 6f), strokeWidth = 3f)
    drawLine(Color.White, Offset(x0 + barScreenPx, y0 - 6f), Offset(x0 + barScreenPx, y0 + 6f), strokeWidth = 3f)

    val label = if (nice >= 1f) "${nice.format1()} m" else "${(nice * 100f).format0()} cm"
    drawContext.canvas.nativeCanvas.apply {
        val p = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12f
            isAntiAlias = true
        }
        drawText(label, x0, y0 - 10f, p)
    }
}

private fun niceMeters(m: Float): Float {
    if (m <= 0f) return 0.1f
    val pow = floor(log10(m.toDouble())).toInt()
    val base = 10.0.pow(pow.toDouble()).toFloat()
    val n = m / base
    val step = when {
        n < 1.5f -> 1f
        n < 3.5f -> 2f
        n < 7.5f -> 5f
        else -> 10f
    }
    return step * base
}

private fun Float.format0(): String = String.format("%.0f", this)
private fun Float.format1(): String = String.format("%.1f", this)
