package com.example.propertyplan.ui.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class RectDraft(val x: Float, val y: Float, val w: Float, val h: Float)

/** Which corner handle is grabbed for corner-resize. */
enum class Corner { TL, TR, BL, BR }

/** Which edge handle is grabbed for edge-resize. */
enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * Drag/gesture states:
 * - Pan: panning the canvas
 * - Move: moving a selected room
 * - Rotate: rotating a selected room
 * - Resize: resizing from a corner (TL/TR/BL/BR)
 * - EdgeResize: resizing from a side (LEFT/RIGHT/TOP/BOTTOM)
 */
sealed class DragState {
    data class Pan(val startPan: Offset) : DragState()

    /** Move the whole rect; keep original rect for delta reference. */
    data class Move(
        val id: String,
        val start: Offset,
        val startRect: Rect
    ) : DragState()

    /** Rotate around center; startDeg = initial angle in degrees. */
    data class Rotate(
        val id: String,
        val start: Offset,
        val startDeg: Float
    ) : DragState()

    /** Corner-based resize (rotation-aware via center + startW/H). */
    data class Resize(
        val id: String,
        val corner: Corner,
        val center: Offset,
        val startW: Float,
        val startH: Float
    ) : DragState()

    /** NEW: Edge-based resize (LEFT/RIGHT/TOP/BOTTOM), rotation-aware. */
    data class EdgeResize(
        val id: String,
        val edge: Edge,
        val center: Offset,
        val startW: Float,
        val startH: Float
    ) : DragState()
}
