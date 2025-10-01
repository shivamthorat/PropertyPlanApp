package com.example.propertyplan.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.propertyplan.model.PlanData
import com.example.propertyplan.ui.canvas.CanvasBoard
import com.example.propertyplan.ui.panels.rememberPanelController
import com.example.propertyplan.ui.widgets.BottomActionBar
import com.example.propertyplan.ui.widgets.FloorDetailsPanel
import com.example.propertyplan.ui.widgets.FloorSummaryPanel
import com.example.propertyplan.ui.widgets.RoomInspectorPanel
import com.example.propertyplan.ui.widgets.RoomTemplatesPanel
import com.example.propertyplan.ui.widgets.TopBar
import com.example.propertyplan.util.sharePlanJson
import com.example.propertyplan.vm.PlanViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SmartPlanScreen(vm: PlanViewModel = viewModel()) {
    val ctx = LocalContext.current
    var canvasW by remember { mutableFloatStateOf(1920f) }
    var canvasH by remember { mutableFloatStateOf(1080f) }

    val panels = rememberPanelController()

    // wire up smart behavior
    LaunchedEffect(vm.mode) { panels.onModeChanged(vm.mode) }
    LaunchedEffect(vm.currentFloorIndex, vm.floors.size) { panels.onFloorChanged(vm.currentFloor()) }

    // prevent auto-opening Inspector/Summary on selection
    val autoOpenInspectorOnSelect = false
    LaunchedEffect(vm.selectedRoomId, autoOpenInspectorOnSelect) {
        if (autoOpenInspectorOnSelect) panels.onSelectionChanged(vm.selectedRoomId != null)
    }

    Scaffold(
        topBar = {
            TopBar(
                onDownloadJson = { sharePlanJson(ctx, PlanData(vm.floors)) },
                onSubmitDemo = {
                    Toast.makeText(ctx, "Demo submission only.", Toast.LENGTH_SHORT).show()
                }
            )
        },
        bottomBar = {
            BottomActionBar(
                mode = vm.mode,
                onModeChange = { vm.mode = it },
                onUndo = { /* hook undo later */ },
                onRedo = { /* hook redo later */ },
                onDuplicate = { vm.duplicateSelectedRoom() },
                onDelete = { vm.deleteSelectedRoom() }
            )
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // center: canvas
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                ElevatedCard(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        CanvasBoard(
                            vm = vm,
                            floor = vm.currentFloor(),
                            onCanvasSize = { w, h -> canvasW = w; canvasH = h }
                        )
                    }
                }
            }

            // Scrim (animated alpha)
            Scrim(
                visible = panels.leftOpen || panels.rightOpen,
                onClick = {
                    when {
                        panels.rightOpen -> panels.toggleRight()
                        panels.leftOpen -> panels.toggleLeft()
                    }
                }
            )

            // LEFT PANEL
            SidePanel(
                side = Side.Left,
                isOpen = panels.leftOpen,
                onDismissed = { if (panels.leftOpen) panels.toggleLeft() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 380.dp)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                ElevatedCard(
                    Modifier
                        .fillMaxSize()
                        .closeOnSwipe(Side.Left) { if (panels.leftOpen) panels.toggleLeft() }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            FloorDetailsPanel(
                                vm = vm,
                                floor = vm.currentFloor(),
                                onSave = { },
                                onDelete = { vm.removeCurrentFloor() },
                                onAdd = { vm.addFloor() },
                                onDuplicate = { vm.duplicateCurrentFloor() },
                                onCopy = { vm.copyFloor() },
                                onPaste = { vm.pasteFloor() }
                            )
                        }
                        item { Divider() }
                        item { RoomTemplatesPanel(vm, canvasW, canvasH) }
                    }
                }
            }

            // RIGHT PANEL
            SidePanel(
                side = Side.Right,
                isOpen = panels.rightOpen,
                onDismissed = { if (panels.rightOpen) panels.toggleRight() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = 360.dp)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                ElevatedCard(
                    Modifier
                        .fillMaxSize()
                        .closeOnSwipe(Side.Right) { if (panels.rightOpen) panels.toggleRight() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { RoomInspectorPanel(vm, vm.currentFloor()) }
                        item { Divider() }
                        item { FloorSummaryPanel(vm, vm.currentFloor()) }
                    }
                }
            }

            // SIDE RAIL HANDLES (movable, fades when idle, drag inward/tap to toggle)
            SideRailHandle(
                side = Side.Left,
                isOpen = panels.leftOpen,
                containerHeightPx = canvasH,
                onToggle = { panels.toggleLeft() }
            )
            SideRailHandle(
                side = Side.Right,
                isOpen = panels.rightOpen,
                containerHeightPx = canvasH,
                onToggle = { panels.toggleRight() }
            )
        }
    }
}

