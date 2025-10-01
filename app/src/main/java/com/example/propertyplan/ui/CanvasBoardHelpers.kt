package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.ui.canvas.draw.drawDimensionsRect
import com.example.propertyplan.ui.canvas.draw.drawLabelAt
import com.example.propertyplan.ui.canvas.model.Corner
import com.example.propertyplan.ui.canvas.model.DragState
import com.example.propertyplan.ui.canvas.model.Edge
import com.example.propertyplan.ui.canvas.model.RectDraft
import com.example.propertyplan.ui.canvas.util.*
import com.example.propertyplan.ui.theme.UiColors
import com.example.propertyplan.util.*
import com.example.propertyplan.vm.PlanViewModel
import com.example.propertyplan.vm.UIMode
import kotlin.math.*

/* ---------------- gestures extracted ---------------- */

suspend fun PointerInputScope.detectRoomInteractions(
    vm: PlanViewModel,
    floor: Floor?,
    scaleRef: MutableState<Float>,
    panRef: MutableState<Offset>,
    widthPx: () -> Float,
    heightPx: () -> Float,
    draft: RectDraft?,
    dragging: DragState?,
    onUpdateDraft: (RectDraft?) -> Unit,
    onUpdateDragging: (DragState?) -> Unit
) {
    val touchSlopPx = 6f
    var draftState = draft
    var dragState = dragging
    detectDragGestures(
        onDragStart = { p ->
            val wp = toWorld(p, panRef.value, scaleRef.value)
            if (vm.mode == UIMode.Draw) {
                draftState = RectDraft(snap(wp.x, vm.snapGrid), snap(wp.y, vm.snapGrid), 0f, 0f)
                onUpdateDraft(draftState)
            } else {
                val hit = floor?.let { getRoomAt(it, wp.x, wp.y) }
                if (hit != null) {
                    vm.selectRoom(hit.id)
                    val hitRadiusPxWorld = 48f / scaleRef.value
                    dragState = hitHandle(
                        hit, wp.x, wp.y,
                        rotateRadiusPx = hitRadiusPxWorld,
                        rotateGapPx = 28f / scaleRef.value
                    ) ?: DragState.Move(hit.id, start = wp, startRect = hit.rect())
                } else {
                    vm.selectRoom(null)
                    dragState = DragState.Pan(startPan = panRef.value)
                }
                onUpdateDragging(dragState)
            }
        },
        onDrag = { change, drag ->
            if (floor == null) return@detectDragGestures
            val p = change.position
            val wp = toWorld(p, panRef.value, scaleRef.value)

            if (vm.mode == UIMode.Draw) {
                draftState?.let { d ->
                    val sx = snap(d.x, vm.snapGrid)
                    val sy = snap(d.y, vm.snapGrid)
                    val rawW = snap(wp.x, vm.snapGrid) - sx
                    val rawH = snap(wp.y, vm.snapGrid) - sy
                    draftState = d.copy(x = sx, y = sy, w = rawW, h = rawH)
                    onUpdateDraft(draftState)
                }
            } else {
                val sel = floor.rooms.find { it.id == vm.selectedRoomId }
                when (val ds = dragState) {
                    is DragState.Pan -> {
                        panRef.value = ds.startPan + drag
                    }
                    is DragState.Move -> {
                        if (sel != null) {
                            val dx = drag.x / scaleRef.value
                            val dy = drag.y / scaleRef.value
                            val snapPxWorld = 16f / scaleRef.value
                            val xs = floor.rooms.filter { it.id != sel.id }.flatMap { listOf(it.x, it.x + it.w) }
                            val ys = floor.rooms.filter { it.id != sel.id }.flatMap { listOf(it.y, it.y + it.h) }
                            vm.updateSelectedRoom { r ->
                                val rawX = if (vm.snapGrid) snap(r.x + dx, true) else r.x + dx
                                val rawY = if (vm.snapGrid) snap(r.y + dy, true) else r.y + dy
                                r.x = rawX.snapToTargets(xs, snapPxWorld)
                                r.y = rawY.snapToTargets(ys, snapPxWorld)
                            }
                        }
                    }
                    is DragState.Rotate -> {
                        val selR = sel ?: return@detectDragGestures
                        val cx = selR.x + selR.w / 2f
                        val cy = selR.y + selR.h / 2f
                        val a0 = atan2(ds.start.y - cy, ds.start.x - cx)
                        val a1 = atan2(wp.y - cy, wp.x - cx)
                        var deg = ((a1 - a0) * 180f / Math.PI.toFloat()) + ds.startDeg
                        if (vm.angleSnap) deg = round(deg / 15f) * 15f
                        vm.updateSelectedRoom { r -> r.angle = ((deg % 360f) + 360f) % 360f }
                    }
                    is DragState.Resize -> {
                        val selR = sel ?: return@detectDragGestures
                        val cx = ds.center.x
                        val cy = ds.center.y
                        val local = toLocal(wp.x, wp.y, cx, cy, selR.angle)
                        var newW = ds.startW
                        var newH = ds.startH
                        when (ds.corner) {
                            Corner.TL, Corner.BL -> newW = max(24f, (ds.startW / 2f) + (ds.startW / 2f - (local.x + ds.startW / 2f)))
                            Corner.TR, Corner.BR -> newW = max(24f, (local.x + ds.startW / 2f) + (ds.startW / 2f))
                        }
                        when (ds.corner) {
                            Corner.TL, Corner.TR -> newH = max(24f, (ds.startH / 2f) + (ds.startH / 2f - (local.y + ds.startH / 2f)))
                            Corner.BL, Corner.BR -> newH = max(24f, (local.y + ds.startH / 2f) + (ds.startH / 2f))
                        }

                        val snapPxWorld = 16f / scaleRef.value
                        val others = floor.rooms.filter { it.id != selR.id }
                        val xs = others.flatMap { listOf(it.x, it.x + it.w) }
                        val ys = others.flatMap { listOf(it.y, it.y + it.h) }

                        var left = cx - newW / 2f
                        var right = cx + newW / 2f
                        var top = cy - newH / 2f
                        var bottom = cy + newH / 2f

                        left = left.snapToTargets(xs, snapPxWorld)
                        right = right.snapToTargets(xs, snapPxWorld)
                        top = top.snapToTargets(ys, snapPxWorld)
                        bottom = bottom.snapToTargets(ys, snapPxWorld)

                        newW = max(24f, right - left)
                        newH = max(24f, bottom - top)

                        vm.updateSelectedRoom { r ->
                            r.w = newW; r.h = newH
                            r.x = (left + right) / 2f - r.w / 2f
                            r.y = (top + bottom) / 2f - r.h / 2f
                        }
                    }
                    is DragState.EdgeResize -> {
                        val selR = sel ?: return@detectDragGestures
                        val cx = ds.center.x
                        val cy = ds.center.y
                        val local = toLocal(wp.x, wp.y, cx, cy, selR.angle)
                        val minSide = 24f
                        var left = -ds.startW / 2f
                        var right =  ds.startW / 2f
                        var top = -ds.startH / 2f
                        var bottom =  ds.startH / 2f
                        when (ds.edge) {
                            Edge.LEFT ->   left   = local.x.coerceAtMost(right - minSide)
                            Edge.RIGHT ->  right  = local.x.coerceAtLeast(left + minSide)
                            Edge.TOP ->    top    = local.y.coerceAtMost(bottom - minSide)
                            Edge.BOTTOM -> bottom = local.y.coerceAtLeast(top + minSide)
                        }
                        val wLT = toWorld(left,  top,    cx, cy, selR.angle)
                        val wRT = toWorld(right, top,    cx, cy, selR.angle)
                        val wLB = toWorld(left,  bottom, cx, cy, selR.angle)
                        val wRB = toWorld(right, bottom, cx, cy, selR.angle)
                        var minX = minOf(wLT.x, wRT.x, wLB.x, wRB.x)
                        var maxX = maxOf(wLT.x, wRT.x, wLB.x, wRB.x)
                        var minY = minOf(wLT.y, wRT.y, wLB.y, wRB.y)
                        var maxY = maxOf(wLT.y, wRT.y, wLB.y, wRB.y)
                        val snapPxWorld = 16f / scaleRef.value
                        val others = floor.rooms.filter { it.id != selR.id }
                        val xs = others.flatMap { listOf(it.x, it.x + it.w) }
                        val ys = others.flatMap { listOf(it.y, it.y + it.h) }
                        minX = minX.snapToTargets(xs, snapPxWorld)
                        maxX = maxX.snapToTargets(xs, snapPxWorld)
                        minY = minY.snapToTargets(ys, snapPxWorld)
                        maxY = maxY.snapToTargets(ys, snapPxWorld)
                        val finalW = (maxX - minX).coerceAtLeast(minSide)
                        val finalH = (maxY - minY).coerceAtLeast(minSide)
                        val finalCx = (minX + maxX) / 2f
                        val finalCy = (minY + maxY) / 2f
                        vm.updateSelectedRoom { r ->
                            r.w = finalW
                            r.h = finalH
                            r.x = finalCx - r.w / 2f
                            r.y = finalCy - r.h / 2f
                        }
                    }
                    null -> {}
                }
            }

            // simple auto-scroll near edges
            val edgePx = 24f
            val speed = 10f
            var dxAuto = 0f; var dyAuto = 0f
            if (p.x < edgePx) dxAuto = speed
            if (p.x > widthPx() - edgePx) dxAuto = -speed
            if (p.y < edgePx) dyAuto = speed
            if (p.y > heightPx() - edgePx) dyAuto = -speed
            if (dxAuto != 0f || dyAuto != 0f) {
                panRef.value += Offset(dxAuto, dyAuto)
            }

            change.consume()
        },
        onDragEnd = {
            vm.onEditCommitted()
            if (floor != null && vm.mode == UIMode.Draw) {
                draftState?.let { d ->
                    val w = abs(d.w); val h = abs(d.h)
                    if (w >= 24f && h >= 24f) {
                        val x = if (d.w < 0) d.x - w else d.x
                        val y = if (d.h < 0) d.y - h else d.y
                        vm.addRoomFromTemplate("Room", w, h, false, x + w / 2f, y + h / 2f)
                    }
                }
            }
            onUpdateDraft(null)
            onUpdateDragging(null)

            // clamp pan a bit
            val p0 = panRef.value
            panRef.value = p0.copy(
                x = p0.x.coerceIn(-widthPx(), widthPx()),
                y = p0.y.coerceIn(-heightPx(), heightPx())
            )
        }
    )
}

