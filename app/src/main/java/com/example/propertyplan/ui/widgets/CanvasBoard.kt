package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.model.Room
import com.example.propertyplan.ui.UiColors
import com.example.propertyplan.util.insideLocalRect
import com.example.propertyplan.util.toLocal
import com.example.propertyplan.vm.PlanViewModel
import com.example.propertyplan.vm.UIMode
import kotlin.math.*

private const val GRID = 20f
private const val MIN_SCALE = 0.45f
private const val MAX_SCALE = 3.0f

@Composable
fun CanvasBoard(
    vm: PlanViewModel,
    floor: Floor?,
    onCanvasSize: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current

    // canvas size cache
    var widthPx by remember { mutableStateOf(1080f) }
    var heightPx by remember { mutableStateOf(1920f) }
    LaunchedEffect(widthPx, heightPx) { onCanvasSize(widthPx, heightPx) }

    // Zoom & pan (use primitive states for perf)
    var scale by remember { mutableFloatStateOf(if (cfg.screenWidthDp <= 411) 0.8f else 1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Local feature toggles (no VM changes)
    var showGuides by remember { mutableStateOf(true) }
    var showLabels by remember { mutableStateOf(true) }

    // Draft & drag state
    var draft by remember { mutableStateOf<RectDraft?>(null) }
    var dragging by remember { mutableStateOf<DragState?>(null) }

    // Touch sizes (scale-aware)
    val baseHandleDp: Dp = 18.dp                    // visual dot
    val hitTargetMinDp: Dp = 48.dp                  // finger-friendly hit target
    val rotateGapPx = with(density) { 28.dp.toPx() }
    val screenSnapRadiusPx = with(density) { 16.dp.toPx() } // snap radius in *screen* space
    val touchSlopPx = with(density) { 6.dp.toPx() }         // jitter guard

    // Keep latest mutable values inside pointer scopes without restarting them
    val scaleRef = remember { mutableStateOf(scale) }
    val panRef = remember { mutableStateOf(pan) }
    LaunchedEffect(scale) { scaleRef.value = scale }
    LaunchedEffect(pan) { panRef.value = pan }

    // dashed stroke cache
    val dash = remember(scale) { PathEffect.dashPathEffect(floatArrayOf(12f, 12f)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBarStatus(
            scale = scale,
            ppm = floor?.params?.ppm ?: 50,
            angleSnap = vm.angleSnap,
            gridSnap = vm.snapGrid,
            selection = floor?.rooms?.find { it.id == vm.selectedRoomId }?.name,
            onZoomToFit = {
                val fit = computeZoomToFit(floor, widthPx, heightPx, paddingPx = with(density){ 48.dp.toPx() })
                if (fit != null) {
                    scale = fit.first; scaleRef.value = fit.first
                    pan = fit.second; panRef.value = fit.second
                }
            },
            showGrid = vm.showGrid,
            toggleGrid = { vm.toggleGrid() },
            showGuides = showGuides,
            toggleGuides = { showGuides = !showGuides },
            showLabels = showLabels,
            toggleLabels = { showLabels = !showLabels }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // ===== MAIN CANVAS =====
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UiColors.BgCanvasOrDefault())
                    // Double-tap: zoom to tap position
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                val newScale = (scaleRef.value * 1.35f).coerceAtMost(MAX_SCALE)
                                val worldBefore = toWorld(tap, panRef.value, scaleRef.value)
                                scale = newScale
                                scaleRef.value = newScale
                                val newPan = tap - worldBefore * newScale
                                pan = newPan
                                panRef.value = newPan
                            }
                        )
                    }
                    // Two-finger pan/zoom
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
                    // One-finger draw/select/drag/rotate/resize + empty-space pan
                    .pointerInput(vm.mode, vm.snapGrid, vm.selectedRoomId) {
                        var movedEnough = false
                        detectDragGestures(
                            onDragStart = { p ->
                                movedEnough = false
                                widthPx = size.width.toFloat()
                                heightPx = size.height.toFloat()
                                val wp = toWorld(p, panRef.value, scaleRef.value)
                                if (vm.mode == UIMode.Draw) {
                                    draft = RectDraft(
                                        snap(wp.x, vm.snapGrid),
                                        snap(wp.y, vm.snapGrid),
                                        0f, 0f
                                    )
                                } else {
                                    val hit = floor?.let { getRoomAt(it, wp.x, wp.y) }
                                    if (hit != null) {
                                        vm.selectRoom(hit.id)
                                        // hit radius: large, finger-friendly, scale-aware
                                        val hitRadiusPx = with(density) { hitTargetMinDp.toPx() } / scaleRef.value
                                        dragging = hitHandle(
                                            hit, wp.x, wp.y,
                                            rotateRadiusPx = hitRadiusPx,
                                            rotateGapPx = rotateGapPx
                                        ) ?: DragState.Move(hit.id, start = wp, startRect = hit.rect())
                                    } else {
                                        vm.selectRoom(null)
                                        dragging = DragState.Pan(startPan = panRef.value) // single-finger pan
                                    }
                                }
                            },
                            onDrag = { change, drag ->
                                if (floor == null) return@detectDragGestures
                                val p = change.position
                                val wp = toWorld(p, panRef.value, scaleRef.value)

                                // jitter guard
                                if (!movedEnough && hypot(drag.x, drag.y) > touchSlopPx) movedEnough = true
                                if (!movedEnough) {
                                    change.consume()
                                    return@detectDragGestures
                                }

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
                                            panRef.value = ds.startPan + drag // screen space
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
                                            if (sel != null) {
                                                val cx = sel.x + sel.w / 2f
                                                val cy = sel.y + sel.h / 2f
                                                val a0 = atan2(ds.start.y - cy, ds.start.x - cx)
                                                val a1 = atan2(wp.y - cy, wp.x - cx)
                                                var deg = ((a1 - a0) * 180f / Math.PI.toFloat()) + ds.startDeg
                                                if (vm.angleSnap) deg = round(deg / 15f) * 15f
                                                vm.updateSelectedRoom { r -> r.angle = ((deg % 360f) + 360f) % 360f }
                                            }
                                        }
                                        is DragState.Resize -> {
                                            if (sel != null) {
                                                val cx = ds.center.x
                                                val cy = ds.center.y
                                                val local = toLocal(wp.x, wp.y, cx, cy, sel.angle)
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

                                                // snap edges to neighbors
                                                val snapPxWorld = (screenSnapRadiusPx / scaleRef.value)
                                                val others = floor.rooms.filter { it.id != sel.id }
                                                val xs = others.flatMap { listOf(it.x, it.x + it.w) }
                                                val ys = others.flatMap { listOf(it.y, it.y + it.h) }

                                                // propose rect centered at cx,cy
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
                                        }
                                        null -> {}
                                    }
                                }

                                // edge auto-scroll while dragging
                                val edgePx = with(density) { 24.dp.toPx() }
                                val speed = with(density) { 10.dp.toPx() } // gentle
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
                                // AUTOSAVE HOOK
                                vm.onEditCommitted()

                                if (floor == null) return@detectDragGestures
                                if (vm.mode == UIMode.Draw) {
                                    draft?.let { d ->
                                        val w = abs(d.w); val h = abs(d.h)
                                        if (w >= 24f && h >= 24f) {
                                            val x = if (d.w < 0) d.x - w else d.x
                                            val y = if (d.h < 0) d.y - h else d.y
                                            vm.addRoomFromTemplate(
                                                "Room", w, h, false,
                                                x + w / 2f, y + h / 2f
                                            )
                                        }
                                    }
                                    draft = null
                                }
                                dragging = null
                                // gentle pan clamp so content doesn’t fly off-screen
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
                // cache
                widthPx = size.width
                heightPx = size.height

                // world transform
                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale)
                }) {
                    // ===== Grid (only visible part; heavy 5th line)
                    if (vm.showGrid) {
                        drawGridWith5thMajor(pan, scale)
                    }

                    // ===== Rooms
                    val selectedId = vm.selectedRoomId
                    floor?.rooms?.forEach { r ->
                        val selected = r.id == selectedId
                        val c = Color(ensureOpaque(r.color))
                        val cx = r.x + r.w / 2f
                        val cy = r.y + r.h / 2f

                        withTransform({
                            rotate(degrees = r.angle, pivot = Offset(cx, cy))
                        }) {
                            // fill
                            drawRect(
                                color = c.copy(alpha = 0.78f),
                                topLeft = Offset(r.x, r.y),
                                size = androidx.compose.ui.geometry.Size(r.w, r.h)
                            )
                            // border
                            drawRect(
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                                topLeft = Offset(r.x, r.y),
                                size = androidx.compose.ui.geometry.Size(r.w, r.h),
                                style = Stroke(width = if (selected) 3f / scale else 2f / scale)
                            )

                            // handles (big hit rings + visual dots) when selected
                            if (selected) {
                                val handleBase = with(density) { baseHandleDp.toPx() }
                                val visualR = (handleBase / scale).coerceAtLeast(10f)
                                val hitR = with(density) { hitTargetMinDp.toPx() } / scale
                                val corners = listOf(
                                    Offset(r.x, r.y),
                                    Offset(r.x + r.w, r.y),
                                    Offset(r.x, r.y + r.h),
                                    Offset(r.x + r.w, r.y + r.h)
                                )
                                corners.forEach { corner ->
                                    drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = corner) // invisible hit blob
                                    drawCircle(Color.White, radius = visualR, center = corner)                     // visible dot
                                }
                                // rotate handle
                                val topCenter = Offset(r.x + r.w / 2f, r.y)
                                val handle = Offset(topCenter.x, topCenter.y - rotateGapPx)
                                drawLine(Color.White, start = topCenter, end = handle, strokeWidth = 2f / scale)
                                drawCircle(Color.White.copy(alpha = 0.0001f), radius = hitR, center = handle)
                                drawCircle(Color.White, radius = visualR, center = handle)

                                // label: area (when selected)
                                if (showLabels) {
                                    val area = (r.w * r.h) / (floor?.params?.ppm?.let { it * it } ?: 1).toFloat()
                                    drawLabelAt(Offset(cx, cy), text = "${area.format1()} m²", scale = scale)
                                }
                            }
                        }
                    }

                    // ===== Draft rect + dimensions & snap glyph
                    draft?.let { d ->
                        val dx = d.w; val dy = d.h
                        val w = abs(dx); val h = abs(dy)
                        val x = if (dx < 0) d.x - w else d.x
                        val y = if (dy < 0) d.y - h else d.y

                        // shape
                        drawRect(
                            color = if (vm.snapGrid) UiColors.SnapOrDefault() else Color.White,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            style = Stroke(width = 2f / scale, pathEffect = dash)
                        )
                        // dimensions
                        if (showGuides) drawDimensionsRect(x, y, w, h, scale)

                        // snap glyph at last corner (screen-space radius)
                        val snapR = (screenSnapRadiusPx / scale).coerceAtLeast(8f)
                        drawCircle(UiColors.SnapOrDefault(), radius = snapR, center = Offset(x + w, y + h))
                    }

                    // Live dimensions while moving/resizing (peek info)
                    if (dragging is DragState.Move || dragging is DragState.Resize) {
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

                // ===== Screen-space overlays =====
                if (showGuides) {
                    // scale bar (bottom-left)
                    drawScaleBar(
                        ppm = floor?.params?.ppm ?: 50,
                        scale = scale,
                        canvasWidth = size.width,
                        canvasHeight = size.height
                    )
                }
            }

            // ===== Mini-map =====
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

            // ===== Zoom controls =====
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