/* -------------------- Shared types -------------------- */

private enum class Side { Left, Right }

/* -------------------- Smooth SidePanel -------------------- */

@Composable
private fun BoxScope.SidePanel(
    side: Side,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    openDurationMs: Int = 260,
    closeDurationMs: Int = 220,
    settleFraction: Float = 0.33f,
    velocityThresholdPxPerSec: Float = 1400f,
    onDismissed: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(0f) }

    // 0f = open; -width (left) / +width (right) = closed.
    val offsetX: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

    fun closedOffset(): Float = if (side == Side.Left) -widthPx else widthPx

    suspend fun animateTo(target: Float, opening: Boolean) {
        val spec: AnimationSpec<Float> = if (opening) {
            tween(durationMillis = openDurationMs, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = closeDurationMs, easing = FastOutLinearInEasing)
        }
        offsetX.animateTo(target, spec)
    }

    LaunchedEffect(isOpen, widthPx) {
        if (widthPx <= 0f) return@LaunchedEffect
        val target = if (isOpen) 0f else closedOffset()
        if (!isOpen && offsetX.value == 0f && offsetX.targetValue == 0f) {
            offsetX.snapTo(target)
        } else {
            animateTo(target, opening = isOpen)
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (widthPx <= 0f) return@rememberDraggableState
        val closed = closedOffset()
        val next = (offsetX.value + delta).coerceIn(
            if (side == Side.Left) closed else 0f,
            if (side == Side.Left) 0f else closed
        )
        scope.launch { offsetX.snapTo(next) }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer {
                translationX = offsetX.value
                clip = false
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (widthPx <= 0f) return@draggable
                    val distanceClose = widthPx * settleFraction
                    val shouldClose = when (side) {
                        Side.Left  -> (offsetX.value <= -distanceClose) || (velocity <= -velocityThresholdPxPerSec)
                        Side.Right -> (offsetX.value >=  distanceClose) || (velocity >=  velocityThresholdPxPerSec)
                    }
                    scope.launch {
                        if (shouldClose) {
                            animateTo(closedOffset(), opening = false)
                            onDismissed()
                        } else {
                            animateTo(0f, opening = true)
                        }
                    }
                }
            )
    ) {
        content()
    }
}

/* -------------------- Slim vertical side rail (movable + idle fade) -------------------- */

