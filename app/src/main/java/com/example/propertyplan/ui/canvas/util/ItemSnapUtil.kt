package com.example.propertyplan.ui.canvas.util

import com.example.propertyplan.model.Floor
import com.example.propertyplan.model.PlacedItem
import kotlin.math.abs
import kotlin.math.min
data class EdgeSnap(val x: Float, val y: Float, val angleDeg: Float)
private fun snapToRectEdge(
    x: Float, y: Float,
    rx: Float, ry: Float, rw: Float, rh: Float
): EdgeSnap {
    val left   = abs(x - rx)
    val right  = abs(x - (rx + rw))
    val top    = abs(y - ry)
    val bottom = abs(y - (ry + rh))
    val md = min(min(left, right), min(top, bottom))
    return when (md) {
        left   -> EdgeSnap(rx, (y).coerceIn(ry, ry + rh), -90f)
        right  -> EdgeSnap(rx + rw, (y).coerceIn(ry, ry + rh),  90f)
        top    -> EdgeSnap((x).coerceIn(rx, rx + rw), ry,        0f)
        else   -> EdgeSnap((x).coerceIn(rx, rx + rw), ry + rh, 180f)
    }
}

/** Closest room edge across all rooms. */
fun snapToNearestRoomEdge(floor: Floor, x: Float, y: Float): EdgeSnap? {
    if (floor.rooms.isEmpty()) return null
    var best: EdgeSnap? = null
    var bestD = Float.POSITIVE_INFINITY
    for (r in floor.rooms) {
        val e = snapToRectEdge(x, y, r.x, r.y, r.w, r.h)
        val d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y)
        if (d < bestD) { bestD = d; best = e }
    }
    return best
}

fun PlacedItem.applyEdgeSnap(s: EdgeSnap) {
    x = s.x
    y = s.y
    var a = s.angleDeg % 360f
    if (a < 0) a += 360f
    rotation = a // ✅ use rotation (not angle)
}