/* ===== Top bar / status ===== */

@Composable
private fun TopBarStatus(
    scale: Float,
    ppm: Int,
    angleSnap: Boolean,
    gridSnap: Boolean,
    selection: String?,
    onZoomToFit: () -> Unit,
    showGrid: Boolean,
    toggleGrid: () -> Unit,
    showGuides: Boolean,
    toggleGuides: () -> Unit,
    showLabels: Boolean,
    toggleLabels: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Zoom ${(scale * 100).toInt()}%")
            Text("${ppm} px/m")
            AssistChip(onClick = onZoomToFit, label = { Text("Fit") })
            FilterChip(selected = showGrid, onClick = toggleGrid, label = { Text("Grid") })
            FilterChip(selected = showGuides, onClick = toggleGuides, label = { Text("Guides") })
            FilterChip(selected = showLabels, onClick = toggleLabels, label = { Text("Labels") })
            // selection summary last so it can scroll out first if overflow
            Text(selection?.let { "Selected: $it" } ?: "No selection")
            // angle/grid snap summary (compact)
            Text(if (angleSnap) "∠Snap 15°" else "∠Free")
            Text(if (gridSnap) "Grid Snap" else "Free Move")
        }
    }
}


/* ===== Zoom buttons ===== */

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End
    ) {
        ElevatedButton(onClick = onZoomIn) { Text("+") }
        ElevatedButton(onClick = onZoomOut) { Text("–") }
        OutlinedButton(onClick = onReset) { Text("Reset") }
    }
}

