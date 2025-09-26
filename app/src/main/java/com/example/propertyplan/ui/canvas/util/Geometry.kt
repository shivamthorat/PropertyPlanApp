package com.example.propertyplan.ui.canvas.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.propertyplan.model.Floor
import com.example.propertyplan.model.Room
import com.example.propertyplan.util.insideLocalRect
import com.example.propertyplan.util.toLocal
import kotlin.math.*

// ---------- Coordinate helpers ----------

/** Screen → World: converts a screen point to world coordinates using current pan/scale. */
fun toWorld(p: Offset, pan: Offset, scale: Float): Offset =
    Offset((p.x - pan.x) / scale, (p.y - pan.y) / scale)

/** World → Screen: converts a world point to screen coordinates using current pan/scale. */
fun toScreen(w: Offset, pan: Offset, scale: Float): Offset =
    Offset(w.x * scale + pan.x, w.y * scale + pan.y)

/**
 * Local (unrotated, centered at [cx, cy]) → World.
 * Local (lx, ly) is in the rect’s local space with the rect center at (0,0) before rotation.
 * Rotate by [deg] around center then translate to (cx, cy).
 */
fun toWorld(lx: Float, ly: Float, cx: Float, cy: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    val cosA = cos(a).toFloat()
    val sinA = sin(a).toFloat()
    val wx = cx + (lx * cosA - ly * sinA)
    val wy = cy + (lx * sinA + ly * cosA)
    return Offset(wx, wy)
}

// ---------- Room & bounds ----------

fun Room.rect(): Rect = Rect(x, y, x + w, y + h)

fun roomsBounds(rooms: List<Room>): Rect? {
    if (rooms.isEmpty()) return null
    var l = Float.POSITIVE_INFINITY
    var t = Float.POSITIVE_INFINITY
    var r = Float.NEGATIVE_INFINITY
    var b = Float.NEGATIVE_INFINITY
    rooms.forEach { rm ->
        l = min(l, rm.x)
        t = min(t, rm.y)
        r = max(r, rm.x + rm.w)
        b = max(b, rm.y + rm.h)
    }
    return Rect(l, t, r, b)
}

// ---------- Zoom-to-fit ----------

fun computeZoomToFit(
    floor: Floor?,
    canvasW: Float,
    canvasH: Float,
    paddingPx: Float
): Pair<Float, Offset>? {
    val bounds = floor?.rooms?.let { roomsBounds(it) } ?: return null
    if (bounds.width <= 0f || bounds.height <= 0f) return null
    val availW = canvasW - paddingPx * 2
    val availH = canvasH - paddingPx * 2
    val sx = availW / bounds.width
    val sy = availH / bounds.height
    val s = (min(sx, sy)).coerceIn(MIN_SCALE, MAX_SCALE)
    val worldCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
    val screenCenter = Offset(canvasW / 2f, canvasH / 2f)
    val pan = screenCenter - worldCenter * s
    return s to pan
}

// ---------- Rect mapping utilities ----------

fun mapRect(world: Rect, content: Rect, fit: FitRect): Rect {
    val sX = fit.scaleX
    val sY = fit.scaleY
    val x = fit.dest.left + (world.left - content.left) * sX
    val y = fit.dest.top + (world.top - content.top) * sY
    val w = world.width * sX
    val h = world.height * sY
    return Rect(x, y, x + w, y + h)
}

fun unmapPoint(p: Offset, content: Rect, fit: FitRect): Offset {
    val sX = fit.scaleX
    val sY = fit.scaleY
    val wx = content.left + (p.x - fit.dest.left) / sX
    val wy = content.top + (p.y - fit.dest.top) / sY
    return Offset(wx, wy)
}

data class FitRect(val dest: Rect, val scaleX: Float, val scaleY: Float)

fun fitRect(content: Rect, container: Rect): FitRect {
    val sX = container.width / content.width
    val sY = container.height / content.height
    val s = min(sX, sY)
    val w = content.width * s
    val h = content.height * s
    val dx = container.left + (container.width - w) / 2f
    val dy = container.top + (container.height - h) / 2f
    return FitRect(Rect(dx, dy, dx + w, dy + h), s, s)
}

// ---------- Picking ----------

fun getRoomAt(floor: Floor, x: Float, y: Float): Room? {
    for (i in floor.rooms.size - 1 downTo 0) {
        val r = floor.rooms[i]
        val cx = r.x + r.w / 2f
        val cy = r.y + r.h / 2f
        val local = toLocal(x, y, cx, cy, r.angle)
        if (insideLocalRect(local.x, local.y, r.w, r.h)) return r
    }
    return null
}

// ---------- Misc ----------

fun ensureOpaque(rgbOrArgb: Int): Int {
    val a = (rgbOrArgb ushr 24) and 0xFF
    return if (a == 0) rgbOrArgb or 0xFF000000.toInt() else rgbOrArgb
}

fun distance(a: Offset, b: Offset) = hypot((a.x - b.x), (a.y - b.y))

fun snap(v: Float, on: Boolean): Float =
    if (on) (round(v / GRID) * GRID) else v

/** Snap a value to the nearest item in [targets] if within [radius] (world px). */
// ui/canvas/util/… (this file)
fun Float.snapToClosestWithin(targets: List<Float>, radius: Float): Float {
    if (targets.isEmpty()) return this
    var best = this
    var bestDelta = Float.POSITIVE_INFINITY
    for (t in targets) {
        val d = kotlin.math.abs(this - t)
        if (d < bestDelta) {
            bestDelta = d
            best = t
        }
    }
    return if (bestDelta <= radius) best else this
}