/* ---------------- drawing helpers ---------------- */

fun DrawScope.drawAllRooms(
    floor: Floor?,
    vm: PlanViewModel,
    density: Density,
    scale: Float,
    showLabels: Boolean,
    showGuides: Boolean,
    baseHandleDp: Dp,
    hitTargetMinDp: Dp,
    rotateGapPx: Float,
    screenSnapRadiusPx: Float
) {
    val selectedId = vm.selectedRoomId
    floor?.rooms?.forEach { r ->
        val selected = r.id == selectedId
        val cx = r.x + r.w / 2f
        val cy = r.y + r.h / 2f
        withTransform({
            rotate(degrees = r.angle, pivot = Offset(cx, cy))
        }) {
            // outline only (like your original)
            drawRect(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                topLeft = Offset(r.x, r.y),
                size = androidx.compose.ui.geometry.Size(r.w, r.h),
                style = Stroke(width = if (selected) 3f / scale else 2f / scale)
            )

            if (selected) {
                val handleBase = with(density) { baseHandleDp.toPx() }
                val visualR = (handleBase / scale).coerceAtLeast(10f)
                val hitR = with(density) { hitTargetMinDp.toPx() } / scale

                // 4 corners
                val corners = listOf(
                    Offset(r.x, r.y),
                    Offset(r.x + r.w, r.y),
                    Offset(r.x, r.y + r.h),
                    Offset(r.x + r.w, r.y + r.h)
                )
                corners.forEach { corner ->
                    drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = corner)
                    drawCircle(Color.White, radius = visualR, center = corner)
                }

                // 4 mid-edges
                val mTop = Offset(r.x + r.w / 2f, r.y)
                val mBottom = Offset(r.x + r.w / 2f, r.y + r.h)
                val mLeft = Offset(r.x, r.y + r.h / 2f)
                val mRight = Offset(r.x + r.w, r.y + r.h / 2f)
                listOf(mTop, mRight, mBottom, mLeft).forEach { mpt ->
                    drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = mpt)
                    drawCircle(Color.White, radius = visualR * 0.8f, center = mpt)
                }

                // rotate handle
                val topCenter = Offset(r.x + r.w / 2f, r.y)
                val handle = Offset(topCenter.x, topCenter.y - rotateGapPx)
                drawLine(Color.White, start = topCenter, end = handle, strokeWidth = 2f / scale)
                drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = handle)
                drawCircle(Color.White, radius = visualR, center = handle)

                if (showLabels) {
                    val ppm = floor?.params?.ppm ?: 50
                    val area = (r.w * r.h) / (ppm * ppm.toFloat())
                    drawLabelAt(Offset(cx, cy), text = "${area.format1()} m²", scale = scale)
                }
            }
        }
    }
}