/* ===== Mini-map ===== */

@Composable
private fun MiniMap(
    modifier: Modifier,
    floor: Floor?,
    pan: Offset,
    scale: Float,
    widthPx: Float,
    heightPx: Float,
    onJump: (Offset) -> Unit
) {
    if (floor == null || floor.rooms.isEmpty()) return
    val worldBounds = remember(floor.rooms) { roomsBounds(floor.rooms) } ?: return

    val mapW = 160f
    val mapH = 120f
    val pad = 8f

    Canvas(
        modifier = modifier
            .size((mapW + pad * 2).dp, (mapH + pad * 2).dp)
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    // convert tap -> world point
                    val fit = fitRect(
                        content = worldBounds,
                        container = Rect(pad, pad, pad + mapW, pad + mapH)
                    )
                    val worldTap = unmapPoint(tap, worldBounds, fit)
                    val screenCenter = Offset(widthPx / 2f, heightPx / 2f)
                    val newPan = screenCenter - worldTap * scale
                    onJump(newPan)
                }
            }
    ) {
        val fit = fitRect(
            content = worldBounds,
            container = Rect(pad, pad, pad + mapW, pad + mapH)
        )
        // draw rooms
        floor.rooms.forEach { r ->
            val rc = mapRect(r.rect(), worldBounds, fit)
            drawRect(Color(0xFF90CAF9), topLeft = rc.topLeft, size = rc.size)
        }
        // draw viewport box
        val topLeftWorld = toWorld(Offset.Zero, pan, scale)
        val bottomRightWorld = toWorld(Offset(widthPx, heightPx), pan, scale)
        val viewRectWorld = Rect(
            topLeftWorld.x, topLeftWorld.y,
            bottomRightWorld.x, bottomRightWorld.y
        )
        val viewRc = mapRect(viewRectWorld, worldBounds, fit)
        drawRect(Color.White, topLeft = viewRc.topLeft, size = viewRc.size, style = Stroke(2f))
    }
}


