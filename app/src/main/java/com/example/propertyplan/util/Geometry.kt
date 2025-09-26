package com.example.propertyplan.util

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

fun deg2rad(d: Float) = d * PI.toFloat() / 180f
fun rad2deg(r: Float) = r * 180f / PI.toFloat()

/** Rotate a point (px, py) around center (cx, cy) by deg degrees */
fun rotatePoint(px: Float, py: Float, cx: Float, cy: Float, deg: Float): Offset {
    val a = deg2rad(deg)
    val s = sin(a); val c = cos(a)
    val dx = px - cx
    val dy = py - cy
    val rx = dx * c - dy * s
    val ry = dx * s + dy * c
    return Offset(cx + rx, cy + ry)
}

/** Transform world point (wx, wy) into local rect space centered at (cx,cy) with rotation */
fun toLocal(wx: Float, wy: Float, cx: Float, cy: Float, angleDeg: Float): Offset {
    val a = deg2rad(-angleDeg)
    val s = sin(a); val c = cos(a)
    val dx = wx - cx
    val dy = wy - cy
    val lx = dx * c - dy * s
    val ly = dx * s + dy * c
    return Offset(lx, ly)
}

/** Hit test inside unrotated rect centered at origin */
fun insideLocalRect(lx: Float, ly: Float, w: Float, h: Float): Boolean {
    return lx >= -w / 2f && lx <= w / 2f && ly >= -h / 2f && ly <= h / 2f
}