@Composable
private fun BoxScope.SideRailHandle(
    side: Side,
    isOpen: Boolean,
    containerHeightPx: Float,
    onToggle: () -> Unit,
    railWidth: Dp = 22.dp,
    railHeight: Dp = 96.dp,
    cornerRadius: Dp = 18.dp,
    maxDragDp: Dp = 140.dp,
    triggerDp: Dp = 48.dp,
    railColor: Color = MaterialTheme.colorScheme.primary,
    idleAlpha: Float = 0.35f,
    idleDelayMs: Long = 2000L,
    fadeMs: Int = 250
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val maxDragPx = with(density) { maxDragDp.toPx() }
    val triggerPx = with(density) { triggerDp.toPx() }
    val railHeightPx = with(density) { railHeight.toPx() }

    val dragPx: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    LaunchedEffect(isOpen) { dragPx.snapTo(0f) }

    // Save vertical position across config changes
    var posFrac by rememberSaveable("rail-${side.name}-pos") { mutableStateOf(0.5f) }
    val travelPx = (containerHeightPx - railHeightPx).coerceAtLeast(0f)
    posFrac = posFrac.coerceIn(0f, 1f)
    val offsetY = (posFrac * travelPx).roundToInt()

    // idle fade
    val alphaAnim: Animatable<Float, AnimationVector1D> = remember { Animatable(1f) }
    var idleJob by remember { mutableStateOf<Job?>(null) }
    fun kickActive() {
        idleJob?.cancel()
        scope.launch { alphaAnim.animateTo(1f, tween(durationMillis = 120, easing = FastOutSlowInEasing)) }
        idleJob = scope.launch {
            delay(idleDelayMs)
            alphaAnim.animateTo(idleAlpha, tween(durationMillis = fadeMs, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) { kickActive() }
    LaunchedEffect(isOpen) { kickActive() }

    val hDragState = rememberDraggableState { delta ->
        kickActive()
        val inward = if (side == Side.Left) +delta else -delta
        val next = (dragPx.value + inward).coerceIn(0f, maxDragPx)
        scope.launch { dragPx.snapTo(next) }
    }

    val vDragState = rememberDraggableState { dy ->
        kickActive()
        if (travelPx > 0f) {
            val df = dy / travelPx
            posFrac = (posFrac + df).coerceIn(0f, 1f)
        }
    }

    val align = if (side == Side.Left) Alignment.TopStart else Alignment.TopEnd
    val offsetX = (if (side == Side.Left) dragPx.value else -dragPx.value).roundToInt()

    val shape =
        if (side == Side.Left)
            RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius)
        else
            RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)

    Box(
        modifier = Modifier
            .align(align)
            .graphicsLayer { translationY = offsetY.toFloat() }
            .padding(
                start = if (side == Side.Left) 2.dp else 0.dp,
                end  = if (side == Side.Right) 2.dp else 0.dp
            )
            .draggable(
                state = vDragState,
                orientation = Orientation.Vertical
            )
    ) {
        Surface(
            color = railColor,
            contentColor = Color.White,
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
            shape = shape,
            modifier = Modifier
                .size(railWidth, railHeight)
                .clip(shape)
                .graphicsLayer {
                    translationX = offsetX.toFloat()
                    alpha = alphaAnim.value
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            kickActive()
                            tryAwaitRelease()
                        },
                        onTap = {
                            kickActive()
                            onToggle()
                        }
                    )
                }
                .draggable(
                    state = hDragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val shouldToggle = dragPx.value >= triggerPx
                        scope.launch {
                            dragPx.animateTo(0f, tween(durationMillis = 220, easing = FastOutLinearInEasing))
                            if (shouldToggle) onToggle()
                            kickActive()
                        }
                    }
                )
        ) {
            // subtle "grip" marks
            Canvas(Modifier.fillMaxSize().padding(horizontal = 5.dp)) {
                val cx = size.width * 0.5f
                val segW = size.width * 0.40f
                val segH = 3f
                val gap = 8f
                val startY = size.height * 0.5f - gap - segH
                repeat(3) { i ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(cx - segW / 2f, startY + i * (segH + gap)),
                        size = Size(segW, segH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(segH / 2f, segH / 2f)
                    )
                }
            }
        }
    }
}

/* -------------------- Scrim with animated alpha (actual color) -------------------- */

@Composable
private fun BoxScope.Scrim(
    visible: Boolean,
    onClick: () -> Unit,
    targetAlpha: Float = 0.12f
) {
    val animAlpha: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        animAlpha.animateTo(
            targetValue = if (visible) targetAlpha else 0f,
            animationSpec = tween(
                durationMillis = if (visible) 160 else 140,
                easing = FastOutSlowInEasing
            )
        )
    }
    if (animAlpha.value <= 0f) return
    Box(
        Modifier
            .matchParentSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = animAlpha.value))
            .align(Alignment.Center)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
    )
}

/* ---------- Swipe-to-close helper (panel card) ---------- */

@Composable
private fun Modifier.closeOnSwipe(
    side: Side,
    distanceThreshold: Dp = 48.dp,
    velocityThresholdPxPerSec: Float = 1500f,
    onClose: () -> Unit
): Modifier {
    val density = LocalDensity.current
    val distancePx = with(density) { distanceThreshold.toPx() }
    var dragSum by remember { mutableFloatStateOf(0f) }

    val state = rememberDraggableState { delta ->
        dragSum += delta
    }

    return this.draggable(
        state = state,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocity ->
            val distanceTrigger = when (side) {
                Side.Left  -> dragSum < -distancePx
                Side.Right -> dragSum >  distancePx
            }
            val velocityTrigger = when (side) {
                Side.Left  -> velocity < -velocityThresholdPxPerSec
                Side.Right -> velocity >  velocityThresholdPxPerSec
            }
            if (distanceTrigger || velocityTrigger) onClose()
            dragSum = 0f
        }
    )
}