/* ===== Drawing helpers (grid, labels, dimensions, scale bar) ===== */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridWith5thMajor(pan: Offset, scale: Float) {
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
            color = if (isMajor) UiColors.GridHeavyOrDefault() else UiColors.GridOrDefault(),
            start = Offset(x, worldTop - GRID * 2),
            end = Offset(x, worldBottom + GRID * 2),
            strokeWidth = if (isMajor) strokeMajor else stroke
        )
    }
    for (gy in startGY..endGY) {
        val y = gy * GRID
        val isMajor = gy % 5 == 0
        drawLine(
            color = if (isMajor) UiColors.GridHeavyOrDefault() else UiColors.GridOrDefault(),
            start = Offset(worldLeft - GRID * 2, y),
            end = Offset(worldRight + GRID * 2, y),
            strokeWidth = if (isMajor) strokeMajor else stroke
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabelAt(
    center: Offset,
    text: String,
    scale: Float
) {
    val pad = 4f / scale
    val bg = Color(0xAA000000)
    val w = (text.length * 7f) / scale + pad * 2
    val h = 16f / scale + pad * 2
    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
    drawRect(bg, topLeft = tl, size = androidx.compose.ui.geometry.Size(w, h))
    drawContext.canvas.nativeCanvas.apply {
        val p = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (12f / scale)
            isAntiAlias = true
        }
        drawText(text, tl.x + pad, tl.y + h - pad * 1.2f, p)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDimensionsRect(
    x: Float, y: Float, w: Float, h: Float, scale: Float
) {
    val midTop = Offset(x + w / 2f, y - 8f / scale)
    val midLeft = Offset(x - 8f / scale, y + h / 2f)
    drawLabelAt(midTop, "${w.format0()} px", scale)
    drawLabelAt(midLeft, "${h.format0()} px", scale)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScaleBar(
    ppm: Int,
    scale: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    // target ~100px bar in screen space
    val targetPx = 100f
    val worldLenPx = targetPx / scale // world pixels (pre-scale)
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

    // label (switch to cm if < 1m)
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

/* ===== Geometry & mapping utils ===== */

private fun toWorld(p: Offset, pan: Offset, scale: Float): Offset =
    Offset((p.x - pan.x) / scale, (p.y - pan.y) / scale)

private fun Room.rect() = Rect(x, y, x + w, y + h)

private fun roomsBounds(rooms: List<Room>): Rect? {
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

private fun computeZoomToFit(
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

private fun mapRect(world: Rect, content: Rect, fit: FitRect): Rect {
    val sx = fit.scaleX
    val sy = fit.scaleY
    val x = fit.dest.left + (world.left - content.left) * sx
    val y = fit.dest.top + (world.top - content.top) * sy
    val w = world.width * sx
    val h = world.height * sy
    return Rect(x, y, x + w, y + h)
}

private fun unmapPoint(p: Offset, content: Rect, fit: FitRect): Offset {
    val sx = fit.scaleX
    val sy = fit.scaleY
    val wx = content.left + (p.x - fit.dest.left) / sx
    val wy = content.top + (p.y - fit.dest.top) / sy
    return Offset(wx, wy)
}

private data class FitRect(val dest: Rect, val scaleX: Float, val scaleY: Float)
private fun fitRect(content: Rect, container: Rect): FitRect {
    val sx = container.width / content.width
    val sy = container.height / content.height
    val s = min(sx, sy)
    val w = content.width * s
    val h = content.height * s
    val dx = container.left + (container.width - w) / 2f
    val dy = container.top + (container.height - h) / 2f
    return FitRect(Rect(dx, dy, dx + w, dy + h), s, s)
}

private fun getRoomAt(floor: Floor, x: Float, y: Float): Room? {
    for (i in floor.rooms.size - 1 downTo 0) {
        val r = floor.rooms[i]
        val cx = r.x + r.w / 2f
        val cy = r.y + r.h / 2f
        val local = toLocal(x, y, cx, cy, r.angle)
        if (insideLocalRect(local.x, local.y, r.w, r.h)) return r
    }
    return null
}

private fun hitHandle(
    r: Room,
    x: Float,
    y: Float,
    rotateRadiusPx: Float, // used as generic hit radius for all handles
    rotateGapPx: Float
): DragState? {
    val cx = r.x + r.w / 2f
    val cy = r.y + r.h / 2f
    val local = toLocal(x, y, cx, cy, r.angle)
    val corners = mapOf(
        Corner.TL to Offset(-r.w/2f, -r.h/2f),
        Corner.TR to Offset( r.w/2f, -r.h/2f),
        Corner.BL to Offset(-r.w/2f,  r.h/2f),
        Corner.BR to Offset( r.w/2f,  r.h/2f)
    )
    val rot = Offset(0f, -r.h/2f - rotateGapPx)
    if (distance(local, rot) <= rotateRadiusPx) {
        return DragState.Rotate(r.id, start = Offset(x, y), startDeg = r.angle)
    }
    for ((corner, pos) in corners) {
        if (distance(local, pos) <= rotateRadiusPx) {
            return DragState.Resize(r.id, corner, center = Offset(cx, cy), startW = r.w, startH = r.h)
        }
    }
    return null
}

/* ===== Misc utils & models inside file to keep it drop-in ===== */

private fun ensureOpaque(rgbOrArgb: Int): Int {
    val a = (rgbOrArgb ushr 24) and 0xFF
    return if (a == 0) rgbOrArgb or 0xFF000000.toInt() else rgbOrArgb
}
private fun distance(a: Offset, b: Offset) = hypot((a.x - b.x), (a.y - b.y))
private fun snap(v: Float, on: Boolean): Float = if (on) (kotlin.math.round(v / GRID) * GRID) else v

private data class RectDraft(val x: Float, val y: Float, val w: Float, val h: Float)

private sealed class DragState {
    data class Pan(val startPan: Offset) : DragState()
    data class Move(val id: String, val start: Offset, val startRect: Rect): DragState()
    data class Rotate(val id: String, val start: Offset, val startDeg: Float): DragState()
    data class Resize(val id: String, val corner: Corner, val center: Offset, val startW: Float, val startH: Float): DragState()
}
private enum class Corner { TL, TR, BL, BR }

/* ===== Formatting, snapping helpers & nice numbers ===== */

private fun Float.format0(): String = String.format("%.0f", this)
private fun Float.format1(): String = String.format("%.1f", this)

private fun Float.snapToTargets(targets: List<Float>, threshold: Float): Float {
    var out = this
    for (t in targets) {
        if (abs(this - t) <= threshold) { out = t; break }
    }
    return out
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

/* ===== Small extension fallbacks for colors ===== */

private fun UiColors.BgCanvasOrDefault(): Color =
    try { UiColors.BgCanvas } catch (_: Throwable) { Color(0xFF0E0F12) }

private fun UiColors.GridOrDefault(): Color =
    try { UiColors.Grid } catch (_: Throwable) { Color(0x22FFFFFF) }

private fun UiColors.GridHeavyOrDefault(): Color =
    try { UiColors.GridHeavy } catch (_: Throwable) { Color(0x44FFFFFF) }

private fun UiColors.SnapOrDefault(): Color =
    try { UiColors.Snap } catch (_: Throwable) { Color(0xFF00E5FF) }
