package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.model.Room
import com.example.propertyplan.vm.PlanViewModel
import kotlin.math.*

private const val GRID = 20f

@Composable
fun PlanCanvas(
    vm: PlanViewModel,
    floor: Floor?,
    onCanvasSize: (Float, Float) -> Unit,
    background: Color = Color(0xFF0B1020)
) {
    // Zoom/pan saved per floor id
    val fid = floor?.id ?: "none"
    var scale by rememberSaveable(fid) { mutableStateOf(1f) }
    var panX by rememberSaveable(fid) { mutableStateOf(0f) }
    var panY by rememberSaveable(fid) { mutableStateOf(0f) }

    // Transformable state (pinch/drag). We keep canvas pan via centroid pan to avoid allocations.
    val tState = remember {
        TransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(0.4f, 3f)
            panX += panChange.x
            panY += panChange.y
        }
    }

    // Cached objects for perf
    val gridColor = remember { Color.White.copy(alpha = 0.06f) }
    val dash by remember { mutableStateOf(PathEffect.dashPathEffect(floatArrayOf(12f, 12f))) }
    val stroke1 = remember { Stroke(width = 1f) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .transformable(tState)
            .pointerInput(Unit) {
                // mouse/trackpad wheel zoom support (optional): skip for brevity
            }
    ) {

        onCanvasSize(size.width, size.height)

        // background
        drawRect(color = background)

        // transformed world
        val pan = Offset(panX, panY)

        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale)
        }) {
            // grid
            if (vm.showGrid) {
                val w = size.width / scale + abs(pan.x)
                val h = size.height / scale + abs(pan.y)
                var x = (-(pan.x / scale) % GRID + GRID) % GRID
                while (x <= w + GRID) {
                    drawLine(gridColor, Offset(x, -10000f), Offset(x, 10000f), 1f / scale)
                    x += GRID
                }
                var y = (-(pan.y / scale) % GRID + GRID) % GRID
                while (y <= h + GRID) {
                    drawLine(gridColor, Offset(-10000f, y), Offset(10000f, y), 1f / scale)
                    y += GRID
                }
            }

            // rooms
            floor?.rooms?.forEach { r ->
                val selected = r.id == vm.selectedRoomId
                val c = Color(r.color)
                val border = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                val borderStroke = if (selected) Stroke(width = 3f / scale) else Stroke(width = 2f / scale)

                rotate(degrees = r.angle, pivot = Offset(r.x + r.w / 2f, r.y + r.h / 2f)) {
                    drawRect(c.copy(alpha = 0.78f), topLeft = Offset(r.x, r.y), size = Size(r.w, r.h))
                    drawRect(border, topLeft = Offset(r.x, r.y), size = Size(r.w, r.h), style = borderStroke)

                    if (selected) {
                        val handleR = 7.dp.toPx() / scale
                        val corners = arrayOf(
                            Offset(r.x, r.y), Offset(r.x + r.w, r.y),
                            Offset(r.x, r.y + r.h), Offset(r.x + r.w, r.y + r.h)
                        )
                        corners.forEach { drawCircle(Color.White, radius = handleR, center = it) }
                        // rotate handle
                        val topCenter = Offset(r.x + r.w / 2f, r.y)
                        val rotHandle = Offset(topCenter.x, topCenter.y - 24f)
                        drawLine(Color.White, topCenter, rotHandle, 2f / scale)
                        drawCircle(Color.White, handleR, rotHandle)
                    }
                }
            }

            // draft rect (if drawing)
            vm.draftRect?.let { d ->
                drawRect(
                    color = if (vm.snapGrid) Color(0xFF22C55E) else Color.White,
                    topLeft = Offset(d.left, d.top),
                    size = Size(d.width, d.height),
                    style = Stroke(width = 2f / scale, pathEffect = dash)
                )
            }
        }
    }

    // expose simple controls to caller
    vm.setViewport(scale, panX, panY)
}