fun DrawScope.drawDraftRect(
    d: RectDraft,
    scale: Float,
    dash: PathEffect,
    showGuides: Boolean,
    screenSnapRadiusPx: Float
) {
    val dx = d.w; val dy = d.h
    val w = abs(dx); val h = abs(dy)
    val x = if (dx < 0) d.x - w else d.x
    val y = if (dy < 0) d.y - h else d.y

    drawRect(
        color = UiColors.State.Snap,
        topLeft = Offset(x, y),
        size = androidx.compose.ui.geometry.Size(w, h),
        style = Stroke(width = 2f / scale, pathEffect = dash)
    )
    if (showGuides) drawDimensionsRect(x, y, w, h, scale)
    val snapR = (screenSnapRadiusPx / scale).coerceAtLeast(8f)
    drawCircle(UiColors.State.Snap, radius = snapR, center = Offset(x + w, y + h))
}

fun DrawScope.drawActiveGuides(
    floor: Floor?,
    vm: PlanViewModel,
    dragging: DragState?,
    scale: Float,
    showGuides: Boolean
) {
    if (!showGuides) return
    if (dragging !is DragState.Move && dragging !is DragState.Resize && dragging !is DragState.EdgeResize) return
    val sel = floor?.rooms?.find { it.id == vm.selectedRoomId } ?: return
    drawDimensionsRect(sel.x, sel.y, sel.w, sel.h, scale)
}
