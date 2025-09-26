package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.ui.canvas.draw.drawDimensionsRect
import com.example.propertyplan.ui.canvas.draw.drawGridWith5thMajor
import com.example.propertyplan.ui.canvas.draw.drawLabelAt
import com.example.propertyplan.ui.canvas.draw.drawScaleBar
import com.example.propertyplan.ui.canvas.model.Corner
import com.example.propertyplan.ui.canvas.model.DragState
import com.example.propertyplan.ui.canvas.model.Edge
import com.example.propertyplan.ui.canvas.model.RectDraft
import com.example.propertyplan.ui.canvas.util.*
import com.example.propertyplan.ui.theme.UiColors
import com.example.propertyplan.util.*
import com.example.propertyplan.vm.PlanViewModel
import com.example.propertyplan.vm.UIMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round

@Composable
fun CanvasBoard(
    vm: PlanViewModel,
    floor: Floor?,
    onCanvasSize: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current

    var widthPx by remember { mutableStateOf(1080f) }
    var heightPx by remember { mutableStateOf(1920f) }
    LaunchedEffect(widthPx, heightPx) { onCanvasSize(widthPx, heightPx) }

    var scale by remember { mutableFloatStateOf(if (cfg.screenWidthDp <= 411) 0.8f else 1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    var showGuides by remember { mutableStateOf(true) }
    var showLabels by remember { mutableStateOf(true) }

    var draft by remember { mutableStateOf<RectDraft?>(null) }
    var dragging by remember { mutableStateOf<DragState?>(null) }

    val baseHandleDp: Dp = 18.dp
    val hitTargetMinDp: Dp = 48.dp
    val rotateGapPx = with(density) { 28.dp.toPx() }          // screen px
    val screenSnapRadiusPx = with(density) { 16.dp.toPx() }   // screen px
    val touchSlopPx = with(density) { 6.dp.toPx() }

    val scaleRef = remember { mutableStateOf(scale) }
    val panRef = remember { mutableStateOf(pan) }
    LaunchedEffect(scale) { scaleRef.value = scale }
    LaunchedEffect(pan) { panRef.value = pan }

    val dash = remember(scale) { PathEffect.dashPathEffect(floatArrayOf(12f, 12f)) }

    Column(Modifier.fillMaxSize()) {
        TopBarStatus(
            scale = scale,
            ppm = floor?.params?.ppm ?: 50,
            angleSnap = vm.angleSnap,
            gridSnap = vm.snapGrid,
            selection = floor?.rooms?.find { it.id == vm.selectedRoomId }?.name,
            onZoomToFit = {
                computeZoomToFit(floor, widthPx, heightPx, with(density){ 48.dp.toPx() })?.let { (s, p) ->
                    scale = s; scaleRef.value = s
                    pan = p; panRef.value = p
                }
            },
            showGrid = vm.showGrid,
            toggleGrid = vm::toggleGrid,
            showGuides = showGuides,
            toggleGuides = { showGuides = !showGuides },
            showLabels = showLabels,
            toggleLabels = { showLabels = !showLabels }
        )

        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiColors.Background.Canvas)
                    // NEW: Intercept press/long-press so OS/Studio inspector won't trigger
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Consumes the "down" immediately.
                                tryAwaitRelease()
                            },
                            onLongPress = { /* no-op: consume long-press */ },
                            onDoubleTap = { tap ->
                                val newScale = (scaleRef.value * 1.35f).coerceAtMost(MAX_SCALE)
                                val worldBefore = toWorld(tap, panRef.value, scaleRef.value)
                                scale = newScale; scaleRef.value = newScale
                                val newPan = tap - worldBefore * newScale
                                pan = newPan; panRef.value = newPan
                            }
                        )
                    }
                    .pointerInput(vm.mode, vm.snapGrid, vm.selectedRoomId) {
                        detectTransformGestures(panZoomLock = true) { centroid, panChange, zoom, _ ->
                            val oldScale = scaleRef.value
                            val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            val worldAtCentroid = toWorld(centroid, panRef.value, oldScale)
                            val newPan = centroid - worldAtCentroid * newScale
                            panRef.value = panRef.value + panChange
                            panRef.value = newPan + (panRef.value - newPan)
                            pan = panRef.value
                            scaleRef.value = newScale
                            scale = newScale
                        }
                    }
                    .pointerInput(vm.mode, vm.snapGrid, vm.selectedRoomId) {
                        var movedEnough = false
                        detectDragGestures(
                            onDragStart = { p ->
                                movedEnough = false
                                widthPx = size.width.toFloat()
                                heightPx = size.height.toFloat()
                                val wp = toWorld(p, panRef.value, scaleRef.value)
                                if (vm.mode == UIMode.Draw) {
                                    draft = RectDraft(snap(wp.x, vm.snapGrid), snap(wp.y, vm.snapGrid), 0f, 0f)
                                } else {
                                    val hit = floor?.let { getRoomAt(it, wp.x, wp.y) }
                                    if (hit != null) {
                                        vm.selectRoom(hit.id)
                                        val hitRadiusPxWorld = with(density) { hitTargetMinDp.toPx() } / scaleRef.value
                                        dragging = hitHandle(
                                            hit, wp.x, wp.y,
                                            rotateRadiusPx = hitRadiusPxWorld,
                                            rotateGapPx = rotateGapPx / scaleRef.value   // convert to world px
                                        ) ?: DragState.Move(hit.id, start = wp, startRect = hit.rect())
                                    } else {
                                        vm.selectRoom(null)
                                        dragging = DragState.Pan(startPan = panRef.value)
                                    }
                                }
                            },
                            onDrag = { change, drag ->
                                if (floor == null) return@detectDragGestures
                                val p = change.position
                                val wp = toWorld(p, panRef.value, scaleRef.value)

                                if (!movedEnough && hypot(drag.x, drag.y) > touchSlopPx) movedEnough = true
                                if (!movedEnough) { change.consume(); return@detectDragGestures }

                                if (vm.mode == UIMode.Draw) {
                                    draft?.let { d ->
                                        val sx = snap(d.x, vm.snapGrid)
                                        val sy = snap(d.y, vm.snapGrid)
                                        val rawW = snap(wp.x, vm.snapGrid) - sx
                                        val rawH = snap(wp.y, vm.snapGrid) - sy
                                        draft = d.copy(x = sx, y = sy, w = rawW, h = rawH)
                                    }
                                } else {
                                    val sel = floor.rooms.find { it.id == vm.selectedRoomId }
                                    when (val ds = dragging) {
                                        is DragState.Pan -> {
                                            panRef.value = ds.startPan + drag
                                            pan = panRef.value
                                        }
                                        is DragState.Move -> {
                                            if (sel != null) {
                                                val dx = drag.x / scaleRef.value
                                                val dy = drag.y / scaleRef.value
                                                val snapPxWorld = (screenSnapRadiusPx / scaleRef.value)
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

                                            val snapPxWorld = (screenSnapRadiusPx / scaleRef.value)
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

                                            val snapPxWorld = (screenSnapRadiusPx / scaleRef.value)
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

                                // edge auto-scroll
                                val edgePx = with(density) { 24.dp.toPx() }
                                val speed = with(density) { 10.dp.toPx() }
                                var dxAuto = 0f; var dyAuto = 0f
                                if (p.x < edgePx) dxAuto = speed
                                if (p.x > widthPx - edgePx) dxAuto = -speed
                                if (p.y < edgePx) dyAuto = speed
                                if (p.y > heightPx - edgePx) dyAuto = -speed
                                if (dxAuto != 0f || dyAuto != 0f) {
                                    panRef.value += Offset(dxAuto, dyAuto)
                                    pan = panRef.value
                                }

                                change.consume()
                            },
                            onDragEnd = {
                                vm.onEditCommitted()

                                if (floor == null) return@detectDragGestures
                                if (vm.mode == UIMode.Draw) {
                                    draft?.let { d ->
                                        val w = abs(d.w); val h = abs(d.h)
                                        if (w >= 24f && h >= 24f) {
                                            val x = if (d.w < 0) d.x - w else d.x
                                            val y = if (d.h < 0) d.y - h else d.y
                                            vm.addRoomFromTemplate("Room", w, h, false, x + w / 2f, y + h / 2f)
                                        }
                                    }
                                    draft = null
                                }
                                dragging = null
                                val p0 = panRef.value
                                panRef.value = p0.copy(
                                    x = p0.x.coerceIn(-widthPx, widthPx),
                                    y = p0.y.coerceIn(-heightPx, heightPx)
                                )
                                pan = panRef.value
                            }
                        )
                    }
            ) {
                widthPx = size.width
                heightPx = size.height

                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale)
                }) {
                    if (vm.showGrid) drawGridWith5thMajor(pan, scale)

                    val selectedId = vm.selectedRoomId
                    floor?.rooms?.forEach { r ->
                        val selected = r.id == selectedId
                        val c = Color(ensureOpaque(r.color))
                        val cx = r.x + r.w / 2f
                        val cy = r.y + r.h / 2f

                        withTransform({
                            rotate(degrees = r.angle, pivot = Offset(cx, cy))
                        }) {
                            // Outline only
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
                                    Offset(r.x, r.y),                         // TL
                                    Offset(r.x + r.w, r.y),                   // TR
                                    Offset(r.x, r.y + r.h),                   // BL
                                    Offset(r.x + r.w, r.y + r.h)              // BR
                                )
                                corners.forEach { corner ->
                                    drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = corner)
                                    drawCircle(Color.White, radius = visualR, center = corner)
                                }

                                // 4 edges (midpoints)
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
                                    val area = (r.w * r.h) / (floor?.params?.ppm?.let { it * it } ?: 1).toFloat()
                                    drawLabelAt(Offset(cx, cy), text = "${area.format1()} m²", scale = scale)
                                }
                            }
                        }
                    }

                    draft?.let { d ->
                        val dx = d.w; val dy = d.h
                        val w = kotlin.math.abs(dx); val h = kotlin.math.abs(dy)
                        val x = if (dx < 0) d.x - w else d.x
                        val y = if (dy < 0) d.y - h else d.y

                        drawRect(
                            color = if (vm.snapGrid) UiColors.State.Snap else Color.White,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            style = Stroke(width = 2f / scale, pathEffect = dash)
                        )
                        if (showGuides) drawDimensionsRect(x, y, w, h, scale)
                        val snapR = (screenSnapRadiusPx / scale).coerceAtLeast(8f)
                        drawCircle(UiColors.State.Snap, radius = snapR, center = Offset(x + w, y + h))
                    }

                    if (dragging is DragState.Move || dragging is DragState.Resize || dragging is DragState.EdgeResize) {
                        val sel = floor?.rooms?.find { it.id == vm.selectedRoomId }
                        sel?.let { r ->
                            val cx = r.x + r.w / 2f
                            val cy = r.y + r.h / 2f
                            val ppm = floor?.params?.ppm ?: 50
                            val area = (r.w * r.h) / (ppm * ppm.toFloat())
                            drawLabelAt(Offset(cx, cy), "${area.format1()} m²", scale)
                            if (showGuides) drawDimensionsRect(r.x, r.y, r.w, r.h, scale)
                        }
                    }
                }

                if (showGuides) {
                    drawScaleBar(
                        ppm = floor?.params?.ppm ?: 50,
                        scale = scale,
                        canvasWidth = size.width,
                        canvasHeight = size.height
                    )
                }
            }

            MiniMap(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                floor = floor,
                pan = pan,
                scale = scale,
                widthPx = widthPx,
                heightPx = heightPx,
                onJump = { newPan -> pan = newPan; panRef.value = newPan }
            )

            ZoomControls(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                onZoomIn = {
                    val center = Offset(widthPx / 2f, heightPx / 2f)
                    val world = toWorld(center, pan, scale)
                    val newS = (scale * 1.2f).coerceAtMost(MAX_SCALE)
                    scale = newS; scaleRef.value = newS
                    val newPan = center - world * newS
                    pan = newPan; panRef.value = newPan
                },
                onZoomOut = {
                    val center = Offset(widthPx / 2f, heightPx / 2f)
                    val world = toWorld(center, pan, scale)
                    val newS = (scale / 1.2f).coerceAtLeast(MIN_SCALE)
                    scale = newS; scaleRef.value = newS
                    val newPan = center - world * newS
                    pan = newPan; panRef.value = newPan
                },
                onReset = { scale = 1f; pan = Offset.Zero; scaleRef.value = 1f; panRef.value = Offset.Zero }
            )
        }
    }
}
