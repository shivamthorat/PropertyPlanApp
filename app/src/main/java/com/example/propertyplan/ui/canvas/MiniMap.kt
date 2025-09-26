package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.ui.canvas.util.*

@Composable
fun MiniMap(
    modifier: Modifier,
    floor: Floor?,
    pan: Offset,
    scale: Float,
    widthPx: Float,
    heightPx: Float,
    onJump: (Offset) -> Unit
) {
    if (floor == null || floor.rooms.isEmpty()) return
    val worldBounds = roomsBounds(floor.rooms) ?: return

    val mapW = 160f
    val mapH = 120f
    val pad = 8f

    Canvas(
        modifier = modifier
            .size((mapW + pad * 2).dp, (mapH + pad * 2).dp)
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    // Map the tap (in minimap space) back to world space
                    val fit = fitRect(
                        content = worldBounds,
                        container = Rect(pad, pad, pad + mapW, pad + mapH)
                    )
                    val worldTap = unmapPoint(tap, worldBounds, fit)

                    // Jump the main canvas so that worldTap lands at screen center
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

        // Draw rooms
        floor.rooms.forEach { r ->
            val rc = mapRect(r.rect(), worldBounds, fit)
            drawRect(Color(0xFF90CAF9), topLeft = rc.topLeft, size = rc.size)
        }

        // Compute current main-canvas viewport in world coords (use the correct toWorld overload)
        val topLeftWorld = toWorld(Offset(0f, 0f), pan, scale)
        val bottomRightWorld = toWorld(Offset(widthPx, heightPx), pan, scale)
        val viewRectWorld = Rect(
            topLeftWorld.x, topLeftWorld.y,
            bottomRightWorld.x, bottomRightWorld.y
        )

        // Draw the viewport rectangle on the minimap
        val viewRc = mapRect(viewRectWorld, worldBounds, fit)
        drawRect(Color.White, topLeft = viewRc.topLeft, size = viewRc.size, style = Stroke(2f))
    }
}
