package com.example.propertyplan.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.example.propertyplan.model.Room
import com.example.propertyplan.ui.canvas.model.Corner
import com.example.propertyplan.ui.canvas.model.DragState
import com.example.propertyplan.ui.canvas.model.Edge
import com.example.propertyplan.util.toLocal
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Returns a DragState (Rotate / Resize) if the pointer is within any handle's
 * hit radius, otherwise null. Uses rotateRadiusPx as a generic hit radius for
 * both rotate and resize handles (already scale-aware at call site).
 */
fun hitHandle(
    room: Room,
    wpX: Float,
    wpY: Float,
    rotateRadiusPx: Float,
    rotateGapPx: Float,
    hitEdgeHalfThicknessPx: Float = 14f,
    handleVisualR: Float = 10f
): DragState? {
    val cx = room.x + room.w / 2f
    val cy = room.y + room.h / 2f

    // world -> local (unrotate around center)
    val local = toLocal(wpX, wpY, cx, cy, room.angle)

    val left = room.x
    val right = room.x + room.w
    val top = room.y
    val bottom = room.y + room.h

    val pTL = Offset(left, top)
    val pTR = Offset(right, top)
    val pBL = Offset(left, bottom)
    val pBR = Offset(right, bottom)

    fun near(pt: Offset): Boolean = hypot(wpX - pt.x, wpY - pt.y) <= (handleVisualR * 1.6f)

    // 1) Rotate handle (above top-center)
    run {
        val topCenter = Offset((left + right) / 2f, top)
        val rotateCenter = Offset(topCenter.x, topCenter.y - rotateGapPx)
        if (hypot(wpX - rotateCenter.x, wpY - rotateCenter.y) <= rotateRadiusPx) {
            return DragState.Rotate(room.id, start = Offset(wpX, wpY), startDeg = room.angle)
        }
    }

    // 2) Corners
    if (near(pTL)) return DragState.Resize(room.id, Corner.TL, Offset(cx, cy), room.w, room.h)
    if (near(pTR)) return DragState.Resize(room.id, Corner.TR, Offset(cx, cy), room.w, room.h)
    if (near(pBL)) return DragState.Resize(room.id, Corner.BL, Offset(cx, cy), room.w, room.h)
    if (near(pBR)) return DragState.Resize(room.id, Corner.BR, Offset(cx, cy), room.w, room.h)

    // 3) Edges in LOCAL coords (unrotated)
    val halfW = room.w / 2f
    val halfH = room.h / 2f
    val xL = -halfW; val xR = halfW; val yT = -halfH; val yB = halfH

    val withinY = local.y >= yT - hitEdgeHalfThicknessPx && local.y <= yB + hitEdgeHalfThicknessPx
    val withinX = local.x >= xL - hitEdgeHalfThicknessPx && local.x <= xR + hitEdgeHalfThicknessPx
    val nearLeft = abs(local.x - xL) <= hitEdgeHalfThicknessPx && withinY
    val nearRight = abs(local.x - xR) <= hitEdgeHalfThicknessPx && withinY
    val nearTop = abs(local.y - yT) <= hitEdgeHalfThicknessPx && withinX
    val nearBottom = abs(local.y - yB) <= hitEdgeHalfThicknessPx && withinX

    if (nearLeft) return DragState.EdgeResize(room.id, Edge.LEFT, Offset(cx, cy), room.w, room.h)
    if (nearRight) return DragState.EdgeResize(room.id, Edge.RIGHT, Offset(cx, cy), room.w, room.h)
    if (nearTop) return DragState.EdgeResize(room.id, Edge.TOP, Offset(cx, cy), room.w, room.h)
    if (nearBottom) return DragState.EdgeResize(room.id, Edge.BOTTOM, Offset(cx, cy), room.w, room.h)

    return null
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)
