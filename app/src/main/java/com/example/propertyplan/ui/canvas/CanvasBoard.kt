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
import com.example.propertyplan.model.ItemType
import com.example.propertyplan.model.PlacedItem
import com.example.propertyplan.ui.canvas.draw.drawDimensionsRect
import com.example.propertyplan.ui.canvas.draw.drawGridWith5thMajor
import com.example.propertyplan.ui.canvas.draw.drawItem
import com.example.propertyplan.ui.canvas.draw.drawLabelAt
import com.example.propertyplan.ui.canvas.draw.drawScaleBar
import com.example.propertyplan.ui.canvas.model.*
import com.example.propertyplan.ui.canvas.util.*
import com.example.propertyplan.ui.theme.UiColors
import com.example.propertyplan.util.*
import com.example.propertyplan.vm.PlanViewModel
import com.example.propertyplan.vm.UIMode
import kotlin.math.*

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
    val rotateGapPx = with(density) { 28.dp.toPx() }
    val screenSnapRadiusPx = with(density) { 16.dp.toPx() }
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
                    // Tap for placing doors/windows/stairs
                    .pointerInput(vm.mode, floor?.id) {
                        detectTapGestures(onTap = { tap ->
                            val world = toWorld(tap, panRef.value, scaleRef.value)
                            val f = floor ?: return@detectTapGestures
                            when (vm.mode) {
                                UIMode.PlaceDoor -> {
                                    val item = vm.addDoorAt(f, world.x, world.y)
                                    snapToNearestRoomEdge(f, world.x, world.y)?.let { s ->
                                        item.applyEdgeSnap(s)
                                    }
                                    vm.onEditCommitted()
                                }
                                UIMode.PlaceWindow -> {
                                    val item = vm.addWindowAt(f, world.x, world.y)
                                    snapToNearestRoomEdge(f, world.x, world.y)?.let { s ->
                                        item.applyEdgeSnap(s)
                                    }
                                    vm.onEditCommitted()
                                }
                                UIMode.PlaceStairs -> {
                                    vm.addStairsAt(f, world.x, world.y)
                                    vm.onEditCommitted()
                                }
                                else -> Unit
                            }
                        })
                    }
                    // Pan / zoom gestures
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
                    // Room drawing, move, resize...
                    .pointerInput(vm.mode, vm.snapGrid, vm.selectedRoomId) {
                        detectRoomInteractions(
                            vm = vm,
                            floor = floor,
                            scaleRef = scaleRef,
                            panRef = panRef,
                            widthPx = { widthPx },
                            heightPx = { heightPx },
                            draft = draft,
                            dragging = dragging,
                            onUpdateDraft = { draft = it },
                            onUpdateDragging = { dragging = it }
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

                    // Draw rooms
                    drawAllRooms(floor, vm, density, scale, showLabels, showGuides, baseHandleDp, hitTargetMinDp, rotateGapPx, screenSnapRadiusPx)

                    // Draw items (doors/windows/stairs)
                    floor?.items?.forEach { item ->
                        drawItem(item, scale)
                    }

                    // Draw draft rectangle
                    draft?.let { d -> drawDraftRect(d, scale, dash, showGuides, screenSnapRadiusPx) }

                    // Draw dimension guides while resizing/moving
                    drawActiveGuides(floor, vm, dragging, scale, showGuides)
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
